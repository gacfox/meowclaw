package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.WorkspaceEntryDto;
import com.gacfox.meowclaw.dto.WorkspacePreviewDto;
import com.gacfox.meowclaw.entity.AgentConfig;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.AgentConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class WorkspaceService {
    private final AgentConfigRepository agentConfigRepository;
    @Value("${agent.workspace.base-dir:./data/workspaces}")
    private String agentWorkspaceBaseDir;

    public WorkspaceService(AgentConfigRepository agentConfigRepository) {
        this.agentConfigRepository = agentConfigRepository;
    }

    public List<WorkspaceEntryDto> listEntries(Long agentId, String path) {
        Path dir = resolvePath(agentId, path);
        if (!Files.exists(dir)) {
            return new ArrayList<>();
        }
        if (!Files.isDirectory(dir)) {
            throw new ServiceNotSatisfiedException("路径不是目录");
        }

        try {
            List<WorkspaceEntryDto> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path item : stream) {
                    WorkspaceEntryDto dto = new WorkspaceEntryDto();
                    dto.setName(item.getFileName().toString());
                    dto.setPath(toRelative(agentId, item));
                    dto.setDirectory(Files.isDirectory(item));
                    dto.setSize(dto.isDirectory() ? 0L : Files.size(item));
                    dto.setModifiedAt(Files.getLastModifiedTime(item).toMillis());
                    dto.setMime(Files.probeContentType(item));
                    entries.add(dto);
                }
            }
            entries.sort(Comparator
                    .comparing(WorkspaceEntryDto::isDirectory).reversed()
                    .thenComparing(WorkspaceEntryDto::getName, String.CASE_INSENSITIVE_ORDER));
            return entries;
        } catch (IOException e) {
            throw new ServiceNotSatisfiedException("读取目录失败");
        }
    }

    public Path getDownloadPath(Long agentId, String path) {
        Path target = resolvePath(agentId, path);
        if (!Files.exists(target)) {
            throw new ServiceNotSatisfiedException("文件不存在");
        }
        if (Files.isDirectory(target)) {
            throw new ServiceNotSatisfiedException("无法下载目录");
        }
        return target;
    }

    public WorkspacePreviewDto preview(Long agentId, String path) {
        Path target = getDownloadPath(agentId, path);
        WorkspacePreviewDto dto = new WorkspacePreviewDto();
        dto.setMime(probeMime(target));
        dto.setSize(getSizeSafe(target));

        if (isTextFile(dto.getMime(), target)) {
            dto.setType("text");
            dto.setContent(readLimited(target, 16000));
        } else {
            dto.setType("binary");
        }
        return dto;
    }

    public void delete(Long agentId, String path) {
        Path target = resolvePath(agentId, path);
        if (!Files.exists(target)) {
            return;
        }
        try {
            if (Files.isDirectory(target)) {
                deleteDirectory(target);
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            throw new ServiceNotSatisfiedException("删除失败");
        }
    }

    public void move(Long agentId, String from, String to) {
        Path source = resolvePath(agentId, from);
        Path target = resolvePath(agentId, to);
        if (!Files.exists(source)) {
            throw new ServiceNotSatisfiedException("源路径不存在");
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceNotSatisfiedException("移动失败");
        }
    }

    private Path resolvePath(Long agentId, String rawPath) {
        AgentConfig agent = agentConfigRepository.findById(agentId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("智能体不存在"));
        String baseDir = agentWorkspaceBaseDir == null || agentWorkspaceBaseDir.isBlank()
                ? "./data/workspaces"
                : agentWorkspaceBaseDir;
        String folder = agent.getWorkspaceFolder();
        if (folder == null || folder.isBlank()) {
            folder = agent.getName();
        }
        Path workspace = Paths.get(baseDir).resolve(folder).toAbsolutePath().normalize();
        Path target = (rawPath == null || rawPath.isBlank())
                ? workspace
                : workspace.resolve(rawPath).normalize();
        if (!target.startsWith(workspace)) {
            throw new ServiceNotSatisfiedException("非法路径");
        }
        return target;
    }

    private String toRelative(Long agentId, Path path) {
        Path workspace = resolvePath(agentId, "");
        return workspace.relativize(path).toString().replace("\\", "/");
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean isTextFile(String mime, Path path) {
        if (mime != null && (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml"))) {
            return true;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".log");
    }

    private String probeMime(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (IOException e) {
            return null;
        }
    }

    private long getSizeSafe(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private String readLimited(Path path, int maxChars) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() > maxChars) {
                return content.substring(0, maxChars) + "\n... (已截断)";
            }
            return content;
        } catch (IOException e) {
            return "读取失败";
        }
    }
}
