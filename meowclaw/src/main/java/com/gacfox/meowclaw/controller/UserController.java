package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.UpdatePasswordDto;
import com.gacfox.meowclaw.dto.UpdateProfileDto;
import com.gacfox.meowclaw.dto.UserDto;
import com.gacfox.meowclaw.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public UserController(UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> getCurrentUser() {
        return ApiResponse.success(userService.getCurrentUser());
    }

    @PutMapping("/me/profile")
    public ApiResponse<UserDto> updateProfile(
            @RequestParam("data") String dataJson,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) throws Exception {
        UpdateProfileDto dto = objectMapper.readValue(dataJson, UpdateProfileDto.class);
        return ApiResponse.success(userService.updateProfile(dto, avatar));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody UpdatePasswordDto dto) {
        userService.updatePassword(dto);
        return ApiResponse.success(null);
    }
}
