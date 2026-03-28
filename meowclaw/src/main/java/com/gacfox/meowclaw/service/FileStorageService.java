package com.gacfox.meowclaw.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {
    @Value("${storage.path:./data/files}")
    private String storagePath;
    @Value("${storage.url-prefix:/api/files}")
    private String urlPrefix;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    @Getter
    private Path rootPath;

    @PostConstruct
    public void init() {
        try {
            rootPath = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(rootPath);
            log.info("File storage initialized at: {}", rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize file storage", e);
        }
    }

    public String storeFile(MultipartFile file, String subdirectory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateFile(file);

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String filename = UUID.randomUUID() + extension;

            Path targetDir = rootPath.resolve(subdirectory);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File stored: {}", targetPath);
            return urlPrefix + "/" + subdirectory + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public String updateFile(MultipartFile file, String oldUrl, String subdirectory) {
        String newUrl = storeFile(file, subdirectory);

        if (newUrl != null && oldUrl != null && !oldUrl.isEmpty()) {
            deleteFile(oldUrl);
        }

        return newUrl;
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        try {
            String relativePath = fileUrl.replaceFirst(urlPrefix + "/", "");
            Path filePath = rootPath.resolve(relativePath).normalize();

            if (filePath.startsWith(rootPath) && Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted: {}", filePath);

                deleteEmptyParentDirs(filePath.getParent());
            }
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", fileUrl, e);
        }
    }

    private void deleteEmptyParentDirs(Path dir) {
        try {
            while (dir != null && !dir.equals(rootPath)) {
                if (Files.isDirectory(dir) && Files.list(dir).findAny().isEmpty()) {
                    Files.delete(dir);
                    log.debug("Deleted empty directory: {}", dir);
                    dir = dir.getParent();
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            log.debug("Could not delete empty parent directories", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type. Allowed types: JPEG, PNG, GIF, WebP");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
