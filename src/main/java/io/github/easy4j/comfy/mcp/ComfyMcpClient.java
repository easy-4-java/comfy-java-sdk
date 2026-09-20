/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.github.easy4j.comfy.ComfyException;

/**
 * Production-hardened local MCP stdio client for first-party comfy-mcp.
 *
 * <p>One instance owns at most one child process. stdout is the MCP protocol
 * channel, stderr is drained continuously into a bounded diagnostic tail, and
 * every request is removed from the pending map on response, timeout, write
 * failure or close.</p>
 */
public class ComfyMcpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ComfyMcpClient.class);

    private enum State { NEW, CONNECTING, CONNECTED, CLOSED }

    private final ComfyMcpConfig config;
    private final ObjectMapper mapper =
            JsonMapper.builder().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
    private final Map<Long, CompletableFuture<JsonNode>> pendingRpcs =
            new ConcurrentHashMap<Long, CompletableFuture<JsonNode>>();
    private final AtomicLong rpcIds = new AtomicLong();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "comfy-mcp-timer");
        thread.setDaemon(true);
        return thread;
    });
    private final Object writeLock = new Object();
    private final StderrTail stderrTail;

    private volatile State state = State.NEW;
    private volatile Process process;
    private volatile PrintWriter stdin;
    private volatile Thread stdoutReader;
    private volatile Thread stderrReader;
    private volatile String serverName;
    private volatile String serverVersion;

    public ComfyMcpClient(ComfyMcpConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.config.validate();
        this.stderrTail = new StderrTail(config.getMaxStderrChars());
    }

    /**
     * Starts comfy-mcp and completes the MCP initialize handshake.
     * Repeated calls after a successful connect are idempotent.
     */
    public synchronized String connect() {
        if (state == State.CLOSED) {
            throw new IllegalStateException("comfy mcp client is closed");
        }
        if (state == State.CONNECTED) {
            return serverVersion;
        }
        if (state == State.CONNECTING) {
            throw new IllegalStateException("comfy mcp client is already connecting");
        }

        state = State.CONNECTING;
        List<String> command = new ArrayList<String>();
        command.add(config.getLocalExecutable());
        if (config.getMcpArgs() != null) {
            Collections.addAll(command, config.getMcpArgs());
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        if (config.getEnvironment() != null && !config.getEnvironment().isEmpty()) {
            builder.environment().putAll(config.getEnvironment());
        }

        Process child = null;
        try {
            child = builder.start();
            final Process connectedChild = child;
            process = connectedChild;
            stdin = new PrintWriter(new OutputStreamWriter(connectedChild.getOutputStream(), StandardCharsets.UTF_8), true);

            stdoutReader = daemon("comfy-mcp-reader", () -> readLoop(connectedChild));
            stderrReader = daemon("comfy-mcp-stderr", () -> drainStderr(connectedChild));
            stdoutReader.start();
            stderrReader.start();

            Map<String, Object> clientInfo = new LinkedHashMap<String, Object>();
            clientInfo.put("name", config.getClientName());
            clientInfo.put("version", config.getClientVersion());
            Map<String, Object> params = new LinkedHashMap<String, Object>();
            params.put("protocolVersion", config.getProtocolVersion());
            params.put("capabilities", new LinkedHashMap<String, Object>());
            params.put("clientInfo", clientInfo);

            JsonNode result = await(request("initialize", params,
                    config.getConnectTimeoutMillis(), "initialize"), "initialize");
            notify("notifications/initialized", new LinkedHashMap<String, Object>());

            if (result.hasNonNull("serverInfo")) {
                serverName = textOrNull(result.path("serverInfo"), "name");
                serverVersion = textOrNull(result.path("serverInfo"), "version");
            }
            state = State.CONNECTED;
            return serverVersion;
        } catch (RuntimeException | IOException e) {
            cleanupProcess(child);
            process = null;
            stdin = null;
            if (state != State.CLOSED) state = State.NEW;
            if (e instanceof ComfyException) throw (ComfyException) e;
            throw new ComfyException("Failed to spawn/connect comfy-mcp: " + config.getLocalExecutable(), e);
        }
    }

    public List<ComfyMcpTool> listTools() {
        if (state == State.CLOSED) {
            throw new ComfyException("comfy mcp client is closed");
        }
        ensureConnected();
        JsonNode result = await(request("tools/list", new LinkedHashMap<String, Object>(),
                config.getReadTimeoutMillis(), "tools/list"), "tools/list");
        List<ComfyMcpTool> tools = new ArrayList<ComfyMcpTool>();
        for (JsonNode tool : result.path("tools")) {
            tools.add(new ComfyMcpTool(
                    tool.path("name").asText(""),
                    tool.hasNonNull("description") ? tool.path("description").asText() : null,
                    tool.path("inputSchema")));
        }
        return tools;
    }

    public CompletableFuture<ComfyMcpCallResult> callToolAsync(String name, Map<String, Object> arguments) {
        requireToolName(name);
        ensureConnected();
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("name", name);
        params.put("arguments", arguments == null
                ? new LinkedHashMap<String, Object>() : arguments);
        return request("tools/call", params, config.getReadTimeoutMillis(), "tools/call " + name)
                .thenApply(this::toCallResult);
    }

    public ComfyMcpCallResult callTool(String name, Map<String, Object> arguments) {
        try {
            return callToolAsync(name, arguments).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ComfyException("comfy mcp tools/call interrupted", e);
        } catch (ExecutionException e) {
            throw propagate("comfy mcp tools/call failed", e.getCause());
        }
    }

    /** Backward-compatible raw server_info convenience method. */
    public JsonNode serverInfo() {
        return callTool("server_info", null).getRaw();
    }

    public ComfyMcpCallResult serverInfoResult() { return callTool("server_info", null); }

    public ComfyMcpCallResult runWorkflow(String workflowPath, boolean wait,
                                           double timeoutSeconds, boolean confirmSpend) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("workflow_path", requireValue("workflowPath", workflowPath));
        args.put("wait", Boolean.valueOf(wait));
        args.put("timeout_seconds", Double.valueOf(timeoutSeconds));
        args.put("confirm_spend", Boolean.valueOf(confirmSpend));
        return callTool("run_workflow", args);
    }

    public ComfyMcpCallResult job(String action, String promptId, Double timeoutSeconds) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("action", requireValue("action", action));
        if (promptId != null && !promptId.isEmpty()) args.put("prompt_id", promptId);
        if (timeoutSeconds != null) args.put("timeout_seconds", timeoutSeconds);
        return callTool("job", args);
    }

    public ComfyMcpCallResult fetchOutputs(String promptId, String outDir,
                                            boolean urlOnly, boolean inlineImages) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("prompt_id", requireValue("promptId", promptId));
        args.put("out_dir", requireValue("outDir", outDir));
        args.put("url_only", Boolean.valueOf(urlOnly));
        args.put("inline_images", Boolean.valueOf(inlineImages));
        return callTool("fetch_outputs", args);
    }

    public ComfyMcpCallResult launchComfyUi(List<String> extraArgs, boolean confirmNetworkExposure) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        if (extraArgs != null) args.put("extra_args", new ArrayList<String>(extraArgs));
        args.put("confirm_network_exposure", Boolean.valueOf(confirmNetworkExposure));
        return callTool("launch_comfyui", args);
    }

    public ComfyMcpCallResult stopComfyUi() { return callTool("stop_comfyui", null); }
    public ComfyMcpCallResult restartComfyUi(List<String> extraArgs, boolean confirmNetworkExposure) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        if (extraArgs != null) args.put("extra_args", new ArrayList<String>(extraArgs));
        args.put("confirm_network_exposure", Boolean.valueOf(confirmNetworkExposure));
        return callTool("restart_comfyui", args);
    }

    public ComfyMcpCallResult searchTemplates(String query, int limit, int offset,
                                               String tag, String type, String model,
                                               String provider, boolean excludeApi) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", valueOrEmpty(query));
        args.put("limit", Integer.valueOf(limit));
        args.put("offset", Integer.valueOf(offset));
        args.put("tag", valueOrEmpty(tag));
        args.put("type", valueOrEmpty(type));
        args.put("model", valueOrEmpty(model));
        args.put("provider", valueOrEmpty(provider));
        args.put("exclude_api", Boolean.valueOf(excludeApi));
        return callTool("search_templates", args);
    }

    public ComfyMcpCallResult getTemplate(String name, boolean checkLocal) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("name", requireValue("name", name));
        args.put("check_local", Boolean.valueOf(checkLocal));
        return callTool("get_template", args);
    }

    public ComfyMcpCallResult fetchTemplate(String name, String outPath, boolean checkLocal) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("name", requireValue("name", name));
        args.put("out_path", requireValue("outPath", outPath));
        args.put("check_local", Boolean.valueOf(checkLocal));
        return callTool("fetch_template", args);
    }

    public ComfyMcpCallResult nodesSearch(String query, boolean excludeApi) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("action", "search");
        args.put("query", requireValue("query", query));
        args.put("exclude_api", Boolean.valueOf(excludeApi));
        return callTool("nodes", args);
    }

    public ComfyMcpCallResult node(String name) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("action", "get");
        args.put("name", requireValue("name", name));
        return callTool("nodes", args);
    }

    public ComfyMcpCallResult searchModels(String query, String folder) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", valueOrEmpty(query));
        args.put("folder", valueOrEmpty(folder));
        return callTool("search_models", args);
    }

    public ComfyMcpCallResult validateWorkflow(String workflowPath) {
        return callTool("validate_workflow", singleton("workflow_path",
                requireValue("workflowPath", workflowPath)));
    }

    public ComfyMcpCallResult workflowSlots(String workflowPath) {
        return callTool("list_workflow_slots", singleton("workflow_path",
                requireValue("workflowPath", workflowPath)));
    }

    public ComfyMcpCallResult workflowNotes(String workflowPath) {
        return callTool("list_workflow_notes", singleton("workflow_path",
                requireValue("workflowPath", workflowPath)));
    }

    public ComfyMcpCallResult discover(boolean schemasOnly, String command) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("schemas_only", Boolean.valueOf(schemasOnly));
        args.put("command", valueOrEmpty(command));
        return callTool("discover", args);
    }

    public ComfyMcpCallResult uploadFiles(List<String> paths, boolean overwrite) {
        if (paths == null || paths.isEmpty()) throw new IllegalArgumentException("paths must not be empty");
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("paths", new ArrayList<String>(paths));
        args.put("overwrite", Boolean.valueOf(overwrite));
        return callTool("upload_file", args);
    }

    public String getServerName() { return serverName; }
    public String getServerVersion() { return serverVersion; }
    public boolean isClosed() { return state == State.CLOSED; }
    public boolean isConnected() { return state == State.CONNECTED; }
    public boolean isProcessAlive() {
        Process current = process;
        return current != null && current.isAlive();
    }
    public String getStderrTail() { return stderrTail.snapshot(); }

    int pendingRpcCount() { return pendingRpcs.size(); }

    @Override
    public synchronized void close() {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        timer.shutdownNow();
        failAllPending(new ComfyException("comfy mcp client closed"));

        PrintWriter writer = stdin;
        stdin = null;
        if (writer != null) {
            synchronized (writeLock) {
                writer.close();
            }
        }

        Process current = process;
        process = null;
        cleanupProcess(current);
        joinQuietly(stdoutReader, 250);
        joinQuietly(stderrReader, 250);
        stdoutReader = null;
        stderrReader = null;
    }

    private CompletableFuture<JsonNode> request(String method, Map<String, Object> params,
                                                 long timeoutMillis, String what) {
        final long id = rpcIds.incrementAndGet();
        final Long key = Long.valueOf(id);
        final CompletableFuture<JsonNode> future = new CompletableFuture<JsonNode>();
        pendingRpcs.put(key, future);

        final ScheduledFuture<?> guard;
        try {
            guard = timer.schedule(() -> {
                if (pendingRpcs.remove(key, future)) {
                    future.completeExceptionally(new ComfyException(
                            "comfy mcp " + what + " timed out after " + timeoutMillis + " ms"));
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            pendingRpcs.remove(key, future);
            future.completeExceptionally(e);
            return future;
        }

        future.whenComplete((result, error) -> {
            pendingRpcs.remove(key, future);
            guard.cancel(false);
        });

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", key);
        payload.put("method", method);
        payload.put("params", params);

        try {
            writeJson(payload);
        } catch (RuntimeException e) {
            if (pendingRpcs.remove(key, future)) {
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    private void notify(String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("params", params);
        writeJson(payload);
    }

    private void writeJson(Map<String, Object> payload) {
        if (state == State.CLOSED) throw new ComfyException("comfy mcp client is closed");
        final String line;
        try {
            line = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ComfyException("comfy mcp RPC serialization failed", e);
        }

        synchronized (writeLock) {
            PrintWriter writer = stdin;
            if (writer == null) throw new ComfyException("comfy mcp client is not connected");
            writer.println(line);
            if (writer.checkError()) {
                throw new ComfyException("comfy mcp stdin write failed (server exited?)");
            }
        }
    }

    private void readLoop(Process child) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            while (true) {
                String frame = readBoundedLine(reader);
                if (frame == null) break;
                if (frame.trim().isEmpty()) continue;
                handleFrame(frame);
            }
            if (state != State.CLOSED) {
                failAllPending(new ComfyException("comfy mcp stdout closed (server exited)"));
            }
        } catch (FrameTooLargeException e) {
            failAllPending(new ComfyException(e.getMessage()));
            child.destroy();
        } catch (IOException e) {
            if (state != State.CLOSED) {
                failAllPending(new ComfyException("comfy mcp stdout read failed", e));
            }
        }
    }

    private String readBoundedLine(BufferedReader reader) throws IOException {
        int cap = effectiveMaxFrameChars();
        StringBuilder line = new StringBuilder(Math.min(cap, 8192));
        int ch;
        boolean overflow = false;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') {
                if (overflow) {
                    throw new FrameTooLargeException(
                            "comfy mcp frame exceeded maxFrameChars=" + config.getMaxFrameChars());
                }
                return line.toString();
            }
            if (ch == '\r') continue;
            if (line.length() < cap) line.append((char) ch);
            else overflow = true;
        }
        if (overflow) {
            throw new FrameTooLargeException(
                    "comfy mcp frame exceeded maxFrameChars=" + config.getMaxFrameChars());
        }
        return line.length() == 0 ? null : line.toString();
    }

    private void drainStderr(Process child) {
        try (InputStreamReader reader = new InputStreamReader(child.getErrorStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                stderrTail.append(buffer, n);
            }
        } catch (IOException e) {
            if (state != State.CLOSED) log.debug("comfy-mcp stderr drain ended", e);
        }
    }

    private void handleFrame(String frame) {
        final JsonNode node;
        try {
            node = mapper.readTree(frame);
        } catch (Exception ex) {
            log.warn("Ignored non-JSON frame from comfy-mcp");
            return;
        }
        if (node.hasNonNull("id")) {
            Long key = Long.valueOf(node.get("id").asLong());
            CompletableFuture<JsonNode> pending = pendingRpcs.remove(key);
            if (pending == null) return;
            if (node.hasNonNull("error")) {
                pending.completeExceptionally(new ComfyException(
                        "comfy mcp RPC failed: " + node.get("error").toString()));
            } else {
                pending.complete(node.path("result"));
            }
            return;
        }
        log.debug("Ignored comfy-mcp notification: method={}", node.path("method").asText(""));
    }

    private ComfyMcpCallResult toCallResult(JsonNode result) {
        StringBuilder text = new StringBuilder();
        List<ComfyMcpContent> contents = new ArrayList<ComfyMcpContent>();
        int cap = config.getMaxContentChars() <= 0 ? Integer.MAX_VALUE : config.getMaxContentChars();
        boolean truncated = false;

        for (JsonNode item : result.path("content")) {
            String type = item.path("type").asText("");
            String itemText = textOrNull(item, "text");
            String mime = textOrNull(item, "mimeType");
            if (mime == null) mime = textOrNull(item, "mime_type");
            String uri = textOrNull(item, "uri");
            contents.add(new ComfyMcpContent(type, itemText, mime, uri, item));

            if ("text".equals(type) && itemText != null) {
                if (text.length() >= cap) {
                    truncated = true;
                    continue;
                }
                int room = cap - text.length();
                if (itemText.length() > room) {
                    text.append(itemText, 0, room);
                    truncated = true;
                } else {
                    text.append(itemText);
                }
            }
        }

        if (truncated) log.warn("comfy mcp text content truncated at maxContentChars={}", cap);
        return new ComfyMcpCallResult(text.toString(), result.path("isError").asBoolean(false),
                result, contents);
    }

    private JsonNode await(CompletableFuture<JsonNode> future, String what) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ComfyException("comfy mcp " + what + " interrupted", e);
        } catch (ExecutionException e) {
            throw propagate("comfy mcp " + what + " failed", e.getCause());
        }
    }

    private void ensureConnected() {
        if (state == State.CLOSED) throw new IllegalStateException("comfy mcp client is closed");
        if (state != State.CONNECTED) throw new IllegalStateException("comfy mcp client is not connected");
    }

    private void failAllPending(ComfyException error) {
        for (Map.Entry<Long, CompletableFuture<JsonNode>> entry : pendingRpcs.entrySet()) {
            CompletableFuture<JsonNode> future = entry.getValue();
            if (pendingRpcs.remove(entry.getKey(), future)) {
                future.completeExceptionally(error);
            }
        }
    }

    private void cleanupProcess(Process child) {
        if (child == null) return;
        if (child.isAlive()) child.destroy();
        try {
            if (child.isAlive() && !child.waitFor(config.getProcessShutdownGraceMillis(), TimeUnit.MILLISECONDS)) {
                child.destroyForcibly();
                child.waitFor(config.getProcessShutdownGraceMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (child.isAlive()) child.destroyForcibly();
        }
    }

    private int effectiveMaxFrameChars() {
        return config.getMaxFrameChars() <= 0 ? Integer.MAX_VALUE : config.getMaxFrameChars();
    }


    private static Thread daemon(String name, Runnable runnable) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void joinQuietly(Thread thread, long millis) {
        if (thread == null || thread == Thread.currentThread()) return;
        try {
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ComfyException propagate(String message, Throwable cause) {
        Throwable actual = cause == null ? new IllegalStateException(message) : cause;
        if (actual instanceof ComfyException) return (ComfyException) actual;
        return new ComfyException(message, actual);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String requireToolName(String name) {
        return requireValue("tool name", name);
    }

    private static String requireValue(String field, String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException(field + " must not contain NUL");
        return value;
    }

    private static String valueOrEmpty(String value) { return value == null ? "" : value; }

    private static Map<String, Object> singleton(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    private static final class FrameTooLargeException extends IOException {
        private FrameTooLargeException(String message) { super(message); }
    }

    private static final class StderrTail {
        private final int cap;
        private final StringBuilder value = new StringBuilder();

        private StderrTail(int cap) { this.cap = cap; }

        private synchronized void append(char[] chars, int len) {
            if (cap == 0 || len <= 0) return;
            value.append(chars, 0, len);
            if (value.length() > cap) value.delete(0, value.length() - cap);
        }

        private synchronized String snapshot() { return value.toString(); }
    }
}
