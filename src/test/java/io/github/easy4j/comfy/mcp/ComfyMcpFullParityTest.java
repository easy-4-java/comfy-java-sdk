/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class ComfyMcpFullParityTest {

    private static ComfyMcpConfig config() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        config.setLocalExecutable("python3");
        config.setMcpArgs(new String[] {
                Paths.get("src", "test", "resources", "fake-mcp-server.py").toAbsolutePath().toString()
        });
        config.setConnectTimeoutMillis(5_000);
        config.setReadTimeoutMillis(5_000);
        return config;
    }

    @Test
    void reviewedCatalogMustContainFortyFirstPartyTools() {
        assertEquals(40, ComfyMcpClient.firstPartyToolNames().size());
        assertTrue(ComfyMcpClient.firstPartyToolNames().contains("run_workflow"));
        assertTrue(ComfyMcpClient.firstPartyToolNames().contains("vary_workflow"));
    }

    @Test
    void typedFacadeShouldReachPreviouslyMissingTools() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            client.connect();
            assertFalse(client.authStatus().isError());
            assertFalse(client.billingStatus().isError());
            assertFalse(client.generateImage("cat", null, false, 10).isError());
            assertFalse(client.listPartnerModels("", "", "", 10, 0).isError());
            assertFalse(client.partnerModelSchema("model").isError());
            assertFalse(client.runTemplate("basic", Collections.<String,Object>emptyMap(), false, false, 10).isError());
            assertFalse(client.systemStats().isError());
            assertFalse(client.freeMemory().isError());
            assertFalse(client.getLogs(20, null).isError());
            assertFalse(client.which().isError());
            assertFalse(client.project("status").isError());
            assertFalse(client.nodeDependencies("pack", "").isError());
            assertFalse(client.workflowDeps("wf.json").isError());
            assertFalse(client.downloadModel("https://example.invalid/model", null, "m.safetensors", false, 10).isError());
            assertFalse(client.download("status", "d1", null).isError());
            assertFalse(client.setWorkflowSlot("wf.json", Arrays.asList("1:seed=1"), true).isError());
            assertFalse(client.varyWorkflow("wf.json", Arrays.asList("1:seed=1,2"), null).isError());
        }
    }
}
