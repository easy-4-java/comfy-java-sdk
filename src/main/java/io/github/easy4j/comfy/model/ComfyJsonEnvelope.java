/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.model;

import tools.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Forward-compatible representation of the CLI's global {@code --json}
 * envelope. Unknown fields remain available through {@link #raw}.
 */
@Data
public class ComfyJsonEnvelope {
    private final boolean ok;
    private final String command;
    private final String version;
    private final String where;
    private final JsonNode data;
    private final JsonNode error;
    private final JsonNode raw;

    public String getErrorHint() {
        if (error == null || error.isMissingNode() || error.isNull()) {
            return null;
        }
        JsonNode hint = error.path("hint");
        return hint.isMissingNode() || hint.isNull() ? null : hint.asText();
    }
}
