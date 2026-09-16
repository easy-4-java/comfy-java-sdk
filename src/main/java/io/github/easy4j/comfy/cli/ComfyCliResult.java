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
package io.github.easy4j.comfy.cli;

import java.util.Objects;

import lombok.Data;

/**
 * Outcome of one {@code comfy} CLI invocation: exit code plus both captured
 * streams. Non-zero exits preserve the real exit code and both buffers &mdash;
 * nothing is collapsed into a sentinel value.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyCliExecutor
 */
@Data
public class ComfyCliResult {

    private static final String TIMEOUT_PREFIX = "comfy CLI timed out after ";

    /** Process exit code; {@code -1} when the executable was missing or the run timed out. */
    private final int exitCode;

    /** Trimmed standard output of the child process. */
    private final String stdout;

    /** Trimmed standard error of the child process. */
    private final String stderr;

    /**
     * Returns whether the invocation exited with status zero.
     *
     * @return {@code true} when {@code exitCode == 0}.
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    /**
     * Returns whether the run was terminated by the watchdog timeout.
     *
     * @return {@code true} when the exit code is {@code -1} and stderr carries
     *         the timeout notice.
     */
    public boolean isTimeout() {
        return exitCode == -1 && Objects.nonNull(stderr) && stderr.startsWith(TIMEOUT_PREFIX);
    }
}
