/*
 * Copyright (c) 2018-present, easy-4-java.
 */
package io.github.easy4j.comfy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClientConfig;

class ComfyCliExecutorTest {

    private static final String ECHO =
            Paths.get("src", "test", "resources", "comfy-echo.sh").toAbsolutePath().toString();
    private static final String SLOW =
            Paths.get("src", "test", "resources", "comfy-slow.sh").toAbsolutePath().toString();

    private ComfyClientConfig configFor(String executable) {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(executable);
        config.setLocalTimeoutSeconds(2);
        config.setLocalProbeTimeoutSeconds(1);
        return config;
    }

    @Test
    void shouldExecuteSuccessfullyWithCapturedStdout() {
        ComfyCliResult result = new ComfyCliExecutor(configFor(ECHO)).execute("hello", "world");
        assertEquals(0, result.getExitCode());
        assertTrue(result.isSuccess());
        assertEquals("hello world", result.getStdout());
    }

    @Test
    void shouldPassArgumentsRawWithoutShellInterpretation() {
        ComfyCliResult result = new ComfyCliExecutor(configFor(ECHO))
                .execute("a b", ";rm -rf /", "$(whoami)");
        assertEquals("a b ;rm -rf / $(whoami)", result.getStdout());
    }

    @Test
    void shouldPreserveRealExitCodeAndStreamsOnNonZeroExit() {
        ComfyCliResult result = new ComfyCliExecutor(configFor("/bin/sh"))
                .execute("-c", "echo out-marker; echo err-marker 1>&2; exit 7");
        assertEquals(7, result.getExitCode());
        assertFalse(result.isSuccess());
        assertTrue(result.getStdout().contains("out-marker"));
        assertTrue(result.getStderr().contains("err-marker"));
    }

    @Test
    void shouldDecodeUtf8Output() {
        ComfyCliResult result = new ComfyCliExecutor(configFor("/bin/sh"))
                .execute("-c", "printf '\\344\\275\\240\\345\\245\\275'");
        assertEquals("你好", result.getStdout());
    }

    @Test
    void shouldFeedStdinAndCloseIt() {
        ComfyCliResult result = new ComfyCliExecutor(configFor("/bin/cat"))
                .executeWithStdin("payload");
        assertEquals("payload", result.getStdout());
    }

    @Test
    void shouldInjectEnvironmentIntoChildProcess() {
        ComfyClientConfig config = configFor("/usr/bin/env");
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("COMFY_PROBE_MARKER", "injected-ok");
        config.setEnvironment(env);
        assertTrue(new ComfyCliExecutor(config).execute().getStdout()
                .contains("COMFY_PROBE_MARKER=injected-ok"));
    }

    @Test
    void shouldUseDedicatedProbeTimeout() {
        ComfyClientConfig config = configFor(SLOW);
        config.setLocalTimeoutSeconds(30);
        config.setLocalProbeTimeoutSeconds(1);
        long started = System.nanoTime();
        assertFalse(new ComfyCliExecutor(config).probe());
        long millis = (System.nanoTime() - started) / 1_000_000L;
        assertTrue(millis < 4_000L, "probe must not inherit the 30 second execution timeout");
    }

    @Test
    void shouldBoundCapturedOutput() {
        ComfyClientConfig config = configFor("/bin/sh");
        config.setMaxOutputBytes(8);
        ComfyCliResult result = new ComfyCliExecutor(config)
                .execute("-c", "printf '12345678901234567890'; printf 'abcdefghijklmnop' 1>&2");
        assertEquals("12345678", result.getStdout());
        assertEquals("abcdefgh", result.getStderr());
        assertTrue(result.isStdoutTruncated());
        assertTrue(result.isStderrTruncated());
        assertTrue(result.isTruncated());
    }

    @Test
    void shouldTimeoutOnHangingProcess() {
        ComfyClientConfig config = configFor("/bin/sh");
        config.setLocalTimeoutSeconds(1);
        ComfyCliResult result = new ComfyCliExecutor(config).execute("-c", "sleep 30");
        assertEquals(-1, result.getExitCode());
        assertTrue(result.isTimeout());
    }

    @Test
    void shouldReturnMessageWhenExecutableMissing() {
        ComfyCliResult result = new ComfyCliExecutor(configFor("/nonexistent/path/to/comfy"))
                .execute("--version");
        assertEquals(-1, result.getExitCode());
        assertFalse(result.isSuccess());
        assertTrue(result.getStderr() != null && !result.getStderr().isEmpty());
    }
}
