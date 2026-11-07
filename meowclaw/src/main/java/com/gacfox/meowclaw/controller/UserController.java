package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.ChangePasswordRequest;
import com.gacfox.meowclaw.dto.UpdateProfileRequest;
import com.gacfox.meowclaw.dto.UserDTO;
import com.gacfox.meowclaw.service.UserService;
import com.gacfox.meowclaw.util.AuthUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 用户管理
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public ApiResult<UserDTO> updateProfile(@RequestBody @Valid UpdateProfileRequest req) {
        UserDTO user = userService.updateProfile(AuthUtil.getCurrentUserId(), req);
        return ApiResult.success(user);
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public ApiResult<?> changePassword(@RequestBody @Valid ChangePasswordRequest req) {
        userService.changePassword(AuthUtil.getCurrentUserId(), req);
        return ApiResult.success();
    }

    @Operation(summary = "上传用户头像")
    @PostMapping("/avatar")
    public ApiResult<String> uploadAvatar(@Parameter(description = "头像图片文件") @RequestParam("file") MultipartFile file) throws IOException {
        String avatarUrl = userService.updateAvatar(AuthUtil.getCurrentUserId(), file);
        return ApiResult.success("success", avatarUrl);
    }
}
