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

import lombok.Data;

import tools.jackson.databind.JsonNode;

/**
 * One tool advertised by the MCP server via {@code tools/list}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyMcpClient#listTools()
 */
@Data
public class ComfyMcpTool {

    /** Tool name (e.g. {@code run_workflow}, {@code search_templates}). */
    private final String name;

    /** Human-readable description; may be {@code null}. */
    private final String description;

    /** JSON-Schema of the tool's {@code arguments} object; never {@code null}. */
    private final JsonNode inputSchema;
}
