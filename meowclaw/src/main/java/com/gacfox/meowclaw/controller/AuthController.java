package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.util.AuthUtil;
import com.gacfox.meowclaw.util.JwtUtil;
import com.gacfox.meowclaw.dto.InitRequest;
import com.gacfox.meowclaw.dto.LoginRequest;
import com.gacfox.meowclaw.dto.UserDTO;
import com.gacfox.meowclaw.service.UserService;
import com.gacfox.proarc.common.model.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    @GetMapping("/check-init")
    public ApiResult<Boolean> checkInit() {
        return ApiResult.success(!userService.isSystemInitialized());
    }

    @PostMapping("/init")
    public ApiResult<?> init(@RequestBody @Valid InitRequest req) {
        userService.initSystem(req);
        return ApiResult.success();
    }

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody @Valid LoginRequest req) {
        UserDTO user = userService.login(req);
        String token = jwtUtil.generateToken(user);
        return ApiResult.success(Map.of("user", user, "token", token));
    }

    @PostMapping("/logout")
    public ApiResult<?> logout() {
        return ApiResult.success();
    }

    @GetMapping("/me")
    public ApiResult<UserDTO> me() {
        UserDTO user = userService.getCurrentUser(AuthUtil.getCurrentUserId());
        return ApiResult.success(user);
    }
}
