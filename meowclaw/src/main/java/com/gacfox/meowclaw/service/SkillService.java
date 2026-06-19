package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.SkillInstallRequest;
import com.gacfox.meowclaw.dto.SkillInstallResultDTO;
import com.gacfox.meowclaw.dto.SkillPackageDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.SkillPackage;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.SkillPackageRepository;
import com.gacfox.meowclaw.util.FrontmatterParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能包管理：上传解析、列表、删除、安装到智能体工作区。
 * 安装是覆盖性、不可逆的——删除技能包不影响已安装到工作区的副本。
 */
@Slf4j
@Service
public class SkillService {
    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final String SKILL_MD_ENTRY = "SKILL.md";

    @Value("${meowclaw.data-dir}")
    private String dataDir;

    private final SkillPackageRepository skillPackageRepository;
    private final AgentRepository agentRepository;

    public SkillService(SkillPackageRepository skillPackageRepository, AgentRepository agentRepository) {
        this.skillPackageRepository = skillPackageRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillPackageDTO> list() {
        return skillPackageRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public SkillPackageDTO upload(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("仅支持 .zip 格式");
        }
        String skillMdContent = extractSkillMd(file);
        if (skillMdContent == null) {
            throw new IllegalArgumentException("压缩包根目录必须包含 SKILL.md");
        }
        Map<String, Object> fm = FrontmatterParser.parse(skillMdContent);
        Object nameObj = fm.get("name");
        if (nameObj == null || String.valueOf(nameObj).isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少必填字段 name");
        }
        String name = String.valueOf(nameObj);
        if (!SKILL_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("skill name 只允许字母数字下划线短横线，长度 1-64");
        }
        Object descObj = fm.get("description");

        Path dir = Paths.get(dataDir, "upload", "skill-package").toAbsolutePath();
        Files.createDirectories(dir);
        String stored = System.currentTimeMillis() + "_" + UUID.randomUUID() + ".zip";
        Path target = dir.resolve(stored);
        file.transferTo(target);

        long now = System.currentTimeMillis();
        SkillPackage entity = new SkillPackage();
        entity.setName(name);
        entity.setDescription(descObj == null ? null : String.valueOf(descObj));
        entity.setStoredFilename(stored);
        entity.setOriginalFilename(original);
        entity.setFileSize(file.getSize());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toDTO(skillPackageRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        SkillPackage entity = skillPackageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("技能包不存在"));
        try {
            Files.deleteIfExists(packagePath(entity.getStoredFilename()));
        } catch (IOException e) {
            log.warn("Failed to delete skill package file {}", entity.getStoredFilename(), e);
        }
        skillPackageRepository.delete(entity);
    }

    public SkillInstallResultDTO install(Long skillId, SkillInstallRequest req) throws IOException {
        SkillPackage pkg = skillPackageRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("技能包不存在"));
        Agent agent = agentRepository.findById(req.getAgentId())
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        if (agent.getWorkspaceFolder() == null || agent.getWorkspaceFolder().isBlank()) {
            throw new IllegalStateException("智能体未配置工作区");
        }

        Path workspace = Paths.get(agent.getWorkspaceFolder());
        Files.createDirectories(workspace);
        Path skillDir = workspace.resolve(".skill").resolve(pkg.getName());

        if (Files.exists(skillDir) && !isEmpty(skillDir) && !Boolean.TRUE.equals(req.getOverwrite())) {
            return new SkillInstallResultDTO(SkillInstallResultDTO.Status.CONFLICT, listRelativeEntries(skillDir));
        }
        if (Files.exists(skillDir)) {
            deleteRecursively(skillDir);
        }
        Files.createDirectories(skillDir);
        extractZip(packagePath(pkg.getStoredFilename()), skillDir);
        return new SkillInstallResultDTO(SkillInstallResultDTO.Status.INSTALLED, List.of());
    }

    private String extractSkillMd(MultipartFile file) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(SKILL_MD_ENTRY)) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    /**
     * 解压 ZIP 到目标目录。每个 entry 都做 ZipSlip 防护：normalize 后必须仍在目标目录内。
     */
    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = normalizedTarget.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(normalizedTarget)) {
                    log.warn("Skip zip entry outside target dir: {}", entry.getName());
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isEmpty();
        }
    }

    private List<String> listRelativeEntries(Path dir) throws IOException {
        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.skip(1).forEach(p -> result.add(dir.relativize(p).toString().replace('\\', '/')));
        }
        return result;
    }

    private void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}", p, e);
                        }
                    });
        }
    }

    private Path packagePath(String storedFilename) {
        return Paths.get(dataDir, "upload", "skill-package", storedFilename).toAbsolutePath();
    }

    private SkillPackageDTO toDTO(SkillPackage entity) {
        return new SkillPackageDTO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStoredFilename(),
                entity.getOriginalFilename(),
                entity.getFileSize(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
