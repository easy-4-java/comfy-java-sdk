/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.mcp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.easy4j.comfy.ComfyException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Local Comfy MCP client using newline-delimited JSON-RPC over stdio.
 *
 * <p>One client owns exactly one child process. Requests may be concurrent and
 * are correlated by JSON-RPC id. Both stdout and stderr are continuously
 * drained; pending requests are removed on response, timeout, write failure,
 * process exit and close.</p>
 */
public class ComfyMcpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ComfyMcpClient.class);

    private final ComfyMcpConfig config;
    private final ObjectMapper mapper =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final Map<Long, CompletableFuture<JsonNode>> pendingRpcs =
            new ConcurrentHashMap<Long, CompletableFuture<JsonNode>>();
    private final AtomicLong rpcIds = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "comfy-mcp-timer");
        thread.setDaemon(true);
        return thread;
    });
    private final Object writeLock = new Object();

    private volatile Process process;
    private volatile PrintWriter stdin;
    private volatile Thread stdoutThread;
    private volatile Thread stderrThread;
    private volatile String serverName;
    private volatile String serverVersion;

    public ComfyMcpClient(ComfyMcpConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.config.validate();
    }

    /**
     * Starts {@code comfy-mcp} and performs MCP initialize/initialized.
     *
     * @throws IllegalStateException if already connected or closed.
     */
    public synchronized String connect() {
        if (closed.get()) {
            throw new IllegalStateException("comfy mcp client is closed");
        }
        if (initialized.get() || process != null) {
            throw new IllegalStateException("comfy mcp client is already connected");
        }

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

        try {
            Process child = builder.start();
            process = child;
            stdin = new PrintWriter(new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8), true);
            stdoutThread = daemon("comfy-mcp-reader", () -> readLoop(child));
            stderrThread = daemon("comfy-mcp-stderr", () -> drainStderr(child));
            stdoutThread.start();
            stderrThread.start();

            Map<String, Object> clientInfo = new LinkedHashMap<String, Object>();
            clientInfo.put("name", config.getClientName());
            clientInfo.put("version", config.getClientVersion());
            Map<String, Object> params = new LinkedHashMap<String, Object>();
            params.put("protocolVersion", config.getProtocolVersion());
            params.put("capabilities", new LinkedHashMap<String, Object>());
            params.put("clientInfo", clientInfo);

            JsonNode result = await(
                    request("initialize", params, config.getConnectTimeoutMillis(), "initialize"),
                    "initialize");
            notify("notifications/initialized", new LinkedHashMap<String, Object>());
            if (result.hasNonNull("serverInfo")) {
                serverName = result.path("serverInfo").path("name").asText(null);
                serverVersion = result.path("serverInfo").path("version").asText(null);
            }
            initialized.set(true);
            return serverVersion;
        } catch (IOException e) {
            cleanupTransport();
            throw new ComfyException("Failed to spawn comfy-mcp: " + config.getLocalExecutable(), e);
        } catch (RuntimeException e) {
            failAllPending(new ComfyException("comfy mcp connect failed", e));
            cleanupTransport();
            throw e;
        }
    }

    public List<ComfyMcpTool> listTools() {
        ensureConnected();
        JsonNode result = await(
                request("tools/list", new LinkedHashMap<String, Object>(),
                        config.getConnectTimeoutMillis(), "tools/list"),
                "tools/list");
        List<ComfyMcpTool> tools = new ArrayList<ComfyMcpTool>();
        for (JsonNode tool : result.path("tools")) {
            tools.add(new ComfyMcpTool(
                    tool.path("name").asText(""),
                    tool.hasNonNull("description") ? tool.path("description").asText() : null,
                    tool.path("inputSchema")));
        }
        return tools;
    }

    public CompletableFuture<ComfyMcpCallResult> callToolAsync(
            String name, Map<String, Object> arguments) {
        Objects.requireNonNull(name, "name");
        ensureConnected();
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("name", name);
        params.put("arguments", arguments == null
                ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(arguments));
        return request("tools/call", params, config.getReadTimeoutMillis(), "tools/call " + name)
                .thenApply(this::toCallResult);
    }

    public ComfyMcpCallResult callTool(String name, Map<String, Object> arguments) {
        return awaitCall(callToolAsync(name, arguments), "tools/call " + name);
    }

    // ---- current comfy-mcp typed conveniences ------------------------------

    public JsonNode serverInfo() { return callTool("server_info", null).getRaw(); }
    public ComfyMcpCallResult authStatus() { return callTool("auth_status", null); }
    public ComfyMcpCallResult billingStatus() { return callTool("billing_status", null); }
    public ComfyMcpCallResult authLogin() { return callTool("auth_login", null); }

    public ComfyMcpCallResult runWorkflow(String workflowPath, boolean wait,
            double timeoutSeconds, boolean confirmSpend) {
        return callTool("run_workflow", params(
                "workflow_path", Objects.requireNonNull(workflowPath, "workflowPath"),
                "wait", Boolean.valueOf(wait),
                "timeout_seconds", Double.valueOf(timeoutSeconds),
                "confirm_spend", Boolean.valueOf(confirmSpend)));
    }

    public ComfyMcpCallResult generateImage(String prompt, String checkpoint,
            boolean wait, double timeoutSeconds) {
        Map<String, Object> args = params(
                "prompt", Objects.requireNonNull(prompt, "prompt"),
                "wait", Boolean.valueOf(wait),
                "timeout_seconds", Double.valueOf(timeoutSeconds));
        putIfNotNull(args, "checkpoint", checkpoint);
        return callTool("generate_image", args);
    }

    public ComfyMcpCallResult listPartnerModels(String style, String partner,
            String query, int limit, int offset) {
        return callTool("list_partner_models", params(
                "style", nullToEmpty(style),
                "partner", nullToEmpty(partner),
                "query", nullToEmpty(query),
                "limit", Integer.valueOf(limit),
                "offset", Integer.valueOf(offset)));
    }

    public ComfyMcpCallResult partnerModelSchema(String model) {
        return callTool("partner_model_schema",
                params("model", Objects.requireNonNull(model, "model")));
    }

    public ComfyMcpCallResult partnerGenerate(String model, Map<String, Object> modelParams,
            boolean confirmSpend, String outPath, double timeoutSeconds) {
        Map<String, Object> args = params(
                "model", Objects.requireNonNull(model, "model"),
                "params", modelParams == null ? new LinkedHashMap<String, Object>() : modelParams,
                "confirm_spend", Boolean.valueOf(confirmSpend),
                "timeout_seconds", Double.valueOf(timeoutSeconds));
        putIfNotNull(args, "out_path", outPath);
        return callTool("partner_generate", args);
    }

    public ComfyMcpCallResult emitPartnerWorkflow(String model, String outPath,
            Map<String, Object> modelParams) {
        return callTool("emit_partner_workflow", params(
                "model", Objects.requireNonNull(model, "model"),
                "out_path", Objects.requireNonNull(outPath, "outPath"),
                "params", modelParams == null ? new LinkedHashMap<String, Object>() : modelParams));
    }

    public ComfyMcpCallResult runTemplate(String name, Map<String, Object> templateParams,
            boolean confirmSpend, boolean wait, double timeoutSeconds) {
        return callTool("run_template", params(
                "name", Objects.requireNonNull(name, "name"),
                "params", templateParams == null ? new LinkedHashMap<String, Object>() : templateParams,
                "confirm_spend", Boolean.valueOf(confirmSpend),
                "wait", Boolean.valueOf(wait),
                "timeout_seconds", Double.valueOf(timeoutSeconds)));
    }

    public ComfyMcpCallResult job(String action, String promptId, Double timeoutSeconds) {
        Map<String, Object> args = params("action", action == null ? "status" : action);
        putIfNotNull(args, "prompt_id", promptId);
        putIfNotNull(args, "timeout_seconds", timeoutSeconds);
        return callTool("job", args);
    }

    public ComfyMcpCallResult jobStatus(String promptId) { return job("status", promptId, null); }
    public ComfyMcpCallResult waitForJob(String promptId, double timeoutSeconds) {
        return job("wait", promptId, Double.valueOf(timeoutSeconds));
    }
    public ComfyMcpCallResult watchJob(String promptId, double timeoutSeconds) {
        return job("watch", promptId, Double.valueOf(timeoutSeconds));
    }
    public ComfyMcpCallResult cancelJob(String promptId) { return job("cancel", promptId, null); }
    public ComfyMcpCallResult getQueue() { return job("queue", null, null); }

    public ComfyMcpCallResult systemStats() { return callTool("system_stats", null); }
    public ComfyMcpCallResult freeMemory() { return freeMemory(true, null); }
    public ComfyMcpCallResult freeMemory(boolean unloadModels, Boolean freeMemory) {
        Map<String, Object> args = params("unload_models", Boolean.valueOf(unloadModels));
        putIfNotNull(args, "free_memory", freeMemory);
        return callTool("free_memory", args);
    }

    public ComfyMcpCallResult fetchOutputs(String promptId, String outDir,
            boolean urlOnly, boolean inlineImages) {
        return callTool("fetch_outputs", params(
                "prompt_id", Objects.requireNonNull(promptId, "promptId"),
                "out_dir", Objects.requireNonNull(outDir, "outDir"),
                "url_only", Boolean.valueOf(urlOnly),
                "inline_images", Boolean.valueOf(inlineImages)));
    }

    public ComfyMcpCallResult launchComfyUi(List<String> extraArgs, boolean confirmNetworkExposure) {
        return callTool("launch_comfyui", params(
                "extra_args", extraArgs == null ? Collections.emptyList() : extraArgs,
                "confirm_network_exposure", Boolean.valueOf(confirmNetworkExposure)));
    }

    public ComfyMcpCallResult stopComfyUi() { return callTool("stop_comfyui", null); }

    public ComfyMcpCallResult restartComfyUi(List<String> extraArgs,
            boolean confirmNetworkExposure, boolean confirmKillUntracked) {
        return callTool("restart_comfyui", params(
                "extra_args", extraArgs == null ? Collections.emptyList() : extraArgs,
                "confirm_network_exposure", Boolean.valueOf(confirmNetworkExposure),
                "confirm_kill_untracked", Boolean.valueOf(confirmKillUntracked)));
    }

    public ComfyMcpCallResult updateComfyUi(String target, boolean confirmUpdateAll) {
        return callTool("update_comfyui", params(
                "target", target == null ? "comfy" : target,
                "confirm_update_all", Boolean.valueOf(confirmUpdateAll)));
    }

    public ComfyMcpCallResult switchComfyUiVersion(String version, boolean confirmSwitch) {
        return callTool("switch_comfyui_version", params(
                "version", Objects.requireNonNull(version, "version"),
                "confirm_switch", Boolean.valueOf(confirmSwitch)));
    }

    public ComfyMcpCallResult installNode(List<String> names, boolean confirmInstall) {
        return callTool("install_node", params(
                "names", Objects.requireNonNull(names, "names"),
                "confirm_install", Boolean.valueOf(confirmInstall)));
    }

    public ComfyMcpCallResult getLogs(int tail, Integer port) {
        Map<String, Object> args = params("tail", Integer.valueOf(tail));
        putIfNotNull(args, "port", port);
        return callTool("get_logs", args);
    }

    public ComfyMcpCallResult discover() { return discover(true, ""); }
    public ComfyMcpCallResult discover(boolean schemasOnly, String command) {
        return callTool("discover", params(
                "schemas_only", Boolean.valueOf(schemasOnly),
                "command", nullToEmpty(command)));
    }
    public ComfyMcpCallResult which() { return callTool("which", null); }

    public ComfyMcpCallResult project(String action) {
        return callTool("project", params("action", action == null ? "status" : action));
    }

    public ComfyMcpCallResult searchTemplates(String query, int limit, int offset,
            String tag, String type, String model, String provider, boolean excludeApi) {
        return callTool("search_templates", params(
                "query", nullToEmpty(query),
                "limit", Integer.valueOf(limit),
                "offset", Integer.valueOf(offset),
                "tag", nullToEmpty(tag),
                "type", nullToEmpty(type),
                "model", nullToEmpty(model),
                "provider", nullToEmpty(provider),
                "exclude_api", Boolean.valueOf(excludeApi)));
    }

    public ComfyMcpCallResult getTemplate(String name) {
        return getTemplate(name, true);
    }

    public ComfyMcpCallResult getTemplate(String name, boolean checkLocal) {
        return callTool("get_template", params(
                "name", Objects.requireNonNull(name, "name"),
                "check_local", Boolean.valueOf(checkLocal)));
    }

    public ComfyMcpCallResult fetchTemplate(String name, String outPath, boolean checkLocal) {
        return callTool("fetch_template", params(
                "name", Objects.requireNonNull(name, "name"),
                "out_path", Objects.requireNonNull(outPath, "outPath"),
                "check_local", Boolean.valueOf(checkLocal)));
    }

    public ComfyMcpCallResult nodes(String action, Map<String, Object> options) {
        Map<String, Object> args = options == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(options);
        args.put("action", action == null ? "search" : action);
        return callTool("nodes", args);
    }

    public ComfyMcpCallResult nodeDependencies(String pack, String registryId) {
        return callTool("node_dependencies", params(
                "pack", nullToEmpty(pack),
                "registry_id", nullToEmpty(registryId)));
    }

    public ComfyMcpCallResult workflowDeps(String workflowPath) {
        return callTool("workflow_deps",
                params("workflow_path", Objects.requireNonNull(workflowPath, "workflowPath")));
    }

    public ComfyMcpCallResult searchModels(String query, String folder) {
        return callTool("search_models", params(
                "query", nullToEmpty(query),
                "folder", nullToEmpty(folder)));
    }

    public ComfyMcpCallResult downloadModel(String url, String relativePath,
            String filename, boolean wait, double timeoutSeconds) {
        Map<String, Object> args = params(
                "url", Objects.requireNonNull(url, "url"),
                "wait", Boolean.valueOf(wait),
                "timeout_seconds", Double.valueOf(timeoutSeconds));
        putIfNotNull(args, "relative_path", relativePath);
        putIfNotNull(args, "filename", filename);
        return callTool("download_model", args);
    }

    public ComfyMcpCallResult download(String action, String downloadId, Double timeoutSeconds) {
        Map<String, Object> args = params("action", action == null ? "status" : action);
        putIfNotNull(args, "download_id", downloadId);
        putIfNotNull(args, "timeout_seconds", timeoutSeconds);
        return callTool("download", args);
    }

    public ComfyMcpCallResult uploadFile(List<String> paths, boolean overwrite) {
        return callTool("upload_file", params(
                "paths", Objects.requireNonNull(paths, "paths"),
                "overwrite", Boolean.valueOf(overwrite)));
    }

    public ComfyMcpCallResult validateWorkflow(String workflowPath) {
        return callTool("validate_workflow",
                params("workflow_path", Objects.requireNonNull(workflowPath, "workflowPath")));
    }

    public ComfyMcpCallResult listWorkflowSlots(String workflowPath) {
        return callTool("list_workflow_slots",
                params("workflow_path", Objects.requireNonNull(workflowPath, "workflowPath")));
    }

    public ComfyMcpCallResult listWorkflowNotes(String workflowPath) {
        return callTool("list_workflow_notes",
                params("workflow_path", Objects.requireNonNull(workflowPath, "workflowPath")));
    }

    public ComfyMcpCallResult setWorkflowSlot(String workflowPath, List<?> overrides, boolean stdout) {
        return callTool("set_workflow_slot", params(
                "workflow_path", Objects.requireNonNull(workflowPath, "workflowPath"),
                "overrides", Objects.requireNonNull(overrides, "overrides"),
                "stdout", Boolean.valueOf(stdout)));
    }

    public ComfyMcpCallResult varyWorkflow(String workflowPath, List<?> slots, String outDir) {
        Map<String, Object> args = params(
                "workflow_path", Objects.requireNonNull(workflowPath, "workflowPath"),
                "slots", Objects.requireNonNull(slots, "slots"));
        putIfNotNull(args, "out_dir", outDir);
        return callTool("vary_workflow", args);
    }

    // ---- state/lifecycle ---------------------------------------------------

    public String getServerName() { return serverName; }
    public String getServerVersion() { return serverVersion; }
    public boolean isClosed() { return closed.get(); }
    public boolean isConnected() { return initialized.get() && !closed.get(); }
    int pendingRequestCount() { return pendingRpcs.size(); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        initialized.set(false);
        failAllPending(new ComfyException("comfy mcp client closed"));
        cleanupTransport();
        timer.shutdownNow();
    }

    // ---- transport internals ----------------------------------------------

    private CompletableFuture<JsonNode> request(String method, Map<String, Object> params,
            long timeoutMillis, String what) {
        ensureTransportOpen();
        final long id = rpcIds.incrementAndGet();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", Long.valueOf(id));
        payload.put("method", method);
        payload.put("params", params);

        final CompletableFuture<JsonNode> future = new CompletableFuture<JsonNode>();
        pendingRpcs.put(Long.valueOf(id), future);

        try {
            writeJson(payload);
        } catch (RuntimeException e) {
            pendingRpcs.remove(Long.valueOf(id), future);
            future.completeExceptionally(e);
            throw e;
        }

        final ScheduledFuture<?> guard;
        try {
            guard = timeoutMillis <= 0 ? null : timer.schedule(() -> {
                if (pendingRpcs.remove(Long.valueOf(id), future)) {
                    future.completeExceptionally(new ComfyException(
                            "comfy mcp " + what + " timed out after " + timeoutMillis + " ms"));
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            pendingRpcs.remove(Long.valueOf(id), future);
            future.completeExceptionally(new ComfyException("comfy mcp timer is closed", e));
            return future;
        }

        future.whenComplete((value, error) -> {
            pendingRpcs.remove(Long.valueOf(id), future);
            if (guard != null) {
                guard.cancel(false);
            }
        });
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
        ensureTransportOpen();
        String line;
        try {
            line = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ComfyException("comfy mcp RPC serialization failed", e);
        }

        synchronized (writeLock) {
            PrintWriter writer = stdin;
            if (writer == null) {
                throw new ComfyException("comfy mcp client is not connected");
            }
            writer.println(line);
            if (writer.checkError()) {
                throw new ComfyException("comfy mcp stdin write failed (server exited?)");
            }
        }
    }

    private void readLoop(Process child) {
        try (Reader reader = new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8)) {
            String frame;
            while ((frame = readBoundedLine(reader)) != null) {
                if (closed.get()) {
                    return;
                }
                if (frame.trim().isEmpty()) {
                    continue;
                }
                handleFrame(frame);
            }
            if (!closed.get() && process == child) {
                failAllPending(new ComfyException("comfy mcp stdout closed (server exited)"));
            }
        } catch (FrameTooLargeException e) {
            failAllPending(new ComfyException(
                    "comfy mcp frame exceeded maxFrameChars=" + config.getMaxFrameChars(), e));
            child.destroy();
        } catch (IOException e) {
            if (!closed.get() && process == child) {
                failAllPending(new ComfyException("comfy mcp stdout read failed", e));
            }
        }
    }

    /** Drains stderr without logging content, because child logs may contain secrets/paths. */
    private void drainStderr(Process child) {
        long chars = 0L;
        try (Reader reader = new InputStreamReader(child.getErrorStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int n;
            while ((n = reader.read(buffer)) >= 0) {
                chars += n;
            }
        } catch (IOException e) {
            if (!closed.get() && process == child) {
                log.debug("comfy-mcp stderr drain ended with {}", e.getClass().getSimpleName());
            }
        }
        log.debug("comfy-mcp stderr drained chars={}", chars);
    }

    private String readBoundedLine(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int max = config.getMaxFrameChars() <= 0 ? Integer.MAX_VALUE : config.getMaxFrameChars();
        for (;;) {
            int ch = reader.read();
            if (ch < 0) {
                return line.length() == 0 ? null : line.toString();
            }
            if (ch == '\n') {
                return line.toString();
            }
            if (ch == '\r') {
                continue;
            }
            if (line.length() >= max) {
                throw new FrameTooLargeException();
            }
            line.append((char) ch);
        }
    }

    private void handleFrame(String frame) {
        JsonNode node;
        try {
            node = mapper.readTree(frame);
        } catch (Exception e) {
            log.warn("Ignored non-JSON frame from comfy-mcp");
            return;
        }

        if (node.hasNonNull("id")) {
            Long id = Long.valueOf(node.get("id").asLong());
            CompletableFuture<JsonNode> pending = pendingRpcs.remove(id);
            if (pending == null) {
                return;
            }
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

        for (JsonNode content : result.path("content")) {
            String type = content.path("type").asText("");
            String piece = content.hasNonNull("text") ? content.path("text").asText() : null;
            contents.add(new ComfyMcpContent(
                    type,
                    piece,
                    content.hasNonNull("mimeType") ? content.path("mimeType").asText() : null,
                    content.hasNonNull("data") ? content.path("data").asText() : null,
                    content.hasNonNull("uri") ? content.path("uri").asText() : null,
                    content));

            if (piece == null || !"text".equals(type)) {
                continue;
            }
            int remaining = cap - text.length();
            if (remaining <= 0) {
                truncated = true;
                continue;
            }
            if (piece.length() > remaining) {
                text.append(piece, 0, remaining);
                truncated = true;
            } else {
                text.append(piece);
            }
        }

        return new ComfyMcpCallResult(
                text.toString(),
                result.path("isError").asBoolean(false),
                result,
                contents,
                truncated);
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

    private ComfyMcpCallResult awaitCall(CompletableFuture<ComfyMcpCallResult> future, String what) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ComfyException("comfy mcp " + what + " interrupted", e);
        } catch (ExecutionException e) {
            throw propagate("comfy mcp " + what + " failed", e.getCause());
        }
    }

    private RuntimeException propagate(String message, Throwable cause) {
        Throwable actual = cause == null ? new ComfyException(message) : cause;
        if (actual instanceof ComfyException) {
            return (ComfyException) actual;
        }
        if (actual instanceof RuntimeException) {
            return (RuntimeException) actual;
        }
        return new ComfyException(message, actual);
    }

    private void failAllPending(ComfyException error) {
        for (Map.Entry<Long, CompletableFuture<JsonNode>> entry : pendingRpcs.entrySet()) {
            if (pendingRpcs.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().completeExceptionally(error);
            }
        }
    }

    private synchronized void cleanupTransport() {
        initialized.set(false);

        PrintWriter writer = stdin;
        stdin = null;
        if (writer != null) {
            writer.close();
        }

        Process child = process;
        process = null;
        if (child != null) {
            child.destroy();
            try {
                if (config.getShutdownTimeoutMillis() > 0
                        && !child.waitFor(config.getShutdownTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                    child.destroyForcibly();
                    child.waitFor(config.getShutdownTimeoutMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                child.destroyForcibly();
            }
        }

        Thread out = stdoutThread;
        Thread err = stderrThread;
        stdoutThread = null;
        stderrThread = null;
        interruptAndJoin(out);
        interruptAndJoin(err);
    }

    private void interruptAndJoin(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(Math.max(100L, config.getShutdownTimeoutMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureConnected() {
        if (closed.get()) {
            throw new IllegalStateException("comfy mcp client is closed");
        }
        if (!initialized.get()) {
            throw new IllegalStateException("comfy mcp client is not connected");
        }
        ensureTransportOpen();
    }

    private void ensureTransportOpen() {
        if (closed.get()) {
            throw new ComfyException("comfy mcp client is closed");
        }
        Process child = process;
        if (child == null || stdin == null) {
            throw new ComfyException("comfy mcp client is not connected");
        }
    }

    private static Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class FrameTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
