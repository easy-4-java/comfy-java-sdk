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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ComfyMcpConfig}. @since 1.0.0 */
class ComfyMcpConfigTest {

    @Test
    void shouldExposeSensibleDefaults() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        assertEquals("comfy-mcp", config.getLocalExecutable());
        assertEquals("2024-11-05", config.getProtocolVersion());
        assertEquals("comfy-java-sdk", config.getClientName());
        assertEquals(10_000, config.getConnectTimeoutMillis());
        assertEquals(900_000, config.getReadTimeoutMillis());
        assertEquals(1_048_576, config.getMaxFrameChars());
        assertEquals(1_048_576, config.getMaxContentChars());
    }

    @Test
    void shouldAcceptExtraArgsAndEnvironment() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        config.setMcpArgs(new String[] {"--port", "8188"});
        config.setEnvironment(java.util.Collections.singletonMap("COMFY_BIN", "/opt/venv/bin/comfy"));
        config.validate();
        assertEquals(2, config.getMcpArgs().length);
        assertEquals("/opt/venv/bin/comfy", config.getEnvironment().get("COMFY_BIN"));
    }

    @Test
    void shouldRejectNullExecutable() {
        ComfyMcpConfig config = new ComfyMcpConfig();
        config.setLocalExecutable(null);
        assertThrows(NullPointerException.class, config::validate);
    }
}
