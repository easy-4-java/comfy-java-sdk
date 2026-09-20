/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyException;

class ComfyMcpHardeningTest {

    private static final String FAKE = Paths.get("src", "test", "resources", "fake-mcp-server.py")
            .toAbsolutePath().toString();

    private static ComfyMcpConfig config() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        config.setLocalExecutable("python3");
        config.setMcpArgs(new String[] {FAKE});
        config.setConnectTimeoutMillis(5_000);
        config.setReadTimeoutMillis(500);
        config.setProcessShutdownGraceMillis(500);
        return config;
    }

    @Test
    void duplicateConnectMustBeIdempotentAndCloseMustReapProcess() {
        ComfyMcpClient client = new ComfyMcpClient(config());
        assertEquals("0.0.0-test", client.connect());
        assertTrue(client.isConnected());
        assertTrue(client.isProcessAlive());

        assertEquals("0.0.0-test", client.connect());
        assertTrue(client.isProcessAlive());

        client.close();
        assertTrue(client.isClosed());
        assertFalse(client.isProcessAlive());
        client.close();
    }

    @Test
    void timeoutMustRemovePendingRpcEntry() {
        ComfyMcpClient client = new ComfyMcpClient(config());
        try {
            client.connect();
            assertThrows(ComfyException.class, () -> client.callTool("hang", null));
            assertEquals(0, client.pendingRpcCount());
        } finally {
            client.close();
        }
    }

    @Test
    void stderrMustBeDrainedAndBounded() {
        ComfyMcpConfig config = config();
        config.setMaxStderrChars(1024);
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("FAKE_MCP_SPAM_STDERR", "200000");
        config.setEnvironment(env);

        try (ComfyMcpClient client = new ComfyMcpClient(config)) {
            client.connect();
            assertEquals("comfyui up", client.callTool("server_info", null).getText());
            assertTrue(client.getStderrTail().length() <= 1024);
        }
    }

    @Test
    void notificationsMustBeSurfaced() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            final java.util.List<ComfyMcpNotification> seen =
                    new java.util.ArrayList<ComfyMcpNotification>();
            client.addListener(new ComfyMcpListener() {
                @Override public void onNotification(ComfyMcpNotification notification) {
                    seen.add(notification);
                }
            });
            client.connect();
            assertFalse(client.callTool("notification_test", null).isError());
            assertEquals(1, seen.size());
            assertEquals("notifications/progress", seen.get(0).getMethod());
        }
    }

    @Test
    void nonTextMcpContentMustRemainStructured() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            client.connect();
            ComfyMcpCallResult result = client.callTool("mixed_content", null);
            assertEquals("preview", result.getText());
            assertEquals(2, result.getContents().size());
            ComfyMcpContent image = result.getContents().get(1);
            assertTrue(image.isImage());
            assertEquals("image/png", image.getMimeType());
            assertEquals("aGVsbG8=", image.getData());
        }
    }
}
