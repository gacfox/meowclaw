package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.InitRequest;
import com.gacfox.meowclaw.dto.LoginRequest;
import com.gacfox.meowclaw.dto.UserDTO;
import com.gacfox.meowclaw.service.UserService;
import com.gacfox.meowclaw.util.AuthUtil;
import com.gacfox.meowclaw.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 初始化、登录和退出
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "检查系统初始化状态")
    @GetMapping("/check-init")
    public ApiResult<Boolean> checkInit() {
        return ApiResult.success(!userService.isSystemInitialized());
    }

    @Operation(summary = "初始化系统")
    @PostMapping("/init")
    public ApiResult<?> init(@RequestBody @Valid InitRequest req) {
        userService.initSystem(req);
        return ApiResult.success();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody @Valid LoginRequest req) {
        UserDTO user = userService.login(req);
        String token = jwtUtil.generateToken(user);
        return ApiResult.success(Map.of("user", user, "token", token));
    }

    @Operation(summary = "用户退出")
    @PostMapping("/logout")
    public ApiResult<?> logout() {
        return ApiResult.success();
    }

    @Operation(summary = "查询当前用户")
    @GetMapping("/me")
    public ApiResult<UserDTO> me() {
        UserDTO user = userService.getCurrentUser(AuthUtil.getCurrentUserId());
        return ApiResult.success(user);
    }
}
