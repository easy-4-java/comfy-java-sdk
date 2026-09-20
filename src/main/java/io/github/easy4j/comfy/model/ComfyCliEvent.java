/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** One NDJSON event emitted by a streaming comfy CLI command. */
@Data
public class ComfyCliEvent {
    private final String schema;
    private final String type;
    private final JsonNode raw;

    public boolean isEnvelope() { return "envelope".equals(type); }
}
