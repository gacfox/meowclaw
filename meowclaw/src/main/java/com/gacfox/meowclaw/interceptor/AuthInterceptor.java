package com.gacfox.meowclaw.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.UserDTO;
import com.gacfox.meowclaw.util.JwtUtil;
import com.gacfox.proarc.common.model.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ATTR = "currentUser";

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthInterceptor(ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            try {
                String token = authHeader.substring(BEARER_PREFIX.length());
                UserDTO user = jwtUtil.parseToken(token);
                request.setAttribute(USER_ATTR, user);
                return true;
            } catch (Exception ignored) {
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResult.failure("未登录")));
        return false;
    }
}
