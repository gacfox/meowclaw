package com.gacfox.meowclaw.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.UserDTO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {
    private static final String USER_CLAIM = "user";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${meowclaw.jwt.secret}") String secret,
                   @Value("${meowclaw.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public String generateToken(UserDTO user) {
        long now = System.currentTimeMillis();
        Map<String, Object> userMap = OBJECT_MAPPER.convertValue(user, new TypeReference<>() {});
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(USER_CLAIM, userMap)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    public UserDTO parseToken(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Object userObj = claims.get(USER_CLAIM);
        return OBJECT_MAPPER.convertValue(userObj, UserDTO.class);
    }
}
