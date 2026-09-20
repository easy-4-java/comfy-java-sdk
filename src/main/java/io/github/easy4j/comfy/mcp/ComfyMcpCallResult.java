/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import java.util.Collections;
import java.util.List;

import tools.jackson.databind.JsonNode;
import lombok.Data;

/** Outcome of one MCP tools/call. */
@Data
public class ComfyMcpCallResult {
    private final String text;
    private final boolean isError;
    private final JsonNode raw;
    private final List<ComfyMcpContent> contents;

    public ComfyMcpCallResult(String text, boolean isError, JsonNode raw) {
        this(text, isError, raw, Collections.<ComfyMcpContent>emptyList());
    }

    public ComfyMcpCallResult(String text, boolean isError, JsonNode raw,
                              List<ComfyMcpContent> contents) {
        this.text = text;
        this.isError = isError;
        this.raw = raw;
        this.contents = contents == null
                ? Collections.<ComfyMcpContent>emptyList()
                : Collections.unmodifiableList(contents);
    }
}
