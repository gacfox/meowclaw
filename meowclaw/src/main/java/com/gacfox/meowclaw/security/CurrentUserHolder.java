package com.gacfox.meowclaw.security;

import com.gacfox.meowclaw.dto.UserTokenDto;

public class CurrentUserHolder {

    private static final ThreadLocal<UserTokenDto> userHolder = new ThreadLocal<>();

    public static void set(UserTokenDto user) {
        userHolder.set(user);
    }

    public static UserTokenDto get() {
        return userHolder.get();
    }

    public static Long getUserId() {
        UserTokenDto user = userHolder.get();
        return user != null ? user.getUserId() : null;
    }

    public static String getUsername() {
        UserTokenDto user = userHolder.get();
        return user != null ? user.getUsername() : null;
    }

    public static void clear() {
        userHolder.remove();
    }
}
