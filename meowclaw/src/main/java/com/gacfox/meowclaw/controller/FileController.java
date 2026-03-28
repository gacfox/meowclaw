package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) throws IOException {
        String requestPath = request.getRequestURI();
        String prefix = "/api/files/";

        if (!requestPath.startsWith(prefix)) {
            return ResponseEntity.badRequest().build();
        }

        String relativePath = requestPath.substring(prefix.length());

        Path rootPath = fileStorageService.getRootPath();
        Path filePath = rootPath.resolve(relativePath).normalize();

        if (!filePath.startsWith(rootPath)) {
            return ResponseEntity.badRequest().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = request.getServletContext().getMimeType(filePath.toString());
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String filename = filePath.getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}
