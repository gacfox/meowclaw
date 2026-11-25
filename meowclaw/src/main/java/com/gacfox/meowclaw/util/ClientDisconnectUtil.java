package com.gacfox.meowclaw.util;

import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.Locale;

/**
 * 识别浏览器翻页、关闭预览或取消下载导致的正常连接中断。
 */
public final class ClientDisconnectUtil {
    private ClientDisconnectUtil() {
    }

    public static boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ClientAbortException || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset")
                        || normalized.contains("connection aborted")
                        || normalized.contains("established connection was aborted")
                        || normalized.contains("中止了一个已建立的连接")
                        || normalized.contains("强迫关闭了一个现有的连接")) {
                    return true;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}
