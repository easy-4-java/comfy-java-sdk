/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.easy4j.comfy.ComfyClientConfig;

/**
 * Synchronous, thread-safe subprocess executor for the first-party
 * {@code comfy} CLI.
 *
 * <p>The implementation deliberately uses {@link ProcessBuilder} rather than a
 * shell: each Java argument becomes exactly one argv item, credentials stay in
 * the child environment, stdout/stderr capture is bounded, and timeout cleanup
 * owns the process and all three Java-side pipes directly. Reader/writer
 * threads are daemon threads and are joined with a bounded deadline.</p>
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
        List<String> command = new ArrayList<String>();
        command.add(config.getLocalExecutable());
        if (args != null) {
            for (String arg : args) {
                if (arg != null) command.add(arg);
            }
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        if (config.getEnvironment() != null && !config.getEnvironment().isEmpty()) {
            // ProcessBuilder starts with a mutable copy of the parent
            // environment, so overrides preserve PATH and all unrelated keys.
            Map<String, String> childEnv = builder.environment();
            childEnv.putAll(config.getEnvironment());
        }

        final Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return new ComfyCliResult(-1, "", safeMessage(e), false, false);
        }

        final CappedOutputStream stdout = new CappedOutputStream(config.getMaxStdoutBytes());
        final CappedOutputStream stderr = new CappedOutputStream(config.getMaxStderrBytes());
        final Thread stdoutReader = daemon("comfy-cli-stdout", () -> copy(process.getInputStream(), stdout));
        final Thread stderrReader = daemon("comfy-cli-stderr", () -> copy(process.getErrorStream(), stderr));
        final byte[] stdinBytes = stdin == null ? new byte[0] : stdin.getBytes(StandardCharsets.UTF_8);
        final Thread stdinWriter = daemon("comfy-cli-stdin", () -> writeAndClose(process.getOutputStream(), stdinBytes));

        stdoutReader.start();
        stderrReader.start();
        stdinWriter.start();

        boolean timedOut = false;
        int exitCode = -1;
        try {
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                timedOut = true;
                terminate(process);
            } else {
                exitCode = process.exitValue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut = true;
            terminate(process);
        } finally {
            // Closing our stdin pipe first guarantees a writer cannot extend
            // the call lifetime if the child/descendant stopped consuming it.
            closeQuietly(process.getOutputStream());
            join(stdinWriter, config.getStreamDrainTimeoutMillis());
            drainReaders(process, stdoutReader, stderrReader);
        }

        String out = decodeUtf8(stdout).trim();
        String err = decodeUtf8(stderr).trim();
        if (timedOut) {
            return timeoutResult(timeoutMs, out, err, stdout, stderr);
        }

        // A reader may still have been force-unblocked because a descendant
        // inherited the pipe after the direct child exited. The direct child's
        // real exit status remains authoritative; captured output is the
        // bounded prefix observed before the drain deadline.
        log.debug("comfy CLI executed: exitCode={}, stdout.len={}, stdout.truncated={}, stderr.truncated={}",
                exitCode, out.length(), stdout.isTruncated(), stderr.isTruncated());
        return new ComfyCliResult(exitCode, out, err, stdout.isTruncated(), stderr.isTruncated());
    }

    private void terminate(Process process) {
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (process.isAlive()
                    && !process.waitFor(config.getProcessShutdownGraceMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(config.getProcessShutdownGraceMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private void drainReaders(Process process, Thread stdoutReader, Thread stderrReader) {
        long drainMs = config.getStreamDrainTimeoutMillis();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(drainMs);
        joinUntil(stdoutReader, deadlineNanos);
        joinUntil(stderrReader, deadlineNanos);
        if (stdoutReader.isAlive() || stderrReader.isAlive()) {
            // A descendant may still own inherited pipe write-ends. Closing
            // our read-ends prevents that descendant from extending the Java
            // call lifetime or retaining reader threads.
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            long closeDeadline = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(Math.min(250L, drainMs));
            joinUntil(stdoutReader, closeDeadline);
            joinUntil(stderrReader, closeDeadline);
        }
    }

    private static void copy(InputStream input, OutputStream output) {
        byte[] buffer = new byte[8192];
        try {
            int n;
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
        } catch (IOException ignored) {
            // Expected when timeout/drain cleanup closes the Java-side pipe.
        } finally {
            closeQuietly(input);
        }
    }

    private static void writeAndClose(OutputStream output, byte[] bytes) {
        try {
            if (bytes.length > 0) {
                output.write(bytes);
                output.flush();
            }
        } catch (IOException ignored) {
            // The child may legitimately exit before consuming stdin.
        } finally {
            closeQuietly(output);
        }
    }

    private static Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void join(Thread thread, long millis) {
        if (thread == null || millis <= 0 || thread == Thread.currentThread()) return;
        try {
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinUntil(Thread thread, long deadlineNanos) {
        if (thread == null || thread == Thread.currentThread() || !thread.isAlive()) return;
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) return;
        long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
        if (millis <= 0) millis = 1;
        join(thread, millis);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static String safeMessage(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static ComfyCliResult timeoutResult(long timeoutMs, String out, String err,
                                                 CappedOutputStream stdout, CappedOutputStream stderr) {
        String detail = "comfy CLI timed out after " + timeoutMs + " ms";
        if (err != null && !err.isEmpty()) detail += "\n" + err;
        return new ComfyCliResult(-1, out, detail, stdout.isTruncated(), stderr.isTruncated());
    }

    private static String decodeUtf8(CappedOutputStream buffer) {
        return new String(buffer.snapshot(), StandardCharsets.UTF_8);
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
        public synchronized void write(int b) {
            totalBytes++;
            if (maxBytes == 0 || delegate.size() < maxBytes) delegate.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (b == null) throw new NullPointerException("b");
            if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
            totalBytes += len;
            if (maxBytes == 0) {
                delegate.write(b, off, len);
                return;
            }
            int remaining = maxBytes - delegate.size();
            if (remaining > 0) delegate.write(b, off, Math.min(remaining, len));
        }

        private synchronized byte[] snapshot() {
            return delegate.toByteArray();
        }

        private synchronized boolean isTruncated() {
            return maxBytes > 0 && totalBytes > delegate.size();
        }
    }
}
