/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.mcp;

import java.util.Collections;
import java.util.List;

import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;

/** Outcome of one MCP {@code tools/call}. */
@Data
public class ComfyMcpCallResult {
    private final String text;
    private final boolean isError;
    private final JsonNode raw;
    private final List<ComfyMcpContent> contents;
    private final boolean textTruncated;

    /** Backward-compatible constructor retained for existing SDK callers. */
    public ComfyMcpCallResult(String text, boolean isError, JsonNode raw) {
        this(text, isError, raw, Collections.<ComfyMcpContent>emptyList(), false);
    }

    public ComfyMcpCallResult(String text, boolean isError, JsonNode raw,
            List<ComfyMcpContent> contents, boolean textTruncated) {
        this.text = text;
        this.isError = isError;
        this.raw = raw;
        this.contents = contents == null
                ? Collections.<ComfyMcpContent>emptyList()
                : Collections.unmodifiableList(contents);
        this.textTruncated = textTruncated;
    }
}
