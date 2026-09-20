/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import tools.jackson.databind.JsonNode;

/**
 * Optional handler for MCP server-originated elicitation requests.
 * The client advertises elicitation capability only when a handler is configured before connect().
 */
public interface ComfyMcpElicitationHandler {
    Object handle(String method, JsonNode params);
}
