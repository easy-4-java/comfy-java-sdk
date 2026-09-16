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
 * Outcome of one {@code tools/call}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyMcpClient#callTool(String, java.util.Map)
 */
@Data
public class ComfyMcpCallResult {

    /**
     * Concatenated text of every {@code text} content item the tool returned,
     * subject to {@code maxContentChars} truncation.
     */
    private final String text;

    /** Whether the tool reported a business-level failure ({@code isError}). */
    private final boolean isError;

    /** The raw JSON-RPC result node, for callers needing non-text content. */
    private final JsonNode raw;
}
