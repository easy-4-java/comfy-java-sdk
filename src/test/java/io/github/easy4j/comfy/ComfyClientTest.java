/*
 * Copyright (c) 2018-present, easy-4-java.
 */
package io.github.easy4j.comfy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.cli.ComfyCli;
import io.github.easy4j.comfy.model.ComfyCliEnvelope;
import tools.jackson.databind.JsonNode;

class ComfyClientTest {

    private static String resource(String name) {
        return Paths.get("src", "test", "resources", name).toAbsolutePath().toString();
    }

    private static ComfyClientConfig config(String executable) {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(executable);
        config.setLocalTimeoutSeconds(2);
        return config;
    }

    @Test
    void shouldDelegateBasics() {
        try (ComfyClient client = new ComfyClient(config(resource("comfy-echo.sh")))) {
            assertTrue(client.version().getStdout().contains("--version"));
            assertTrue(client.isAvailable());
            assertNotNull(client.cli());
            assertNotNull(client.getConfig());
        }
    }

    @Test
    void shouldParseGenerateJsonWithoutMutatingOptions() {
        ComfyCli.GenerateOptions options = new ComfyCli.GenerateOptions().prompt("hi");
        try (ComfyClient jsonClient = new ComfyClient(config(resource("comfy-json.sh")))) {
            JsonNode json = jsonClient.generateJson("flux-pro", options);
            assertEquals("https://example/asset.png", json.path("data").get(0).path("url").asText());
        }

        try (ComfyClient echoClient = new ComfyClient(config(resource("comfy-echo.sh")))) {
            String args = echoClient.cli().generate("flux-pro", options).getStdout();
            assertFalse(args.contains("--json"), "generateJson must not mutate caller options");
        }
    }

    @Test
    void shouldParseUniformCliEnvelope() {
        try (ComfyClient client = new ComfyClient(config(resource("comfy-envelope.sh")))) {
            ComfyCliEnvelope envelope = client.environment();
            assertTrue(envelope.isOk());
            assertEquals("env", envelope.getCommand());
            assertEquals("local", envelope.getWhere());
            assertTrue(envelope.getData().path("running").asBoolean());
        }
    }

    @Test
    void shouldRejectTruncatedJsonEnvelope() {
        ComfyClientConfig config = config(resource("comfy-envelope.sh"));
        config.setMaxOutputBytes(12);
        try (ComfyClient client = new ComfyClient(config)) {
            assertThrows(ComfyException.class, client::environment);
        }
    }

    @Test
    void shouldRejectInvalidConfig() {
        ComfyClientConfig config = config(resource("comfy-echo.sh"));
        config.setDefaultWhere("bogus");
        assertThrows(IllegalArgumentException.class, () -> new ComfyClient(config));
    }
}
