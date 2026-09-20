/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import java.util.Map;
import java.util.Objects;

import lombok.Data;

/** Configuration for the local comfy-mcp stdio transport. */
@Data
public class ComfyMcpConfig {

    private String localExecutable = "comfy-mcp";
    private String[] mcpArgs;
    private Map<String, String> environment;
    private String protocolVersion = "2024-11-05";
    private String clientName = "comfy-java-sdk";
    private String clientVersion = "1.0.0";
    private int connectTimeoutMillis = 10_000;
    private int readTimeoutMillis = 900_000;
    private int maxFrameChars = 1_048_576;
    private int maxContentChars = 1_048_576;
    private int maxStderrChars = 65_536;
    private int processShutdownGraceMillis = 2_000;

    public void validate() {
        Objects.requireNonNull(localExecutable, "localExecutable");
        Objects.requireNonNull(clientName, "clientName");
        Objects.requireNonNull(clientVersion, "clientVersion");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        if (localExecutable.trim().isEmpty()) throw new IllegalStateException("localExecutable must not be blank");
        if (connectTimeoutMillis <= 0) throw new IllegalStateException("connectTimeoutMillis must be > 0");
        if (readTimeoutMillis <= 0) throw new IllegalStateException("readTimeoutMillis must be > 0");
        if (maxFrameChars < 0 || maxContentChars < 0 || maxStderrChars < 0) {
            throw new IllegalStateException("MCP capture limits must be >= 0");
        }
        if (processShutdownGraceMillis < 0) {
            throw new IllegalStateException("processShutdownGraceMillis must be >= 0");
        }
    }
}
