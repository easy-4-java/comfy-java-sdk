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
import java.time.Duration;
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
 * Synchronous, stateless subprocess executor for {@code comfy}.
 *
 * <p>Arguments are passed as argv (never through a shell), output is decoded
 * as UTF-8, capture growth is bounded by configuration, and probes use their
 * own short timeout instead of the normal generation timeout.</p>
 */
public class ComfyCliExecutor {

    private static final Logger log = LoggerFactory.getLogger(ComfyCliExecutor.class);

    private final ComfyClientConfig config;

    public ComfyCliExecutor(ComfyClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.config.validate();
    }

    public ComfyCliResult execute(String... args) {
        return runProcess(null, secondsToMillis(config.getLocalTimeoutSeconds()), args);
    }

    public ComfyCliResult executeWithStdin(String stdin, String... args) {
        return runProcess(stdin, secondsToMillis(config.getLocalTimeoutSeconds()), args);
    }

    public boolean probe() {
        try {
            return runProcess(null, secondsToMillis(config.getLocalProbeTimeoutSeconds()), "--version").isSuccess();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static long secondsToMillis(int seconds) {
        return Math.multiplyExact((long) seconds, 1000L);
    }

    private ComfyCliResult runProcess(String stdin, long timeoutMs, String... args) {
        // Literal executable path/name: do not parse it as a command line.
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

        CappedOutputStream stdout = new CappedOutputStream(config.getMaxStdoutBytes());
        CappedOutputStream stderr = new CappedOutputStream(config.getMaxStderrBytes());
        byte[] stdinBytes = stdin == null ? new byte[0] : stdin.getBytes(StandardCharsets.UTF_8);
        PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr, new ByteArrayInputStream(stdinBytes));
        streamHandler.setStopTimeout(Duration.ofMillis(config.getStreamDrainTimeoutMillis()));
        executor.setStreamHandler(streamHandler);

        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMs);
        executor.setWatchdog(watchdog);

        long startNanos = System.nanoTime();
        try {
            int exitCode = childEnv == null ? executor.execute(cmd) : executor.execute(cmd, childEnv);
            String out = decodeUtf8(stdout).trim();
            String err = decodeUtf8(stderr).trim();
            log.debug("comfy CLI executed: exitCode={}, stdout.len={}, stdout.truncated={}, stderr.truncated={}",
                    exitCode, out.length(), stdout.isTruncated(), stderr.isTruncated());
            if (watchdog.killedProcess()) {
                return timeoutResult(timeoutMs, out, err, stdout, stderr);
            }
            return new ComfyCliResult(exitCode, out, err, stdout.isTruncated(), stderr.isTruncated());
        } catch (ExecuteException e) {
            String out = decodeUtf8(stdout).trim();
            String err = decodeUtf8(stderr).trim();
            boolean timedOut = watchdog.killedProcess()
                    || System.nanoTime() - startNanos >= timeoutMs * 1_000_000L;
            if (timedOut) {
                return timeoutResult(timeoutMs, out, err, stdout, stderr);
            }
            return new ComfyCliResult(e.getExitValue(), out, err, stdout.isTruncated(), stderr.isTruncated());
        } catch (IOException e) {
            String out = decodeUtf8(stdout).trim();
            String err = decodeUtf8(stderr).trim();
            boolean timedOut = watchdog.killedProcess()
                    || System.nanoTime() - startNanos >= timeoutMs * 1_000_000L;
            if (timedOut) {
                return timeoutResult(timeoutMs, out, err, stdout, stderr);
            }
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new ComfyCliResult(-1, out, message, stdout.isTruncated(), stderr.isTruncated());
        }
    }

    private static ComfyCliResult timeoutResult(long timeoutMs, String out, String err,
                                                 CappedOutputStream stdout, CappedOutputStream stderr) {
        String detail = "comfy CLI timed out after " + timeoutMs + " ms";
        if (err != null && !err.isEmpty()) {
            detail += "\n" + err;
        }
        return new ComfyCliResult(-1, out, detail, stdout.isTruncated(), stderr.isTruncated());
    }

    private static String decodeUtf8(CappedOutputStream buffer) {
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Prefix-retaining bounded capture to prevent unbounded child-output growth. */
    private static final class CappedOutputStream extends OutputStream {
        private final int maxBytes;
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private long totalBytes;

        private CappedOutputStream(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int b) {
            totalBytes++;
            if (maxBytes == 0 || delegate.size() < maxBytes) {
                delegate.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (b == null) {
                throw new NullPointerException("b");
            }
            if (off < 0 || len < 0 || off + len > b.length) {
                throw new IndexOutOfBoundsException();
            }
            totalBytes += len;
            if (maxBytes == 0) {
                delegate.write(b, off, len);
                return;
            }
            int remaining = maxBytes - delegate.size();
            if (remaining > 0) {
                delegate.write(b, off, Math.min(remaining, len));
            }
        }

        private byte[] toByteArray() {
            return delegate.toByteArray();
        }

        private boolean isTruncated() {
            return maxBytes > 0 && totalBytes > delegate.size();
        }
    }
}
