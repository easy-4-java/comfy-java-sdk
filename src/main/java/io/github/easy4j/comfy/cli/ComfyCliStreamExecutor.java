/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.github.easy4j.comfy.ComfyClientConfig;
import io.github.easy4j.comfy.model.ComfyCliEvent;

/**
 * Incremental NDJSON subprocess executor for long-running comfy commands.
 * stdout is decoded line-by-line while stderr is drained continuously.
 */
public final class ComfyCliStreamExecutor {
    private final ComfyClientConfig config;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public ComfyCliStreamExecutor(ComfyClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public ComfyCliStreamSession execute(final ComfyCliStreamListener listener, String... args) {
        Objects.requireNonNull(listener, "listener");
        List<String> command = new ArrayList<String>();
        command.add(config.getLocalExecutable());
        if (args != null) {
            for (String arg : args) if (arg != null) command.add(arg);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> env = builder.environment();
        if (config.getEnvironment() != null) env.putAll(config.getEnvironment());

        final Process process;
        try {
            process = builder.start();
            process.getOutputStream().close();
        } catch (IOException e) {
            CompletableFuture<ComfyCliResult> failed = new CompletableFuture<ComfyCliResult>();
            failed.completeExceptionally(e);
            try { listener.onError(e); } catch (RuntimeException ignored) { }
            throw new IllegalStateException("Failed to spawn comfy stream process", e);
        }

        final CompletableFuture<ComfyCliResult> completion = new CompletableFuture<ComfyCliResult>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicReference<Throwable> listenerFailure = new AtomicReference<Throwable>();
        final CappedBytes stdout = new CappedBytes(config.getMaxStdoutBytes());
        final CappedBytes stderr = new CappedBytes(config.getMaxStderrBytes());

        final Thread out = daemon("comfy-cli-stream-stdout", new Runnable() {
            @Override public void run() {
                readStdout(process, listener, stdout, listenerFailure);
            }
        });
        final Thread err = daemon("comfy-cli-stream-stderr", new Runnable() {
            @Override public void run() {
                readStderr(process, listener, stderr, listenerFailure);
            }
        });
        final Thread waiter = daemon("comfy-cli-stream-waiter", new Runnable() {
            @Override public void run() {
                int exit = -1;
                try {
                    boolean finished = process.waitFor(config.getLocalTimeoutSeconds(), TimeUnit.SECONDS);
                    if (!finished || listenerFailure.get() != null) {
                        terminate(process);
                    } else {
                        exit = process.exitValue();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancelled.set(true);
                    terminate(process);
                } finally {
                    join(out, config.getStreamDrainTimeoutMillis());
                    join(err, config.getStreamDrainTimeoutMillis());
                }

                Throwable callbackError = listenerFailure.get();
                if (callbackError != null) {
                    completion.completeExceptionally(callbackError);
                    safeError(listener, callbackError);
                    return;
                }

                String outText = stdout.utf8().trim();
                String errText = stderr.utf8().trim();
                boolean timeout = exit == -1 && !cancelled.get();
                ComfyCliResult result = new ComfyCliResult(exit, outText,
                        timeout ? "comfy CLI stream timed out" + (errText.isEmpty() ? "" : "\n" + errText) : errText,
                        stdout.truncated(), stderr.truncated());
                completion.complete(result);
                try { listener.onComplete(result); } catch (RuntimeException ignored) { }
            }
        });

        out.start();
        err.start();
        waiter.start();

        return new ComfyCliStreamSession(process, completion, new Runnable() {
            @Override public void run() {
                cancelled.set(true);
                terminate(process);
                try { process.getInputStream().close(); } catch (IOException ignored) { }
                try { process.getErrorStream().close(); } catch (IOException ignored) { }
            }
        });
    }

    private void readStdout(Process process, ComfyCliStreamListener listener, CappedBytes retained,
                            AtomicReference<Throwable> listenerFailure) {
        readLines(process.getInputStream(), effectiveLineLimit(config.getMaxStdoutBytes()), new LineConsumer() {
            @Override public void accept(String line) {
                retained.append((line + "\n").getBytes(StandardCharsets.UTF_8));
                if (line.trim().isEmpty()) return;
                try {
                    JsonNode node = mapper.readTree(line);
                    ComfyCliEvent event = new ComfyCliEvent(
                            textOrNull(node, "schema"), textOrNull(node, "type"), node);
                    listener.onEvent(event);
                } catch (Throwable e) {
                    if (e instanceof RuntimeException) {
                        listenerFailure.compareAndSet(null, e);
                        terminate(process);
                    }
                    // Non-JSON lines are retained as raw stdout for compatibility.
                }
            }
        });
    }

    private void readStderr(Process process, ComfyCliStreamListener listener, CappedBytes retained,
                            AtomicReference<Throwable> listenerFailure) {
        readLines(process.getErrorStream(), effectiveLineLimit(config.getMaxStderrBytes()), new LineConsumer() {
            @Override public void accept(String line) {
                retained.append((line + "\n").getBytes(StandardCharsets.UTF_8));
                try {
                    listener.onStderr(line);
                } catch (RuntimeException e) {
                    listenerFailure.compareAndSet(null, e);
                    terminate(process);
                }
            }
        });
    }

    private static void readLines(InputStream input, int maxLineBytes, LineConsumer consumer) {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        try {
            int b;
            while ((b = input.read()) != -1) {
                if (b == '\n') {
                    consumer.accept(new String(line.toByteArray(), StandardCharsets.UTF_8));
                    line.reset();
                } else if (b != '\r') {
                    if (line.size() < maxLineBytes) line.write(b);
                }
            }
            if (line.size() > 0) consumer.accept(new String(line.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        } finally {
            try { input.close(); } catch (IOException ignored) { }
        }
    }

    private static int effectiveLineLimit(int configured) {
        return configured > 0 ? configured : 16 * 1024 * 1024;
    }

    private void terminate(Process process) {
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(config.getProcessShutdownGraceMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(config.getProcessShutdownGraceMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String textOrNull(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void join(Thread thread, long millis) {
        if (thread == null || thread == Thread.currentThread() || millis <= 0) return;
        try { thread.join(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void safeError(ComfyCliStreamListener listener, Throwable error) {
        try { listener.onError(error); } catch (RuntimeException ignored) { }
    }

    private interface LineConsumer { void accept(String line); }

    private static final class CappedBytes {
        private final int max;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private long total;

        private CappedBytes(int max) { this.max = max; }

        private synchronized void append(byte[] input) {
            total += input.length;
            if (max == 0) {
                bytes.write(input, 0, input.length);
                return;
            }
            int remaining = max - bytes.size();
            if (remaining > 0) bytes.write(input, 0, Math.min(remaining, input.length));
        }

        private synchronized boolean truncated() { return max > 0 && total > bytes.size(); }
        private synchronized String utf8() { return new String(bytes.toByteArray(), StandardCharsets.UTF_8); }
    }
}
