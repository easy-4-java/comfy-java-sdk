/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.easy4j.comfy.ComfyClientConfig;

/**
 * Synchronous, thread-safe subprocess executor for the local {@code comfy} CLI.
 *
 * <p>Arguments are passed as argv entries without a shell, stdout/stderr are
 * drained concurrently, output capture is bounded, and the real exit code is
 * preserved for non-zero exits.</p>
 */
public class ComfyCliExecutor {

    private static final Logger log = LoggerFactory.getLogger(ComfyCliExecutor.class);

    private final ComfyClientConfig config;

    public ComfyCliExecutor(ComfyClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public ComfyCliResult execute(String... args) {
        return runProcess(null, secondsToMillis(config.getLocalTimeoutSeconds()), args);
    }

    public ComfyCliResult executeWithStdin(String stdin, String... args) {
        return runProcess(stdin, secondsToMillis(config.getLocalTimeoutSeconds()), args);
    }

    ComfyCliResult executeWithTimeoutSeconds(int timeoutSeconds, String... args) {
        return runProcess(null, secondsToMillis(timeoutSeconds), args);
    }

    public boolean probe() {
        try {
            return executeWithTimeoutSeconds(config.getLocalProbeTimeoutSeconds(), "--version").isSuccess();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ComfyCliResult runProcess(String stdin, long timeoutMs, String... args) {
        CommandLine cmd = new CommandLine(config.getLocalExecutable());
        if (args != null) {
            for (String arg : args) {
                if (arg != null) {
                    cmd.addArgument(arg, false);
                }
            }
        }

        DefaultExecutor executor = new DefaultExecutor();
        Map<String, String> childEnv = null;
        if (config.getEnvironment() != null && !config.getEnvironment().isEmpty()) {
            childEnv = new LinkedHashMap<String, String>(System.getenv());
            childEnv.putAll(config.getEnvironment());
        }

        BoundedOutput stdout = new BoundedOutput(config.getMaxOutputBytes());
        BoundedOutput stderr = new BoundedOutput(config.getMaxOutputBytes());
        byte[] stdinBytes = stdin == null ? new byte[0] : stdin.getBytes(StandardCharsets.UTF_8);
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr, new ByteArrayInputStream(stdinBytes)));

        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMs);
        executor.setWatchdog(watchdog);
        long startNanos = System.nanoTime();

        try {
            int exitCode = childEnv == null ? executor.execute(cmd) : executor.execute(cmd, childEnv);
            String out = stdout.asUtf8().trim();
            String err = stderr.asUtf8().trim();
            log.debug("comfy CLI executed: exitCode={}, stdout.len={}, stdout.truncated={}, stderr.truncated={}",
                    exitCode, out.length(), stdout.isTruncated(), stderr.isTruncated());
            if (watchdog.killedProcess()) {
                return result(-1, out, timeoutMessage(timeoutMs, err), stdout, stderr);
            }
            return result(exitCode, out, err, stdout, stderr);
        } catch (ExecuteException e) {
            String out = stdout.asUtf8().trim();
            String err = stderr.asUtf8().trim();
            boolean timedOut = watchdog.killedProcess()
                    || System.nanoTime() - startNanos >= timeoutMs * 1_000_000L;
            if (timedOut) {
                return result(-1, out, timeoutMessage(timeoutMs, err), stdout, stderr);
            }
            return result(e.getExitValue(), out, err, stdout, stderr);
        } catch (IOException e) {
            return result(-1, "", safeMessage(e), stdout, stderr);
        }
    }

    private static ComfyCliResult result(int exitCode, String out, String err,
            BoundedOutput stdout, BoundedOutput stderr) {
        return new ComfyCliResult(exitCode, out, err, stdout.isTruncated(), stderr.isTruncated());
    }

    private static String timeoutMessage(long timeoutMs, String stderr) {
        String prefix = "comfy CLI timed out after " + timeoutMs + " ms";
        return stderr == null || stderr.isEmpty() ? prefix : prefix + "\n" + stderr;
    }

    private static String safeMessage(IOException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static long secondsToMillis(int seconds) {
        return Math.max(1L, (long) seconds) * 1000L;
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private final int maxBytes;
        private boolean truncated;

        private BoundedOutput(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(int b) {
            if (maxBytes < 0 || delegate.size() < maxBytes) {
                delegate.write(b);
            } else {
                truncated = true;
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int off, int len) {
            if (len <= 0) {
                return;
            }
            if (maxBytes < 0) {
                delegate.write(bytes, off, len);
                return;
            }
            int remaining = maxBytes - delegate.size();
            if (remaining > 0) {
                int accepted = Math.min(remaining, len);
                delegate.write(bytes, off, accepted);
                if (accepted < len) {
                    truncated = true;
                }
            } else {
                truncated = true;
            }
        }

        private synchronized String asUtf8() {
            return new String(delegate.toByteArray(), StandardCharsets.UTF_8);
        }

        private synchronized boolean isTruncated() {
            return truncated;
        }
    }
}
