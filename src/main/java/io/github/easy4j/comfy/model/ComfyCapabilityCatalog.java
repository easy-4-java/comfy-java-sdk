/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.model;

import java.util.Iterator;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import lombok.Data;

/** Runtime CLI capability snapshot derived from comfy --json discover. */
@Data
public class ComfyCapabilityCatalog {
    private final String cliVersion;
    private final JsonNode raw;

    public boolean containsCommand(String command) {
        if (command == null || command.trim().isEmpty()) return false;
        return contains(raw, command);
    }

    private static boolean contains(JsonNode node, String value) {
        if (node == null || node.isMissingNode() || node.isNull()) return false;
        if (node.isTextual()) return value.equals(node.asText());
        if (node.isArray()) {
            for (JsonNode child : node) if (contains(child, value)) return true;
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (value.equals(field.getKey()) || contains(field.getValue(), value)) return true;
            }
        }
        return false;
    }
}
