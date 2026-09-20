/*
 * Copyright (c) 2018-present, easy-4-java.
 */
package io.github.easy4j.comfy.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyException;

class ComfyMcpClientE2ETest {

    private static final String FAKE_SERVER = Paths
            .get("src", "test", "resources", "fake-mcp-server.py")
            .toAbsolutePath().toString();

    private static ComfyMcpConfig config() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        config.setLocalExecutable("python3");
        config.setMcpArgs(new String[] {FAKE_SERVER});
        config.setConnectTimeoutMillis(5_000);
        config.setReadTimeoutMillis(5_000);
        config.setShutdownTimeoutMillis(1_000);
        return config;
    }

    @Test
    void shouldConnectListToolsAndRejectDuplicateConnect() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            assertEquals("0.0.0-test", client.connect());
            assertEquals("FakeComfyMcp", client.getServerName());
            assertTrue(client.isConnected());
            List<ComfyMcpTool> tools = client.listTools();
            assertEquals(3, tools.size());
            assertThrows(IllegalStateException.class, client::connect);
        }
    }

    @Test
    void shouldCallTypedWorkflowWrapper() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            client.connect();
            ComfyMcpCallResult result = client.runWorkflow("wf.json", false, 12.5, false);
            assertFalse(result.isError());
            assertTrue(result.getText().contains("\"workflow_path\": \"wf.json\""));
            assertTrue(result.getText().contains("\"wait\": false"));
        }
    }

    @Test
    void shouldPreserveNonTextContent() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            client.connect();
            ComfyMcpCallResult result = client.callTool("mixed_content", null);
            assertEquals("hello", result.getText());
            assertEquals(3, result.getContents().size());
            assertEquals("image", result.getContents().get(1).getType());
            assertEquals("image/png", result.getContents().get(1).getMimeType());
            assertEquals("aGVsbG8=", result.getContents().get(1).getData());
            assertEquals("file:///tmp/out.png", result.getContents().get(2).getUri());
        }
    }

    @Test
    void shouldDrainLargeStderrWithoutDeadlock() {
        ComfyMcpConfig config = config();
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("FAKE_MCP_STDERR_BYTES", "262144");
        config.setEnvironment(env);
        try (ComfyMcpClient client = new ComfyMcpClient(config)) {
            client.connect();
            assertEquals("comfyui up", client.callTool("server_info", null).getText());
        }
    }

    @Test
    void shouldRemoveTimedOutRpcFromPendingMap() throws Exception {
        ComfyMcpConfig config = config();
        config.setReadTimeoutMillis(100);
        try (ComfyMcpClient client = new ComfyMcpClient(config)) {
            client.connect();
            assertThrows(ComfyException.class, () -> client.callTool("slow", null));
            Thread.sleep(50L);
            assertEquals(0, client.pendingRequestCount());
        }
    }

    @Test
    void shouldRejectCallsBeforeConnectAndAfterClose() {
        ComfyMcpClient client = new ComfyMcpClient(config());
        assertThrows(IllegalStateException.class, () -> client.callToolAsync("server_info", null));
        client.close();
        assertTrue(client.isClosed());
        assertFalse(client.isConnected());
        assertThrows(IllegalStateException.class, () -> client.callToolAsync("server_info", null));
        client.close();
    }

    @Test
    void shouldFailConnectWhenServerExitsPrematurely() {
        ComfyMcpConfig config = config();
        config.setLocalExecutable("/bin/echo");
        try (ComfyMcpClient client = new ComfyMcpClient(config)) {
            assertThrows(ComfyException.class, client::connect);
        }
    }

    @Test
    void shouldPassEnvironmentAndExerciseCurrentToolConveniences() {
        ComfyMcpConfig config = config();
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("COMFY_BIN", "/opt/venv/bin/comfy");
        config.setEnvironment(env);
        try (ComfyMcpClient client = new ComfyMcpClient(config)) {
            client.connect();
            assertNotNull(client.serverInfo());
            assertTrue(client.fetchOutputs("p1", "/tmp/out", true, false).getText().contains("p1"));
            assertTrue(client.searchModels("wan", "loras").getText().contains("wan"));
            assertTrue(client.launchComfyUi(Arrays.asList("--port", "8188"), false)
                    .getText().contains("8188"));
        }
    }

    @Test
    void businessLevelMcpErrorsShouldRemainResults() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            client.connect();
            assertTrue(client.callTool("nope", null).isError());
        }
    }
}
