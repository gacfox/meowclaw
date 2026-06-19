package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.UserConverter;
import com.gacfox.meowclaw.dto.ChangePasswordRequest;
import com.gacfox.meowclaw.dto.InitRequest;
import com.gacfox.meowclaw.dto.LoginRequest;
import com.gacfox.meowclaw.dto.UpdateProfileRequest;
import com.gacfox.meowclaw.dto.UserDTO;
import com.gacfox.meowclaw.entity.User;
import com.gacfox.meowclaw.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserConverter userConverter;
    private final PasswordEncoder passwordEncoder;

    @Value("${meowclaw.data-dir}")
    private String dataDir;

    @Autowired
    public UserService(UserRepository userRepository, UserConverter userConverter, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isSystemInitialized() {
        return userRepository.count() > 0;
    }

    @Transactional
    public void initSystem(InitRequest req) {
        if (isSystemInitialized()) {
            throw new IllegalArgumentException("系统已初始化");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getUsername());
        long now = System.currentTimeMillis();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserDTO login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return userConverter.toDTO(user);
    }

    @Transactional(readOnly = true)
    public UserDTO getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return userConverter.toDTO(user);
    }

    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (req.getUsername() != null && !req.getUsername().equals(user.getUsername())) {
            userRepository.findByUsername(req.getUsername()).ifPresent(u -> {
                throw new IllegalArgumentException("用户名已存在");
            });
            user.setUsername(req.getUsername());
        }
        if (req.getDisplayName() != null) {
            user.setDisplayName(req.getDisplayName());
        }
        user.setUpdatedAt(System.currentTimeMillis());
        return userConverter.toDTO(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);
    }

    @Transactional
    public String updateAvatar(Long userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        deleteAvatarFile(user.getAvatarUrl());

        Path avatarDir = Paths.get(dataDir, "upload", "avatar").toAbsolutePath();
        Files.createDirectories(avatarDir);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String filename = userId + "_" + System.currentTimeMillis() + ext;
        Path targetPath = avatarDir.resolve(filename);
        file.transferTo(targetPath);

        String avatarUrl = "/upload/avatar/" + filename;
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);
        return avatarUrl;
    }

    private void deleteAvatarFile(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) return;
        try {
            Path file = Paths.get(dataDir, avatarUrl).toAbsolutePath();
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
