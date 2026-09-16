/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.easy4j.comfy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClientConfig;

/**
 * Unit tests for {@link ComfyCliExecutor}: raw argv passing, exit-code
 * preservation, stream capture, stdin piping and the watchdog timeout.
 *
 * @since 1.0.0
 */
class ComfyCliExecutorTest {

    /** Absolute path of the argument-echoing fixture script (surefire runs from the module base dir). */
    private static final String ECHO =
            java.nio.file.Paths.get("src", "test", "resources", "comfy-echo.sh").toAbsolutePath().toString();

    private ComfyClientConfig configFor(String executable) {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(executable);
        // Short timeouts so failing tests stay fast.
        config.setLocalTimeoutSeconds(2);
        config.setLocalProbeTimeoutSeconds(2);
        return config;
    }

    @Test
    void shouldExecuteSuccessfullyWithCapturedStdout() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor(ECHO));

        ComfyCliResult result = executor.execute("hello", "world");

        assertEquals(0, result.getExitCode());
        assertTrue(result.isSuccess());
        assertEquals("hello world", result.getStdout());
    }

    @Test
    void shouldPassArgumentsRawWithoutEmbeddedQuotes() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor(ECHO));

        ComfyCliResult result = executor.execute("Write a failing test", "--model", "kimi k2");

        assertEquals("Write a failing test --model kimi k2", result.getStdout(),
                "multi-word arguments must arrive without embedded literal quotes");
    }

    @Test
    void shouldPreserveRealExitCodeAndStreamsOnNonZeroExit() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor("/bin/sh"));

        ComfyCliResult result = executor.execute("-c", "echo out-marker; echo err-marker 1>&2; exit 7");

        assertEquals(7, result.getExitCode());
        assertFalse(result.isSuccess());
        assertTrue(result.getStdout().contains("out-marker"), "stdout must survive a non-zero exit");
        assertTrue(result.getStderr().contains("err-marker"), "stderr must survive a non-zero exit");
    }

    @Test
    void shouldReturnIoExceptionMessageWhenExecutableMissing() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor("/nonexistent/path/to/comfy"));

        ComfyCliResult result = executor.execute("--version");

        assertEquals(-1, result.getExitCode());
        assertFalse(result.isSuccess());
        assertTrue(result.getStderr() != null && !result.getStderr().isEmpty());
    }

    @Test
    void shouldDecodeUtf8OutputRegardlessOfPlatformCharset() {
        // POSIX printf octal escapes emit 你好 as raw UTF-8 bytes; with a
        // platform-default-charset decode this corrupts on C-locale JVMs.
        // NOTE: the backslashes are doubled in Java source so the shell
        // receives single ones — an octal escape like \344 must be written
        // \\344 here or the compiler eats it at compile time.
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor("/bin/sh"));

        ComfyCliResult result = executor.execute("-c", "printf '\\344\\275\\240\\345\\245\\275'");

        assertEquals("你好", result.getStdout());
    }

    @Test
    void shouldIgnoreNullArguments() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor(ECHO));

        ComfyCliResult result = executor.execute("hello", null, "world");

        assertEquals(0, result.getExitCode());
        assertEquals("hello world", result.getStdout());
    }

    @Test
    void shouldFeedStdinToChildProcess() {
        // `cat` with no file arguments echoes its standard input verbatim,
        // which is how stdin-consuming CLI forms receive their payload.
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor("/bin/cat"));

        ComfyCliResult result = executor.executeWithStdin("secret-api-key");

        assertEquals(0, result.getExitCode());
        assertEquals("secret-api-key", result.getStdout());
    }

    @Test
    void shouldExecuteWithoutStdinAsBefore() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor(ECHO));

        assertEquals("plain", executor.executeWithStdin(null, "plain").getStdout());
        assertEquals("plain", executor.executeWithStdin("", "plain").getStdout());
    }

    @Test
    void shouldInjectEnvironmentIntoChildProcess() {
        ComfyClientConfig config = configFor("/usr/bin/env");
        java.util.Map<String, String> env = new java.util.LinkedHashMap<String, String>();
        env.put("COMFY_PROBE_MARKER", "injected-ok");
        config.setEnvironment(env);

        ComfyCliResult result = executor(config).execute();

        assertTrue(result.getStdout().contains("COMFY_PROBE_MARKER=injected-ok"),
                "child environment must carry the injected variables");
    }

    private ComfyCliExecutor executor(ComfyClientConfig config) {
        return new ComfyCliExecutor(config);
    }

    @Test
    void shouldReportSuccessFromProbeWhenExecutableWorks() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor(ECHO));

        assertTrue(executor.probe());
    }

    @Test
    void shouldReportFailureFromProbeWhenExecutableMissing() {
        ComfyCliExecutor executor = new ComfyCliExecutor(configFor("/nonexistent/path/to/comfy"));

        assertFalse(executor.probe());
    }

    @Test
    void shouldTimeoutOnHangingProcess() {
        // Use a short timeout and a command that sleeps for a long time.
        ComfyClientConfig config = configFor("/bin/sh");
        config.setLocalTimeoutSeconds(1);
        ComfyCliExecutor executor = new ComfyCliExecutor(config);

        ComfyCliResult result = executor.execute("-c", "sleep 30");

        assertEquals(-1, result.getExitCode());
        assertFalse(result.isSuccess());
        assertTrue(result.isTimeout(), "stderr must carry the timeout notice");
    }
}
