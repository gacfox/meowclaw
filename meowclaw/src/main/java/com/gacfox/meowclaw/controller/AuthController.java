package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/init/status")
    public ApiResponse<Map<String, Boolean>> getInitStatus() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("initialized", authService.isInitialized());
        return ApiResponse.success(result);
    }

    @PostMapping("/init")
    public ApiResponse<Void> init(@Valid @RequestBody InitDto dto) {
        authService.init(dto);
        return ApiResponse.success();
    }

    @PostMapping("/login")
    public ApiResponse<TokenDto> login(@Valid @RequestBody LoginDto dto) {
        return ApiResponse.success(authService.login(dto));
    }
}
