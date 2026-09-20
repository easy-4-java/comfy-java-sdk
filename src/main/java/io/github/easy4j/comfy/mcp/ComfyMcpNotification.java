/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import tools.jackson.databind.JsonNode;
import lombok.Data;

/** Server-to-client MCP notification preserved with raw JSON for forward compatibility. */
@Data
public class ComfyMcpNotification {
    private final String method;
    private final JsonNode params;
    private final JsonNode raw;
}
