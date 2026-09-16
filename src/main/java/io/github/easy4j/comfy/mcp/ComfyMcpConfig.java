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

import java.util.Map;
import java.util.Objects;

import lombok.Data;

/**
 * Configuration for the comfy-mcp route.
 *
 * <p>{@code comfy-mcp} speaks MCP over stdio (newline-delimited JSON-RPC).
 * The SDK spawns it as a child process and drives {@code tools/list} /
 * {@code tools/call} through that channel &mdash; no sockets, so this route
 * works on every JDK line the SDK supports.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyMcpClient
 */
@Data
public class ComfyMcpConfig {

    /**
     * Name or absolute path of the MCP server executable
     * (e.g. {@code comfy-mcp} from PyPI, or an absolute venv path).
     */
    private String localExecutable = "comfy-mcp";

    /** Extra arguments forwarded to the server executable. */
    private String[] mcpArgs;

    /**
     * Extra environment variables for the server process (e.g.
     * {@code COMFY_BIN} pointing at the workspace's {@code comfy} binary);
     * merged over the parent environment. Required when {@code comfy} is not
     * on the PATH the SDK's process sees.
     */
    private Map<String, String> environment;

    /** MCP protocol version advertised in {@code initialize}. */
    private String protocolVersion = "2024-11-05";

    /** Client name advertised in {@code initialize}. */
    private String clientName = "comfy-java-sdk";

    /** Client version advertised in {@code initialize}. */
    private String clientVersion = "1.0.0";

    /** Timeout in milliseconds for process startup plus the MCP {@code initialize} handshake. */
    private int connectTimeoutMillis = 10_000;

    /** Upper bound in milliseconds for one {@code tools/call} (generation tools can run for minutes). */
    private int readTimeoutMillis = 900_000;

    /**
     * Hard cap in characters for one JSON-RPC frame on the wire; a frame
     * exceeding it tears the transport down. {@code <= 0} means unbounded.
     * Defaults to 1&nbsp;MiB characters.
     */
    private int maxFrameChars = 1_048_576;

    /**
     * Hard cap in characters for the text content accumulated per
     * {@code tools/call}; excess content items are truncated with a warning.
     * {@code <= 0} means unbounded. Defaults to 1&nbsp;MiB characters.
     */
    private int maxContentChars = 1_048_576;

    /**
     * Validates the configuration.
     *
     * @throws NullPointerException when the executable or client name is {@code null}.
     */
    public void validate() {
        Objects.requireNonNull(localExecutable, "localExecutable");
        Objects.requireNonNull(clientName, "clientName");
    }
}
