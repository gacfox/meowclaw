package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.SkillDto;
import com.gacfox.meowclaw.entity.AgentConfig;
import com.gacfox.meowclaw.entity.Skill;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.AgentConfigRepository;
import com.gacfox.meowclaw.repository.SkillRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SkillService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REQUIRED_SKILL_FILE = "SKILL.md";
    private static final String SKILL_NAME_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final SkillRepository skillRepository;
    private final AgentConfigRepository agentConfigRepository;

    @Value("${skills.storage-dir:./data/skills}")
    private String skillStorageDir;

    public SkillService(SkillRepository skillRepository, AgentConfigRepository agentConfigRepository) {
        this.skillRepository = skillRepository;
        this.agentConfigRepository = agentConfigRepository;
    }

    public List<SkillDto> list() {
        return skillRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public SkillDto upload(String name, String description, MultipartFile file) {
        String normalizedName = normalizeName(name);
        validateSkillName(normalizedName);
        validateZipFile(file);

        Optional<Skill> existing = skillRepository.findByName(normalizedName);
        if (existing.isPresent()) {
            throw new ServiceNotSatisfiedException("技能名称已存在");
        }

        Path storageDir = resolveStorageDir();
        Path ignored = createDirectories(storageDir);
        String packageFile = normalizedName + ".zip";
        Path targetPath = storageDir.resolve(packageFile).toAbsolutePath().normalize();

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("skill-", ".zip");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!containsRequiredSkillFile(tempFile)) {
                throw new ServiceNotSatisfiedException("技能包根目录缺少 SKILL.md");
            }

            Files.copy(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (ServiceNotSatisfiedException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceNotSatisfiedException("保存技能包失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignoredDelete) {
                    // ignore
                }
            }
        }

        Skill skill = new Skill();
        skill.setName(normalizedName);
        skill.setDescription(description);
        skill.setPackageFile(packageFile);
        Instant now = Instant.now();
        skill.setCreatedAtInstant(now);
        skill.setUpdatedAtInstant(now);

        Skill saved = skillRepository.save(skill);
        return toDto(saved);
    }

    public void delete(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("技能不存在"));

        if (isSkillUsed(skill.getName())) {
            throw new ServiceNotSatisfiedException("该技能正在被智能体使用，无法删除");
        }

        Path packagePath = resolveStorageDir().resolve(skill.getPackageFile()).toAbsolutePath().normalize();
        try {
            Files.deleteIfExists(packagePath);
        } catch (Exception e) {
            throw new ServiceNotSatisfiedException("删除技能包失败: " + e.getMessage());
        }

        skillRepository.deleteById(id);
    }

    public SkillDto update(Long id, String name, String description) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("技能不存在"));

        String normalizedName = normalizeName(name);
        if (normalizedName == null || !normalizedName.equals(skill.getName())) {
            throw new ServiceNotSatisfiedException("技能名称不可修改");
        }

        skill.setDescription(description);
        skill.setUpdatedAtInstant(Instant.now());
        Skill saved = skillRepository.save(skill);
        return toDto(saved);
    }

    public Path getSkillPackagePath(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("技能不存在"));
        Path packagePath = resolveStorageDir().resolve(skill.getPackageFile()).toAbsolutePath().normalize();
        if (!Files.exists(packagePath)) {
            throw new ServiceNotSatisfiedException("技能包不存在");
        }
        return packagePath;
    }

    public String readSkillPrompt(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("技能不存在"));
        Path packagePath = resolveStorageDir().resolve(skill.getPackageFile()).toAbsolutePath().normalize();
        if (!Files.exists(packagePath)) {
            throw new ServiceNotSatisfiedException("技能包不存在");
        }
        try (InputStream inputStream = Files.newInputStream(packagePath);
             ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (REQUIRED_SKILL_FILE.equals(entry.getName())) {
                    byte[] data = zis.readAllBytes();
                    return new String(data, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            throw new ServiceNotSatisfiedException("读取技能内容失败: " + e.getMessage());
        }
        throw new ServiceNotSatisfiedException("技能包缺少 SKILL.md");
    }

    public List<Skill> findByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<Skill> skills = new ArrayList<>();
        for (String name : new HashSet<>(names)) {
            skillRepository.findByName(name).ifPresent(skills::add);
        }
        return skills;
    }

    public List<String> parseEnabledSkills(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(value, new TypeReference<>() {
            });
            return parsed.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
        } catch (Exception e) {
            return value.lines()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
    }

    public void syncAgentSkills(AgentConfig agent, Path workspaceDir) {
        List<String> enabledSkills = parseEnabledSkills(agent.getEnabledSkills());
        Path skillsDir = workspaceDir.resolve("skills").toAbsolutePath().normalize();
        Path ignored = createDirectories(skillsDir);

        Set<String> desired = new HashSet<>(enabledSkills);
        removeUnusedSkillDirs(skillsDir, desired);

        for (String skillName : desired) {
            Optional<Skill> skillOpt = skillRepository.findByName(skillName);
            if (skillOpt.isEmpty()) {
                continue;
            }
            Path zipPath = resolveStorageDir().resolve(skillOpt.get().getPackageFile()).toAbsolutePath().normalize();
            Path targetDir = skillsDir.resolve(skillName).toAbsolutePath().normalize();
            extractSkillZip(zipPath, targetDir);
        }
    }

    private void extractSkillZip(Path zipPath, Path targetDir) {
        deleteDirectory(targetDir);
        Path ignored = createDirectories(targetDir);

        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Path dirPath = targetDir.resolve(entry.getName()).normalize();
                    if (dirPath.startsWith(targetDir)) {
                        Path ignoredDir = createDirectories(dirPath);
                    }
                    continue;
                }
                Path filePath = targetDir.resolve(entry.getName()).normalize();
                if (!filePath.startsWith(targetDir)) {
                    continue;
                }
                Path parent = filePath.getParent();
                if (parent != null) {
                    Path ignoredParent = createDirectories(parent);
                }
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new ServiceNotSatisfiedException("解压技能包失败: " + e.getMessage());
        }
    }

    private void removeUnusedSkillDirs(Path skillsDir, Set<String> desired) {
        try {
            if (!Files.exists(skillsDir)) {
                return;
            }
            try (var stream = Files.list(skillsDir)) {
                stream.filter(Files::isDirectory).forEach(path -> {
                    String name = path.getFileName().toString();
                    if (!desired.contains(name)) {
                        deleteDirectory(path);
                    }
                });
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private void deleteDirectory(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                                // ignore
                            }
                        });
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private boolean containsRequiredSkillFile(Path zipFile) {
        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (REQUIRED_SKILL_FILE.equals(entryName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new ServiceNotSatisfiedException("读取技能包失败: " + e.getMessage());
        }
        return false;
    }

    private void validateZipFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceNotSatisfiedException("技能包不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new ServiceNotSatisfiedException("技能包必须是 zip 格式");
        }
    }

    private void validateSkillName(String name) {
        if (name == null || name.isBlank()) {
            throw new ServiceNotSatisfiedException("技能名称不能为空");
        }
        if (!name.matches(SKILL_NAME_PATTERN)) {
            throw new ServiceNotSatisfiedException("技能名称必须是 kebab-case");
        }
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private boolean isSkillUsed(String skillName) {
        List<AgentConfig> agents = agentConfigRepository.findAll();
        for (AgentConfig agent : agents) {
            List<String> enabled = parseEnabledSkills(agent.getEnabledSkills());
            if (enabled.contains(skillName)) {
                return true;
            }
        }
        return false;
    }

    private SkillDto toDto(Skill skill) {
        SkillDto dto = new SkillDto();
        BeanUtils.copyProperties(skill, dto);
        return dto;
    }

    private Path resolveStorageDir() {
        String baseDir = skillStorageDir == null || skillStorageDir.isBlank()
                ? "./data/skills"
                : skillStorageDir;
        return Path.of(baseDir).toAbsolutePath().normalize();
    }

    private Path createDirectories(Path path) {
        try {
            return Files.createDirectories(path);
        } catch (Exception e) {
            throw new ServiceNotSatisfiedException("创建目录失败: " + e.getMessage());
        }
    }
}
