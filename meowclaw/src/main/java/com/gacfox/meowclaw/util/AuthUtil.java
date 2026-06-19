package com.gacfox.meowclaw.util;

import com.gacfox.meowclaw.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AuthUtil {

    private static final String USER_ATTR = "currentUser";

    public static UserDTO getCurrentUser() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        return (UserDTO) request.getAttribute(USER_ATTR);
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
