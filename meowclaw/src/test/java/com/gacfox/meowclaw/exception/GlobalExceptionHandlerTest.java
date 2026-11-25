package com.gacfox.meowclaw.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void doesNotWriteJsonErrorForClientDisconnect() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/pdf");
        IOException abort = new IOException("你的主机中的软件中止了一个已建立的连接。");

        assertThat(handler.handleException(abort, response)).isNull();
    }
}
