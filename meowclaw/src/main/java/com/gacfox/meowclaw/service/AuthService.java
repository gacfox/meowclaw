package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.entity.User;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.UserRepository;
import com.gacfox.meowclaw.util.JwtUtil;
import com.gacfox.meowclaw.util.PasswordUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordUtil passwordUtil, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordUtil = passwordUtil;
        this.jwtUtil = jwtUtil;
    }

    public boolean isInitialized() {
        return userRepository.count() > 0;
    }

    public void init(InitDto dto) {
        if (isInitialized()) {
            throw new ServiceNotSatisfiedException("系统已初始化");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordUtil.encode(dto.getPassword()));
        user.setCreatedAtInstant(Instant.now());
        userRepository.save(user);
    }

    public TokenDto login(LoginDto dto) {
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new ServiceNotSatisfiedException("用户名或密码错误"));

        if (!passwordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new ServiceNotSatisfiedException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        TokenDto tokenDto = new TokenDto();
        tokenDto.setToken(token);
        return tokenDto;
    }
}
