/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClientConfig;

class ComfyCliHardeningTest {

    private static final String SLOW =
            Paths.get("src", "test", "resources", "slow-comfy.sh").toAbsolutePath().toString();

    @Test
    void probeMustUseDedicatedTimeout() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(SLOW);
        config.setLocalTimeoutSeconds(10);
        config.setLocalProbeTimeoutSeconds(1);
        ComfyCliExecutor executor = new ComfyCliExecutor(config);

        long started = System.nanoTime();
        assertFalse(executor.probe());
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(elapsedMs < 5000, "probe must not wait for the normal command timeout");
    }

    @Test
    void captureMustBeBoundedAndMarkedTruncated() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable("/bin/sh");
        config.setLocalTimeoutSeconds(2);
        config.setMaxStdoutBytes(4);
        config.setMaxStderrBytes(3);
        ComfyCliExecutor executor = new ComfyCliExecutor(config);

        ComfyCliResult result = executor.execute("-c", "printf 123456789; printf abcdef 1>&2");
        assertEquals(0, result.getExitCode());
        assertEquals("1234", result.getStdout());
        assertEquals("abc", result.getStderr());
        assertTrue(result.isStdoutTruncated());
        assertTrue(result.isStderrTruncated());
        assertTrue(result.isTruncated());
    }
}
