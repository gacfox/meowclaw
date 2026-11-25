package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.exception.UnsupportedWorkspaceFileTypeException;
import com.gacfox.meowclaw.repository.AgentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 智能体工作区文件管理。所有用户路径同时经过词法归一化和真实路径边界校验，
 * 不存在的写入路径会校验其最近的真实父目录，避免符号链接逃逸工作区。
 */
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
    private final Object transferLock = new Object();

    @Autowired
    public WorkspaceService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public List<FileEntryDTO> list(Long agentId, String dir) throws IOException {
        Path root = resolveRoot(agentId);
        Path target = resolveExisting(root, dir);
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("路径不是目录");
        }
        try (Stream<Path> stream = Files.list(target)) {
            return stream
                    .map(p -> toEntry(root, p))
                    .sorted(Comparator
                            .comparing(FileEntryDTO::isDirectory, Comparator.reverseOrder())
                            .thenComparing(e -> e.getName().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public WorkspaceFilePreview read(Long agentId, String path) throws IOException {
        Path file = resolveFileForDownload(agentId, path);
        long size = Files.size(file);
        String ext = extension(file.getFileName().toString());
        if (IMAGE_MIME.containsKey(ext)) {
            if (size > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("图片过大，暂不支持预览（上限 10MB）");
            }
            return new WorkspaceFilePreview(file, WorkspaceFilePreview.Kind.IMAGE, IMAGE_MIME.get(ext), size);
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            if (size > MAX_TEXT_BYTES) {
                throw new IllegalArgumentException("文件过大，暂不支持预览（上限 2MB）");
            }
            return new WorkspaceFilePreview(file, WorkspaceFilePreview.Kind.TEXT, "text/plain", size);
        }
        if ("pdf".equals(ext)) {
            return new WorkspaceFilePreview(file, WorkspaceFilePreview.Kind.PDF, "application/pdf", size);
        }
        throw new UnsupportedWorkspaceFileTypeException("该文件类型不支持预览");
    }

    public Path resolveFileForDownload(Long agentId, String path) throws IOException {
        Path root = resolveRoot(agentId);
        if (!Files.exists(resolveWithin(root, path), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("文件不存在");
        }
        Path file = resolveExisting(root, path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("不能下载目录");
        }
        return file;
    }

    public void saveText(Long agentId, String path, String content) throws IOException {
        Path root = resolveRoot(agentId);
        Path file = resolveForWrite(root, path, true);
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("目标是一个目录");
        }
        if (!TEXT_EXTENSIONS.contains(extension(file.getFileName().toString()))) {
            throw new IllegalArgumentException("该文件类型不支持文本编辑");
        }
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public void delete(Long agentId, String path) throws IOException {
        Path root = resolveRoot(agentId);
        Path lexicalTarget = resolveWithin(root, path);
        if (lexicalTarget.equals(root)) {
            throw new IllegalArgumentException("不能删除工作区根目录");
        }
        if (!Files.exists(lexicalTarget, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        rejectSymbolicLink(lexicalTarget);
        Path target = resolveExisting(root, path);
        if (target.equals(root)) {
            throw new IllegalArgumentException("不能删除工作区根目录");
        }
        deleteRecursively(target);
    }

    public BatchOperationResultDTO deleteBatch(Long agentId, List<String> paths) throws IOException {
        Path root = resolveRoot(agentId);
        requirePaths(paths);
        List<Path> lexicalPaths = paths.stream().map(path -> {
            Path pathInRoot = resolveWithin(root, path);
            if (pathInRoot.equals(root)) {
                throw new IllegalArgumentException("不能删除工作区根目录");
            }
            return pathInRoot;
        }).toList();

        List<BatchOperationItemDTO> results = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            String sourcePath = paths.get(i);
            try {
                Path lexicalPath = lexicalPaths.get(i);
                if (!Files.exists(lexicalPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("路径不存在");
                }
                rejectSymbolicLink(lexicalPath);
                Path source = resolveExisting(root, sourcePath);
                if (source.equals(root)) {
                    throw new IllegalArgumentException("不能删除工作区根目录");
                }
                deleteRecursively(source);
                results.add(item(sourcePath, null, true, false, "删除成功"));
            } catch (IOException | IllegalArgumentException e) {
                results.add(item(sourcePath, null, false, false, errorMessage(e)));
            }
        }
        return new BatchOperationResultDTO(results);
    }

    public void move(Long agentId, String fromPath, String toPath) throws IOException {
        Path root = resolveRoot(agentId);
        Path from = resolveExisting(root, fromPath);
        if (from.equals(root)) {
            throw new IllegalArgumentException("不能移动工作区根目录");
        }
        Path to = resolveForWrite(root, toPath, true);
        if (from.equals(to)) {
            return;
        }
        if (to.startsWith(from)) {
            throw new IllegalArgumentException("不能移动到自身或其子目录内");
        }
        movePath(true, from, to);
    }

    public BatchOperationResultDTO copy(Long agentId, List<String> sourcePaths, String targetDir,
                                        String conflict) throws IOException {
        Path root = resolveRoot(agentId);
        Path destinationDir = resolveDirectory(root, targetDir);
        ConflictStrategy strategy = ConflictStrategy.from(conflict);
        requirePaths(sourcePaths);
        List<Path> lexicalSources = sourcePaths.stream().map(path -> resolveWithin(root, path)).toList();
        List<BatchOperationItemDTO> results = new ArrayList<>();

        synchronized (transferLock) {
            for (int i = 0; i < sourcePaths.size(); i++) {
                String sourcePath = sourcePaths.get(i);
                Path lexicalSource = lexicalSources.get(i);
                Path intendedTarget = null;
                try {
                    if (lexicalSource.equals(root) || lexicalSource.getFileName() == null) {
                        throw new IllegalArgumentException("不能复制工作区根目录");
                    }
                    intendedTarget = destinationDir.resolve(lexicalSource.getFileName().toString()).normalize();
                    Path source = resolveTree(root, sourcePath);
                    Path target = resolveForWrite(root, relativePath(root, intendedTarget), false);
                    if (destinationDir.startsWith(source) || target.equals(source)) {
                        throw new IllegalArgumentException("不能复制到自身或其子目录内");
                    }
                    CopyOutcome outcome = copyOne(source, target, strategy);
                    results.add(item(sourcePath, relativePath(root, outcome.target()), true,
                            outcome.skipped(), outcome.skipped() ? "目标已存在，已跳过" : "复制成功"));
                } catch (IOException | IllegalArgumentException e) {
                    String targetPath = intendedTarget != null && intendedTarget.startsWith(root)
                            ? relativePath(root, intendedTarget) : null;
                    results.add(item(sourcePath, targetPath, false, false, errorMessage(e)));
                }
            }
        }
        return new BatchOperationResultDTO(results);
    }

    public BatchOperationResultDTO moveBatch(Long agentId, List<String> sourcePaths, String targetDir,
                                             String conflict) throws IOException {
        Path root = resolveRoot(agentId);
        Path destinationDir = resolveDirectory(root, targetDir);
        ConflictStrategy strategy = ConflictStrategy.from(conflict);
        requirePaths(sourcePaths);
        List<Path> lexicalSources = sourcePaths.stream().map(path -> {
            Path source = resolveWithin(root, path);
            if (source.equals(root)) {
                throw new IllegalArgumentException("不能移动工作区根目录");
            }
            return source;
        }).toList();
        List<BatchOperationItemDTO> results = new ArrayList<>();

        synchronized (transferLock) {
            for (int i = 0; i < sourcePaths.size(); i++) {
                String sourcePath = sourcePaths.get(i);
                Path lexicalSource = lexicalSources.get(i);
                Path intendedTarget = destinationDir.resolve(lexicalSource.getFileName().toString()).normalize();
                try {
                    Path source = resolveExisting(root, sourcePath);
                    Path target = resolveForWrite(root, relativePath(root, intendedTarget), false);
                    if (source.equals(target)) {
                        results.add(item(sourcePath, relativePath(root, target), true, true,
                                "源路径与目标路径相同，无需移动"));
                        continue;
                    }
                    if (destinationDir.startsWith(source) || target.startsWith(source)) {
                        throw new IllegalArgumentException("不能移动到自身或其子目录内");
                    }
                    MoveOutcome outcome = moveOne(source, target, strategy);
                    results.add(item(sourcePath, relativePath(root, outcome.target()), true,
                            outcome.skipped(), outcome.skipped() ? "目标已存在，已跳过" : "移动成功"));
                } catch (IOException | IllegalArgumentException e) {
                    results.add(item(sourcePath, relativePath(root, intendedTarget), false, false, errorMessage(e)));
                }
            }
        }
        return new BatchOperationResultDTO(results);
    }

    public BatchOperationResultDTO rename(Long agentId, String path, String newName,
                                          String conflict) throws IOException {
        Path root = resolveRoot(agentId);
        String validName = validateFileName(newName);
        ConflictStrategy strategy = ConflictStrategy.from(conflict);
        Path lexicalSource = resolveWithin(root, path);
        if (lexicalSource.equals(root)) {
            throw new IllegalArgumentException("不能重命名工作区根目录");
        }
        synchronized (transferLock) {
            Path intendedTarget = lexicalSource.getParent().resolve(validName).normalize();
            try {
                Path source = resolveExisting(root, path);
                Path target = resolveForWrite(root, relativePath(root, intendedTarget), false);
                if (source.equals(target)) {
                    return singleResult(path, relativePath(root, target), true, true,
                            "新旧名称相同，无需重命名");
                }
                MoveOutcome outcome = moveOne(source, target, strategy);
                return singleResult(path, relativePath(root, outcome.target()), true, outcome.skipped(),
                        outcome.skipped() ? "目标已存在，已跳过" : "重命名成功");
            } catch (IOException | IllegalArgumentException e) {
                return singleResult(path, relativePath(root, intendedTarget), false, false, errorMessage(e));
            }
        }
    }

    public WorkspacePropertiesDTO properties(Long agentId, List<String> paths) throws IOException {
        Path root = resolveRoot(agentId);
        requirePaths(paths);
        long files = 0;
        long directories = 0;
        long totalSize = 0;
        long lastModified = 0;
        for (String path : paths) {
            Path source = resolveTree(root, path);
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path current : walk.toList()) {
                    rejectSymbolicLink(current);
                    Path realCurrent = ensureRealPathWithin(root, current);
                    lastModified = Math.max(lastModified, Files.getLastModifiedTime(realCurrent).toMillis());
                    if (Files.isDirectory(realCurrent)) {
                        directories = Math.incrementExact(directories);
                    } else if (Files.isRegularFile(realCurrent)) {
                        files = Math.incrementExact(files);
                        totalSize = Math.addExact(totalSize, Files.size(realCurrent));
                    }
                }
            }
        }
        return new WorkspacePropertiesDTO(files, directories, totalSize, lastModified);
    }

    public void create(Long agentId, String path, CreateEntryRequest.Type type) throws IOException {
        Path root = resolveRoot(agentId);
        Path lexicalTarget = resolveWithin(root, path);
        if (Files.exists(lexicalTarget, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("已存在同名条目");
        }
        Path target = resolveForWrite(root, path, true);
        if (type == CreateEntryRequest.Type.DIR) {
            Files.createDirectory(target);
        } else {
            Files.createFile(target);
        }
    }

    public FileEntryDTO upload(Long agentId, String dir, MultipartFile file) throws IOException {
        Path root = resolveRoot(agentId);
        Path targetDir = resolveDirectory(root, dir);
        String filename = sanitizeFilename(file.getOriginalFilename());
        if (filename.isBlank()) {
            throw new IllegalArgumentException("文件名无效");
        }
        Path target = resolveForWrite(root, relativePath(root, targetDir.resolve(filename)), false);
        if (Files.isDirectory(target)) {
            throw new IllegalArgumentException("目标是一个目录");
        }
        Files.deleteIfExists(target);
        file.transferTo(target);
        return toEntry(root, target);
    }

    /**
     * 将已在工作区外完整合并并校验的文件落入工作区。FAIL 冲突时由调用方保留上传会话。
     */
    public UploadCompleteDTO storeUploadedFile(Long agentId, String targetDir, String fileName,
                                               Path mergedFile, String conflict) throws IOException {
        Path root = resolveRoot(agentId);
        Path destinationDir = resolveDirectory(root, targetDir);
        String validName = validateFileName(fileName);
        ConflictStrategy strategy = ConflictStrategy.from(conflict);
        if (!Files.isRegularFile(mergedFile)) {
            throw new IllegalArgumentException("合并文件不存在");
        }

        synchronized (transferLock) {
            Path intended = destinationDir.resolve(validName).normalize();
            Path target = resolveForWrite(root, relativePath(root, intended), false);
            boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            if (exists && strategy == ConflictStrategy.FAIL) {
                throw new IllegalArgumentException("目标已存在");
            }
            if (exists && strategy == ConflictStrategy.SKIP) {
                return new UploadCompleteDTO(null, true, relativePath(root, target));
            }
            if (exists && strategy == ConflictStrategy.RENAME) {
                target = uniqueTarget(destinationDir, validName);
            }
            if (exists && strategy == ConflictStrategy.OVERWRITE && Files.isDirectory(target)) {
                throw new IllegalArgumentException("不能以上传文件覆盖目录");
            }

            // 先在与目标相同的文件系统写完整暂存文件，再替换目标，避免复制失败时旧文件已被删除。
            Path staged = Files.createTempFile(destinationDir, ".meowclaw-upload-", ".tmp");
            boolean moved = false;
            try {
                Files.copy(mergedFile, staged, StandardCopyOption.REPLACE_EXISTING);
                while (true) {
                    Path candidate = target;
                    if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                        if (strategy == ConflictStrategy.FAIL) {
                            throw new IllegalArgumentException("目标已存在");
                        }
                        if (strategy == ConflictStrategy.RENAME) {
                            candidate = uniqueTarget(destinationDir, validName);
                        } else if (strategy == ConflictStrategy.SKIP) {
                            return new UploadCompleteDTO(null, true, relativePath(root, candidate));
                        } else if (Files.isDirectory(candidate)) {
                            throw new IllegalArgumentException("不能以上传文件覆盖目录");
                        }
                    }
                    try {
                        movePath(strategy == ConflictStrategy.OVERWRITE, staged, candidate);
                        moved = true;
                        return new UploadCompleteDTO(toEntry(root, candidate), false, relativePath(root, candidate));
                    } catch (FileAlreadyExistsException e) {
                        if (strategy == ConflictStrategy.FAIL) {
                            throw new IllegalArgumentException("目标已存在", e);
                        }
                        if (strategy != ConflictStrategy.RENAME) {
                            throw e;
                        }
                        target = uniqueTarget(destinationDir, validName);
                    }
                }
            } finally {
                if (!moved) {
                    Files.deleteIfExists(staged);
                }
            }
        }
    }

    public Path resolveRoot(Long agentId) throws IOException {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        String folder = agent.getWorkspaceFolder();
        if (folder == null || folder.isBlank()) {
            throw new IllegalStateException("智能体未配置工作区");
        }
        Path root = Paths.get(folder).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root.toRealPath();
    }

    /**
     * 将相对路径解析到工作区根之内，越界（含绝对路径、.. 穿越）一律拒绝。
     */
    public Path resolveWithin(Path root, String relativePath) {
        String rel = relativePath == null ? "" : relativePath;
        Path resolved = root.resolve(rel).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法路径：" + rel);
        }
        return resolved;
    }

    public Path resolveExisting(Path root, String relativePath) throws IOException {
        Path target = resolveWithin(root, relativePath);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("路径不存在");
        }
        return ensureRealPathWithin(root, target);
    }

    /** 用于递归复制、属性和 ZIP，拒绝树中的任何符号链接。 */
    public Path resolveTree(Path root, String relativePath) throws IOException {
        Path lexical = resolveWithin(root, relativePath);
        rejectSymbolicLink(lexical);
        Path source = resolveExisting(root, relativePath);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path current : walk.toList()) {
                rejectSymbolicLink(current);
                ensureRealPathWithin(root, current);
            }
        }
        return source;
    }

    public Path resolveDirectory(Path root, String relativePath) throws IOException {
        Path directory = resolveExisting(root, relativePath);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("目标路径不是目录");
        }
        return directory;
    }

    public Path resolveForWrite(Path root, String relativePath, boolean createParents) throws IOException {
        Path target = resolveWithin(root, relativePath);
        Path ancestor = target;
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null || !ancestor.startsWith(root)) {
            throw new IllegalArgumentException("非法路径：" + relativePath);
        }
        ensureRealPathWithin(root, ancestor);

        Path parent = target.getParent();
        if (parent != null) {
            if (createParents) {
                Files.createDirectories(parent);
            }
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                Path realParent = ensureRealPathWithin(root, parent);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    return ensureRealPathWithin(root, target);
                }
                return realParent.resolve(target.getFileName().toString()).normalize();
            }
        }
        return target;
    }

    public String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    public String validateFileName(String fileName) {
        String name = fileName == null ? "" : fileName;
        if (name.isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                || name.chars().anyMatch(ch -> ch < 32 || "<>:\"|?*".indexOf(ch) >= 0)) {
            throw new IllegalArgumentException("文件名无效");
        }
        Path parsed = Paths.get(name);
        if (parsed.isAbsolute() || parsed.getNameCount() != 1 || !parsed.getFileName().toString().equals(name)) {
            throw new IllegalArgumentException("文件名无效");
        }
        return name;
    }

    /** 递归删除且向调用方传播任何失败，不再只记录日志后返回成功。 */
    public static void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(target);
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private CopyOutcome copyOne(Path source, Path target, ConflictStrategy strategy) throws IOException {
        rejectOverwriteAncestor(source, target, strategy);
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (exists) {
            switch (strategy) {
                case FAIL -> throw new IllegalArgumentException("目标已存在");
                case SKIP -> {
                    return new CopyOutcome(target, true);
                }
                case RENAME -> target = uniqueTarget(target.getParent(), target.getFileName().toString());
                case OVERWRITE -> deleteRecursively(target);
            }
        }
        copyTree(source, target);
        return new CopyOutcome(target, false);
    }

    private void copyTree(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            throw new IllegalArgumentException("不支持复制符号链接");
        }
        if (Files.isDirectory(source)) {
            Files.createDirectory(target);
            try (Stream<Path> children = Files.list(source)) {
                for (Path child : children.toList()) {
                    copyTree(child, target.resolve(child.getFileName().toString()));
                }
            }
        } else if (Files.isRegularFile(source)) {
            Files.copy(source, target);
        } else {
            throw new IllegalArgumentException("不支持的文件类型");
        }
    }

    private MoveOutcome moveOne(Path source, Path target, ConflictStrategy strategy) throws IOException {
        rejectOverwriteAncestor(source, target, strategy);
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (exists) {
            switch (strategy) {
                case FAIL -> throw new IllegalArgumentException("目标已存在");
                case SKIP -> {
                    return new MoveOutcome(target, true);
                }
                case RENAME -> target = uniqueTarget(target.getParent(), target.getFileName().toString());
                case OVERWRITE -> deleteRecursively(target);
            }
        }
        while (true) {
            try {
                movePath(false, source, target);
                return new MoveOutcome(target, false);
            } catch (FileAlreadyExistsException e) {
                if (strategy != ConflictStrategy.RENAME) {
                    if (strategy == ConflictStrategy.FAIL) {
                        throw new IllegalArgumentException("目标已存在", e);
                    }
                    throw e;
                }
                target = uniqueTarget(target.getParent(), target.getFileName().toString());
            }
        }
    }

    private void rejectOverwriteAncestor(Path source, Path target, ConflictStrategy strategy) {
        if (strategy == ConflictStrategy.OVERWRITE && source.startsWith(target)) {
            throw new IllegalArgumentException("不能覆盖包含来源的目标目录");
        }
    }

    private Path uniqueTarget(Path parent, String filename) {
        String base = filename;
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            base = filename.substring(0, dot);
            ext = filename.substring(dot);
        }
        for (long i = 1; ; i++) {
            String candidateName;
            try {
                candidateName = base + " (" + i + ")" + ext;
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("无法生成唯一文件名", e);
            }
            Path candidate = parent.resolve(candidateName).normalize();
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }
    }

    private void movePath(boolean replace, Path source, Path target) throws IOException {
        if (replace) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            // 不带 REPLACE_EXISTING 的普通移动在目标竞争出现时可靠地失败；
            // ATOMIC_MOVE 在部分平台上可能忽略目标已存在语义，不能用于 FAIL/RENAME。
            Files.move(source, target);
        }
    }

    private Path ensureRealPathWithin(Path root, Path path) throws IOException {
        Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("路径不存在", e);
        }
        if (!realPath.startsWith(root)) {
            throw new IllegalArgumentException("非法路径：" + path);
        }
        return realPath;
    }

    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("不支持符号链接：" + path.getFileName());
        }
    }

    private void requirePaths(List<String> paths) {
        if (paths == null || paths.isEmpty() || paths.stream().anyMatch(path -> path == null)) {
            throw new IllegalArgumentException("路径不能为空");
        }
    }

    private BatchOperationResultDTO singleResult(String source, String target, boolean success,
                                                  boolean skipped, String message) {
        return new BatchOperationResultDTO(List.of(item(source, target, success, skipped, message)));
    }

    private BatchOperationItemDTO item(String source, String target, boolean success,
                                       boolean skipped, String message) {
        return new BatchOperationItemDTO(source, target, success, skipped, message);
    }

    private String errorMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }

    private FileEntryDTO toEntry(Path root, Path p) {
        String rel = relativePath(root, p);
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
        return new FileEntryDTO(p.getFileName().toString(), rel, Files.isDirectory(p), size, lastModified);
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
            String part = parts[i];
            if (!part.isEmpty() && !part.equals(".") && !part.equals("..")) {
                return part;
            }
        }
        return "";
    }

    private record CopyOutcome(Path target, boolean skipped) {
    }

    private record MoveOutcome(Path target, boolean skipped) {
    }
}
