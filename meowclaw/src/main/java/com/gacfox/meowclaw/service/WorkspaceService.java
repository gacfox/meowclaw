package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.CreateEntryRequest;
import com.gacfox.meowclaw.dto.FileContent;
import com.gacfox.meowclaw.dto.FileEntry;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 智能体工作区文件管理：所有操作都以 agentId 解析出工作区根，
 * 相对路径必须落在根之内（normalize + startsWith 防穿越）。
 */
@Slf4j
@Service
public class WorkspaceService {
    private static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "yml", "yaml", "js", "mjs", "cjs", "ts", "tsx", "jsx",
            "java", "kt", "py", "go", "rs", "c", "cpp", "cc", "h", "hpp", "cs", "rb", "php", "swift",
            "css", "scss", "less", "html", "htm", "xml", "svg", "sh", "bash", "zsh", "bat", "ps1",
            "sql", "properties", "ini", "conf", "cfg", "toml", "env", "log", "csv", "tsv",
            "vue", "svelte", "graphql", "gql", "dockerfile", "gitignore", "gradle");
    private static final Map<String, String> IMAGE_MIME = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif",
            "webp", "image/webp", "bmp", "image/bmp", "ico", "image/x-icon");

    private final AgentRepository agentRepository;

    public WorkspaceService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public java.util.List<FileEntry> list(Long agentId, String dir) throws IOException {
        Path root = resolveRoot(agentId);
        Path target = resolveWithin(root, dir);
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("路径不是目录");
        }
        try (Stream<Path> stream = Files.list(target)) {
            return stream
                    .map(p -> toEntry(root, p))
                    .sorted(Comparator
                            .comparing(FileEntry::isDirectory, Comparator.reverseOrder())
                            .thenComparing(e -> e.getName().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public FileContent read(Long agentId, String path) throws IOException {
        Path root = resolveRoot(agentId);
        Path file = resolveWithin(root, path);
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("不能读取目录内容");
        }
        String ext = extension(file.getFileName().toString());
        if (IMAGE_MIME.containsKey(ext)) {
            return readImage(file, IMAGE_MIME.get(ext));
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return readText(file);
        }
        return new FileContent(FileContent.Kind.UNSUPPORTED, null, null, null);
    }

    public void saveText(Long agentId, String path, String content) throws IOException {
        Path root = resolveRoot(agentId);
        Path file = resolveWithin(root, path);
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("目标是一个目录");
        }
        if (!TEXT_EXTENSIONS.contains(extension(file.getFileName().toString()))) {
            throw new IllegalArgumentException("该文件类型不支持文本编辑");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public void delete(Long agentId, String path) throws IOException {
        Path root = resolveRoot(agentId);
        Path target = resolveWithin(root, path);
        if (target.equals(root)) {
            throw new IllegalArgumentException("不能删除工作区根目录");
        }
        if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete {}", p, e);
                    }
                });
            }
        } else {
            Files.deleteIfExists(target);
        }
    }

    public void move(Long agentId, String fromPath, String toPath) throws IOException {
        Path root = resolveRoot(agentId);
        Path from = resolveWithin(root, fromPath);
        Path to = resolveWithin(root, toPath);
        if (from.equals(root)) {
            throw new IllegalArgumentException("不能移动工作区根目录");
        }
        if (from.equals(to)) {
            return;
        }
        if (to.startsWith(from)) {
            throw new IllegalArgumentException("不能移动到自身或其子目录内");
        }
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void create(Long agentId, String path, CreateEntryRequest.Type type) throws IOException {
        Path root = resolveRoot(agentId);
        Path target = resolveWithin(root, path);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("已存在同名条目");
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (type == CreateEntryRequest.Type.DIR) {
            Files.createDirectory(target);
        } else {
            Files.createFile(target);
        }
    }

    public FileEntry upload(Long agentId, String dir, MultipartFile file) throws IOException {
        Path root = resolveRoot(agentId);
        Path targetDir = resolveWithin(root, dir);
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("目标路径不是目录");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        if (filename.isBlank()) {
            throw new IllegalArgumentException("文件名无效");
        }
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(targetDir)) {
            throw new IllegalArgumentException("非法文件名");
        }
        Files.deleteIfExists(target);
        file.transferTo(target);
        return toEntry(root, target);
    }

    private Path resolveRoot(Long agentId) throws IOException {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        String folder = agent.getWorkspaceFolder();
        if (folder == null || folder.isBlank()) {
            throw new IllegalStateException("智能体未配置工作区");
        }
        Path root = Paths.get(folder).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    /**
     * 将相对路径解析到工作区根之内，越界（含绝对路径、.. 穿越）一律拒绝。
     */
    private Path resolveWithin(Path root, String relativePath) {
        String rel = relativePath == null ? "" : relativePath.trim();
        Path resolved = root.resolve(rel).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法路径：" + rel);
        }
        return resolved;
    }

    private FileContent readText(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("文件过大，暂不支持预览（上限 2MB）");
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return new FileContent(FileContent.Kind.TEXT, "text/plain", content, null);
    }

    private FileContent readImage(Path file, String mime) throws IOException {
        long size = Files.size(file);
        if (size > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片过大，暂不支持预览（上限 10MB）");
        }
        byte[] bytes = Files.readAllBytes(file);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        return new FileContent(FileContent.Kind.IMAGE, mime, null, dataUrl);
    }

    private FileEntry toEntry(Path root, Path p) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        long size;
        try {
            size = Files.isDirectory(p) ? 0L : Files.size(p);
        } catch (IOException e) {
            size = 0L;
        }
        long lastModified;
        try {
            lastModified = Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            lastModified = 0L;
        }
        return new FileEntry(p.getFileName().toString(), rel, Files.isDirectory(p), size, lastModified);
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String original) {
        if (original == null) {
            return "";
        }
        String[] parts = original.replace('\\', '/').split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isEmpty() && !part.equals(".") && !part.equals("..")) {
                return part;
            }
        }
        return "";
    }
}
