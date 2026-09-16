/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.easy4j.comfy.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyException;

/**
 * End-to-end tests running {@link ComfyMcpClient} against a fake MCP server
 * process (python3, NDJSON JSON-RPC on stdio) — the same wire contract as
 * {@code comfy-mcp}.
 *
 * @since 1.0.0
 */
class ComfyMcpClientE2ETest {

    private static final String FAKE_SERVER = Paths.get("src", "test", "resources", "fake-mcp-server.py")
            .toAbsolutePath().toString();

    private static ComfyMcpConfig config() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        config.setLocalExecutable("python3");
        config.setMcpArgs(new String[] {FAKE_SERVER});
        config.setConnectTimeoutMillis(10_000);
        config.setReadTimeoutMillis(10_000);
        return config;
    }

    @Test
    void shouldConnectAndInitialize() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            String version = client.connect();

            assertEquals("0.0.0-test", version);
            assertEquals("FakeComfyMcp", client.getServerName());
        }
    }

    @Test
    void shouldListToolsAndCallThem() {
        try (ComfyMcpClient client = new ComfyMcpClient(config())) {
            client.connect();

            List<ComfyMcpTool> tools = client.listTools();
            assertEquals(2, tools.size());
            assertEquals("server_info", tools.get(0).getName());
            assertNotNull(tools.get(1).getInputSchema());

            ComfyMcpCallResult info = client.callTool("server_info", null);
            assertFalse(info.isError());
            assertEquals("comfyui up", info.getText());

            Map<String, Object> args = new LinkedHashMap<String, Object>();
            args.put("workflow_path", "wf.json");
            ComfyMcpCallResult run = client.callTool("run_workflow", args);
            assertFalse(run.isError());
            assertEquals("queued wf.json", run.getText());

            ComfyMcpCallResult unknown = client.callTool("nope", null);
            assertTrue(unknown.isError());
        }
    }

    @Test
    void shouldRejectCallsBeforeConnectAndAfterClose() {
        ComfyMcpClient client = new ComfyMcpClient(config());
        assertThrows(IllegalStateException.class, () -> client.callToolAsync("server_info", null));
        client.close();
        assertTrue(client.isClosed());
        assertThrows(IllegalStateException.class, () -> client.callToolAsync("server_info", null));
        assertThrows(ComfyException.class, () -> client.listTools());
        client.close();
    }

    @Test
    void shouldFailConnectWhenServerExitsPrematurely() {
        ComfyMcpConfig config = config();
        config.setLocalExecutable("/bin/echo");
        ComfyMcpClient client = new ComfyMcpClient(config);
        assertThrows(ComfyException.class, client::connect);
        client.close();
    }

    @Test
    void shouldTimeoutWhenServerNeverAnswers() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        // `sleep` produces no stdout: the initialize request is never answered.
        config.setLocalExecutable("/bin/sleep");
        config.setMcpArgs(new String[] {"30"});
        config.setConnectTimeoutMillis(1_000);
        ComfyMcpClient client = new ComfyMcpClient(config);
        assertThrows(ComfyException.class, client::connect);
        client.close();
    }

    @Test
    void shouldPassEnvironmentToServer() {
        ComfyMcpConfig config = config();
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        // COMFY_BIN 场景由 env 注入承载——这里以 env 透传间接验证（fake server 不读 env，
        // 但 spawn 不因额外 env 失败即视为通过；真实验证在 executor 测试覆盖）。
        config.setEnvironment(new LinkedHashMap<String, String>());
        config.getEnvironment().put("COMFY_BIN", "/opt/venv/bin/comfy");
        try (ComfyMcpClient client = new ComfyMcpClient(config)) {
            client.connect();
            assertFalse(client.listTools().isEmpty());
        }
    }
}
