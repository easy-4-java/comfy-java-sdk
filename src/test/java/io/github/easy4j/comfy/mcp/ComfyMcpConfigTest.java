/*
 * Copyright (c) 2018-present, easy-4-java.
 */
package io.github.easy4j.comfy.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ComfyMcpConfigTest {

    @Test
    void shouldExposeBoundedDefaults() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        assertEquals("comfy-mcp", config.getLocalExecutable());
        assertEquals("2024-11-05", config.getProtocolVersion());
        assertEquals(10_000, config.getConnectTimeoutMillis());
        assertEquals(900_000, config.getReadTimeoutMillis());
        assertEquals(2_000, config.getShutdownTimeoutMillis());
        assertEquals(16 * 1024 * 1024, config.getMaxFrameChars());
        assertEquals(4 * 1024 * 1024, config.getMaxContentChars());
    }

    @Test
    void shouldRejectInvalidTimeouts() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        config.setConnectTimeoutMillis(0);
        assertThrows(IllegalStateException.class, config::validate);

        config = new ComfyMcpConfig();
        config.setReadTimeoutMillis(0);
        assertThrows(IllegalStateException.class, config::validate);
    }
}
