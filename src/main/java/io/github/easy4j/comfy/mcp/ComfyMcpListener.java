/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

/** Listener for MCP notifications such as progress/log messages. */
public interface ComfyMcpListener {
    void onNotification(ComfyMcpNotification notification);
}
