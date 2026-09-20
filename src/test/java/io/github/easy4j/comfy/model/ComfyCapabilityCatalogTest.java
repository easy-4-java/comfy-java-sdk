/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClient;
import io.github.easy4j.comfy.ComfyClientConfig;

class ComfyCapabilityCatalogTest {
    @Test
    void shouldFindCommandsInForwardCompatibleRawDiscoveryTree() {
        ComfyClient client = new ComfyClient(new ComfyClientConfig());
        ComfyJsonEnvelope envelope = client.parseEnvelope(
                "{\"ok\":true,\"version\":\"1.15.0\",\"data\":"
                + "{\"commands\":[{\"name\":\"run\"},{\"name\":\"workflow\","
                + "\"subcommands\":[\"slots\",\"vary\"]}]}}");
        ComfyCapabilityCatalog catalog =
                new ComfyCapabilityCatalog(envelope.getVersion(), envelope.getData());
        assertTrue(catalog.containsCommand("run"));
        assertTrue(catalog.containsCommand("vary"));
        assertFalse(catalog.containsCommand("not-a-command"));
    }
}
