/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.mcp;

import java.util.Map;
import java.util.Objects;

import lombok.Data;

/** Configuration for the local {@code comfy-mcp} stdio route. */
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
    private int shutdownTimeoutMillis = 2_000;

    /**
     * Hard frame limit before JSON parsing. 16 MiB allows normal inline image
     * responses while still bounding a malicious/broken single-line frame.
     * {@code <= 0} means unbounded.
     */
    private int maxFrameChars = 16 * 1024 * 1024;

    /** Text aggregation cap for one tools/call. {@code <= 0} means unbounded. */
    private int maxContentChars = 4 * 1024 * 1024;

    public void validate() {
        Objects.requireNonNull(localExecutable, "localExecutable");
        Objects.requireNonNull(clientName, "clientName");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        if (localExecutable.trim().isEmpty()) {
            throw new IllegalStateException("localExecutable must not be blank");
        }
        if (connectTimeoutMillis <= 0) {
            throw new IllegalStateException("connectTimeoutMillis must be > 0");
        }
        if (readTimeoutMillis <= 0) {
            throw new IllegalStateException("readTimeoutMillis must be > 0");
        }
        if (shutdownTimeoutMillis < 0) {
            throw new IllegalStateException("shutdownTimeoutMillis must be >= 0");
        }
    }
}
