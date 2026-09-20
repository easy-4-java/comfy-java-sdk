/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import tools.jackson.databind.JsonNode;
import lombok.Data;

/** One MCP content item. Raw JSON is retained for forward compatibility. */
@Data
public class ComfyMcpContent {
    private final String type;
    private final String text;
    private final String mimeType;
    private final String uri;
    private final JsonNode raw;

    public boolean isText() { return "text".equals(type); }
    public boolean isImage() { return "image".equals(type); }
    public boolean isAudio() { return "audio".equals(type); }

    /** Base64 or provider-specific data, resolved lazily to avoid a duplicate copy. */
    public String getData() {
        if (raw == null) return null;
        JsonNode value = raw.path("data");
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
