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
package io.github.easy4j.comfy.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.easy4j.comfy.ComfyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client for the comfy-mcp route: spawns the MCP server as a child process
 * and drives it over JSON-RPC on stdio (newline-delimited JSON, the MCP
 * stdio transport).
 *
 * <p>Lifecycle: {@link #connect()} performs the MCP {@code initialize}
 * handshake and completes the {@code notifications/initialized} sequence;
 * {@link #listTools()} discovers the tool catalog ({@code server_info},
 * {@code run_workflow}, {@code search_templates}, {@code launch_comfyui},
 * &hellip;); {@link #callTool(String, Map)} executes one tool and returns the
 * concatenated text content. {@link #close()} terminates the server.</p>
 *
 * <p>The client is thread-safe: concurrent {@code tools/call} requests are
 * correlated by JSON-RPC id. Each client owns exactly one server child
 * process; {@link #close()} destroys it.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyMcpConfig
 * @see ComfyMcpTool
 * @see ComfyMcpCallResult
 */
public class ComfyMcpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ComfyMcpClient.class);

    private final ComfyMcpConfig config;
    private final ObjectMapper mapper =
            JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
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

    private volatile Process process;
    private volatile PrintWriter stdin;
    private volatile String serverName;
    private volatile String serverVersion;

    /**
     * Creates a new client bound to the given configuration.
     *
     * @param config runtime configuration; must not be {@code null}.
     */
    public ComfyMcpClient(ComfyMcpConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.config.validate();
    }

    /**
     * Spawns the MCP server child process and performs the MCP
     * {@code initialize} handshake plus {@code notifications/initialized}.
     *
     * @return the server-reported version string, may be {@code null}.
     * @throws ComfyException when the process fails to start or the handshake
     *                        fails or times out.
     */
    public String connect() {
        List<String> command = new ArrayList<String>();
        command.add(config.getLocalExecutable());
        if (config.getMcpArgs() != null) {
            for (String arg : config.getMcpArgs()) {
                command.add(arg);
            }
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        if (config.getEnvironment() != null && !config.getEnvironment().isEmpty()) {
            builder.environment().putAll(config.getEnvironment());
        }
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new ComfyException("Failed to spawn comfy-mcp: " + config.getLocalExecutable(), e);
        }
        stdin = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
        Thread reader = new Thread(this::readLoop, "comfy-mcp-reader");
        reader.setDaemon(true);
        reader.start();

        Map<String, Object> clientInfo = new LinkedHashMap<String, Object>();
        clientInfo.put("name", config.getClientName());
        clientInfo.put("version", config.getClientVersion());
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("protocolVersion", config.getProtocolVersion());
        params.put("capabilities", new LinkedHashMap<String, Object>());
        params.put("clientInfo", clientInfo);
        JsonNode result = await(request("initialize", params), config.getConnectTimeoutMillis(), "initialize");
        notify("notifications/initialized", new LinkedHashMap<String, Object>());
        if (result.hasNonNull("serverInfo")) {
            serverName = result.path("serverInfo").path("name").asText(null);
            serverVersion = result.path("serverInfo").path("version").asText(null);
        }
        initialized.set(true);
        return serverVersion;
    }

    /**
     * Lists the tools the server advertises.
     *
     * @return the tool catalog; never {@code null}.
     */
    public List<ComfyMcpTool> listTools() {
        JsonNode result = await(request("tools/list", new LinkedHashMap<String, Object>()),
                config.getConnectTimeoutMillis(), "tools/list");
        List<ComfyMcpTool> tools = new ArrayList<ComfyMcpTool>();
        for (JsonNode tool : result.path("tools")) {
            tools.add(new ComfyMcpTool(
                    tool.path("name").asText(""),
                    tool.hasNonNull("description") ? tool.path("description").asText() : null,
                    tool.path("inputSchema")));
        }
        return tools;
    }

    /**
     * Calls one tool asynchronously.
     *
     * @param name      the tool name.
     * @param arguments tool arguments (JSON-Schema-shaped); may be {@code null}.
     * @return a future completed with the call result, or completed
     *         exceptionally with a {@link ComfyException}.
     */
    public CompletableFuture<ComfyMcpCallResult> callToolAsync(String name, Map<String, Object> arguments) {
        Objects.requireNonNull(name, "name");
        if (closed.get()) {
            throw new IllegalStateException("comfy mcp client is closed");
        }
        if (!initialized.get()) {
            throw new IllegalStateException("comfy mcp client is not connected");
        }
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("name", name);
        params.put("arguments", arguments == null ? new LinkedHashMap<String, Object>() : arguments);
        CompletableFuture<JsonNode> response = request("tools/call", params);
        CompletableFuture<ComfyMcpCallResult> future = response.thenApply(this::toCallResult);
        scheduleTimeout(future, config.getReadTimeoutMillis(), "tools/call " + name);
        return future;
    }

    /**
     * Calls one tool, blocking until it completes.
     *
     * @param name      the tool name.
     * @param arguments tool arguments; may be {@code null}.
     * @return the call result; never {@code null}.
     * @throws ComfyException when the call fails or times out.
     */
    public ComfyMcpCallResult callTool(String name, Map<String, Object> arguments) {
        try {
            return callToolAsync(name, arguments).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ComfyException("comfy mcp tools/call interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof ComfyException) {
                throw (ComfyException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new ComfyException("comfy mcp tools/call failed", cause);
        }
    }

    /**
     * Convenience wrapper for the {@code server_info} tool ("call first" per
     * the Comfy docs &mdash; verifies the local ComfyUI is up).
     *
     * @return the raw JSON-RPC result node; never {@code null}.
     */
    public JsonNode serverInfo() {
        return rawCall("server_info");
    }

    /**
     * Returns the server name reported by {@code initialize}, or {@code null}
     * before {@link #connect()}.
     *
     * @return the server name, may be {@code null}.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Returns the server version reported by {@code initialize}, or
     * {@code null} before {@link #connect()}.
     *
     * @return the server version, may be {@code null}.
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Returns whether the client has been closed.
     *
     * @return {@code true} after {@link #close()}.
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Terminates the MCP server child process and releases the timer.
     * Idempotent.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        timer.shutdownNow();
        Process current = process;
        if (current != null) {
            current.destroy();
        }
        failAllPending(new ComfyException("comfy mcp client closed"));
    }

    // ============================================================
    // transport internals
    // ============================================================

    private JsonNode rawCall(String tool) {
        return await(request("tools/call", paramsFor(tool, null)),
                config.getReadTimeoutMillis(), "tools/call " + tool);
    }

    private Map<String, Object> paramsFor(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("name", name);
        params.put("arguments", arguments == null ? new LinkedHashMap<String, Object>() : arguments);
        return params;
    }

    private ComfyMcpCallResult toCallResult(JsonNode result) {
        StringBuilder text = new StringBuilder();
        int cap = config.getMaxContentChars() <= 0 ? Integer.MAX_VALUE : config.getMaxContentChars();
        boolean truncated = false;
        for (JsonNode content : result.path("content")) {
            if (!"text".equals(content.path("type").asText(""))) {
                continue;
            }
            String piece = content.path("text").asText("");
            if (text.length() >= cap) {
                truncated = true;
                break;
            }
            if (text.length() + piece.length() > cap) {
                text.append(piece, 0, cap - text.length());
                truncated = true;
                break;
            }
            text.append(piece);
        }
        if (truncated) {
            log.warn("comfy mcp tool content truncated at maxContentChars={}", config.getMaxContentChars());
        }
        boolean isError = result.path("isError").asBoolean(false);
        return new ComfyMcpCallResult(text.toString(), isError, result);
    }

    private CompletableFuture<JsonNode> request(String method, Map<String, Object> params) {
        long id = rpcIds.incrementAndGet();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", Long.valueOf(id));
        payload.put("method", method);
        payload.put("params", params);
        CompletableFuture<JsonNode> future = new CompletableFuture<JsonNode>();
        pendingRpcs.put(Long.valueOf(id), future);
        writeJson(payload);
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
        if (closed.get()) {
            throw new ComfyException("comfy mcp client is closed");
        }
        String line;
        try {
            line = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ComfyException("comfy mcp RPC serialization failed", e);
        }
        PrintWriter writer = stdin;
        if (writer == null) {
            throw new ComfyException("comfy mcp client is not connected");
        }
        synchronized (this) {
            writer.println(line);
            if (writer.checkError()) {
                throw new ComfyException("comfy mcp stdin write failed (server exited?)");
            }
        }
    }

    private void readLoop() {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (closed.get()) {
                    return;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (line.length() > effectiveMaxFrameChars()) {
                    log.warn("comfy mcp frame over cap, tearing transport down");
                    failAllPending(new ComfyException(
                            "comfy mcp frame exceeded maxFrameChars=" + config.getMaxFrameChars()));
                    process.destroy();
                    return;
                }
                handleFrame(line);
            }
            failAllPending(new ComfyException("comfy mcp stdout closed (server exited)"));
        } catch (IOException e) {
            if (!closed.get()) {
                failAllPending(new ComfyException("comfy mcp stdout read failed", e));
            }
        }
    }

    private void handleFrame(String frame) {
        JsonNode node;
        try {
            node = mapper.readTree(frame);
        } catch (Exception ex) {
            log.warn("Ignored non-JSON frame from comfy mcp");
            return;
        }
        if (node.hasNonNull("id")) {
            CompletableFuture<JsonNode> pending = pendingRpcs.remove(Long.valueOf(node.get("id").asLong()));
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
        log.debug("Ignored comfy mcp notification: method={}", node.path("method").asText(""));
    }

    private void scheduleTimeout(CompletableFuture<?> future, long timeoutMillis, String what) {
        if (timeoutMillis <= 0) {
            return;
        }
        java.util.concurrent.ScheduledFuture<?> guard = timer.schedule(() -> future
                .completeExceptionally(new ComfyException("comfy mcp " + what + " timed out after "
                        + timeoutMillis + " ms")), timeoutMillis, TimeUnit.MILLISECONDS);
        future.whenComplete((r, error) -> guard.cancel(false));
    }

    private JsonNode await(CompletableFuture<JsonNode> future, long timeoutMillis, String what) {
        scheduleTimeout(future, timeoutMillis, what);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ComfyException("comfy mcp " + what + " interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof ComfyException) {
                throw (ComfyException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new ComfyException("comfy mcp " + what + " failed", cause);
        }
    }

    private void failAllPending(ComfyException error) {
        for (Map.Entry<Long, CompletableFuture<JsonNode>> entry : pendingRpcs.entrySet()) {
            CompletableFuture<JsonNode> future = pendingRpcs.remove(entry.getKey());
            if (future != null) {
                future.completeExceptionally(error);
            }
        }
    }

    private int effectiveMaxFrameChars() {
        return config.getMaxFrameChars() <= 0 ? Integer.MAX_VALUE : config.getMaxFrameChars();
    }
}
