package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ChangePasswordRequest;
import com.gacfox.meowclaw.dto.UpdateProfileRequest;
import com.gacfox.meowclaw.dto.UserDTO;
import com.gacfox.meowclaw.service.UserService;
import com.gacfox.meowclaw.util.AuthUtil;
import com.gacfox.proarc.common.model.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/profile")
    public ApiResult<UserDTO> updateProfile(@RequestBody @Valid UpdateProfileRequest req) {
        UserDTO user = userService.updateProfile(AuthUtil.getCurrentUserId(), req);
        return ApiResult.success(user);
    }

    @PutMapping("/password")
    public ApiResult<?> changePassword(@RequestBody @Valid ChangePasswordRequest req) {
        userService.changePassword(AuthUtil.getCurrentUserId(), req);
        return ApiResult.success();
    }

    @PostMapping("/avatar")
    public ApiResult<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        String avatarUrl = userService.updateAvatar(AuthUtil.getCurrentUserId(), file);
        return ApiResult.success("success", avatarUrl);
    }
}
