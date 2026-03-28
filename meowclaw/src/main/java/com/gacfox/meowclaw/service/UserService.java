package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.UpdatePasswordDto;
import com.gacfox.meowclaw.dto.UpdateProfileDto;
import com.gacfox.meowclaw.dto.UserDto;
import com.gacfox.meowclaw.entity.User;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.UserRepository;
import com.gacfox.meowclaw.security.CurrentUserHolder;
import com.gacfox.meowclaw.util.PasswordUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PasswordUtil passwordUtil;

    public UserService(UserRepository userRepository, FileStorageService fileStorageService, PasswordUtil passwordUtil) {
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.passwordUtil = passwordUtil;
    }

    public UserDto getCurrentUser() {
        Long userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            throw new ServiceNotSatisfiedException("用户未登录");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("用户不存在"));

        return toDto(user);
    }

    public UserDto updateProfile(UpdateProfileDto dto, MultipartFile avatar) {
        Long userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            throw new ServiceNotSatisfiedException("用户未登录");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("用户不存在"));

        if (dto.getDisplayUsername() != null) {
            user.setDisplayUsername(dto.getDisplayUsername());
        }

        if (avatar != null && !avatar.isEmpty()) {
            String oldAvatarUrl = user.getAvatarUrl();
            String newAvatarUrl = fileStorageService.updateFile(avatar, oldAvatarUrl, "avatars/users");
            user.setAvatarUrl(newAvatarUrl);
        }

        userRepository.save(user);
        return toDto(user);
    }

    public void updatePassword(UpdatePasswordDto dto) {
        Long userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            throw new ServiceNotSatisfiedException("用户未登录");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("用户不存在"));

        user.setPassword(passwordUtil.encode(dto.getPassword()));
        userRepository.save(user);
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }
}
