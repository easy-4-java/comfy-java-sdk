/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.model;

import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Uniform JSON envelope emitted by {@code comfy --json <command>}.
 *
 * <p>The payload intentionally keeps {@code data} and {@code error} as
 * {@link JsonNode}: the command tree is self-describing via
 * {@code comfy --json discover} and evolves independently of this SDK.</p>
 */
@Data
public class ComfyCliEnvelope {
    private boolean ok;
    private String command;
    private String version;
    private String where;
    private JsonNode data;
    private JsonNode error;
}
