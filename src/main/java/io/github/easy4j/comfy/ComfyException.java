/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy;

/**
 * Unchecked SDK exception for CLI execution/parsing and MCP transport/RPC
 * failures. Business-level MCP tool errors remain represented by
 * {@code ComfyMcpCallResult#isError()}.
 */
public class ComfyException extends RuntimeException {
    public ComfyException(String message) { super(message); }
    public ComfyException(String message, Throwable cause) { super(message, cause); }
}
