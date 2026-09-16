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
package io.github.easy4j.comfy;

/**
 * Unchecked exception raised by the ACP and web-server routes when a turn or
 * HTTP call fails: connection errors, JSON-RPC errors, malformed frames,
 * read timeouts and non-zero business codes are all surfaced as this type.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
public class ComfyException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message human-readable description of the failure.
     */
    public ComfyException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message human-readable description of the failure.
     * @param cause   the underlying cause, may be {@code null}.
     */
    public ComfyException(String message, Throwable cause) {
        super(message, cause);
    }
}
