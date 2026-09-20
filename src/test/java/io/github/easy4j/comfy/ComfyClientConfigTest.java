/*
 * Copyright (c) 2018-present, easy-4-java.
 */
package io.github.easy4j.comfy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ComfyClientConfigTest {

    @Test
    void shouldExposeProductionSafeDefaults() {
        ComfyClientConfig config = new ComfyClientConfig();
        assertEquals("comfy", config.getLocalExecutable());
        assertEquals(600, config.getLocalTimeoutSeconds());
        assertEquals(5, config.getLocalProbeTimeoutSeconds());
        assertEquals(16 * 1024 * 1024, config.getMaxOutputBytes());
    }

    @Test
    void shouldValidateRoutingAndTimeouts() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setDefaultWhere("bogus");
        assertThrows(IllegalArgumentException.class, config::validate);

        config = new ComfyClientConfig();
        config.setLocalTimeoutSeconds(0);
        assertThrows(IllegalStateException.class, config::validate);

        config = new ComfyClientConfig();
        config.setLocalProbeTimeoutSeconds(0);
        assertThrows(IllegalStateException.class, config::validate);
    }
}
