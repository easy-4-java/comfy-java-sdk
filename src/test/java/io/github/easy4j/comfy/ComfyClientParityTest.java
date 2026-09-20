/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.cli.ComfyCli;
import io.github.easy4j.comfy.model.ComfyJsonEnvelope;

class ComfyClientParityTest {

    @Test
    void shouldParseOfficialEnvelopeShape() {
        ComfyClient client = new ComfyClient(new ComfyClientConfig());
        ComfyJsonEnvelope envelope = client.parseEnvelope(
                "{\"schema\":\"envelope/1\",\"type\":\"envelope\",\"ok\":true,"
                + "\"command\":\"which\",\"version\":\"1.15.0\",\"where\":\"local\","
                + "\"data\":{\"workspace_path\":\"/tmp/ComfyUI\"},\"error\":null}");
        assertTrue(envelope.isOk());
        assertEquals("which", envelope.getCommand());
        assertEquals("/tmp/ComfyUI", envelope.getData().path("workspace_path").asText());
    }

    @Test
    void generateJsonMustNotMutateCallerOptions() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(Paths.get("src", "test", "resources", "comfy-echo.sh")
                .toAbsolutePath().toString());
        config.setLocalTimeoutSeconds(2);
        ComfyClient client = new ComfyClient(config);
        ComfyCli.GenerateOptions options = new ComfyCli.GenerateOptions().prompt("cat");

        assertThrows(ComfyException.class, () -> client.generateJson("model", options));

        String secondCall = client.cli().generate("model", options).getStdout();
        assertTrue(!secondCall.contains("--json"),
                "generateJson must not mutate the caller-owned options instance");
    }
}
