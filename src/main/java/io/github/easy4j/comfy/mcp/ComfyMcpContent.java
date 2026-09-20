/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.mcp;

import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * One MCP content item. Known common fields are projected for convenience and
 * {@link #raw} preserves new MCP content variants without SDK upgrades.
 */
@Data
public class ComfyMcpContent {
    private final String type;
    private final String text;
    private final String mimeType;
    private final String data;
    private final String uri;
    private final JsonNode raw;
}
