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

import java.util.Map;
import java.util.Objects;

import lombok.Data;

/**
 * Configuration for the comfy CLI subprocess route.
 *
 * <p>Plain POJO (Spring {@code @ConfigurationProperties}-bindable).</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyClient
 */
@Data
public class ComfyClientConfig {

    /** Name or absolute path of the local {@code comfy} CLI executable. */
    private String localExecutable = "comfy";

    /**
     * Extra environment variables for the child process (e.g.
     * {@code COMFY_API_KEY}, {@code COMFY_WHERE}); merged over the parent
     * environment. Credentials must travel here — never as command line
     * arguments, which are visible in {@code ps} output.
     */
    private Map<String, String> environment;

    /** Command execution timeout in seconds (generation runs can be long). */
    private int localTimeoutSeconds = 600;

    /** Timeout in seconds used by {@link ComfyCliExecutor#probe()} when verifying CLI availability. */
    private int localProbeTimeoutSeconds = 5;

    /**
     * Default routing forwarded as {@code --where <where>} to commands that
     * accept it: {@code local} or {@code cloud}. The CLI also honours the
     * {@code COMFY_WHERE} environment variable via {@link #environment}.
     */
    private String defaultWhere;

    /**
     * Validates the configuration.
     *
     * @throws IllegalStateException when {@code defaultWhere} is neither
     *                               {@code local} nor {@code cloud}.
     */
    public void validate() {
        Objects.requireNonNull(localExecutable, "localExecutable");
        if (defaultWhere != null && !"local".equals(defaultWhere) && !"cloud".equals(defaultWhere)) {
            throw new IllegalStateException("defaultWhere must be 'local' or 'cloud': " + defaultWhere);
        }
    }
}
