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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ComfyClientConfig} defaults and validation. @since 1.0.0 */
class ComfyClientConfigTest {

    @Test
    void shouldExposeSensibleDefaults() {
        ComfyClientConfig config = new ComfyClientConfig();
        assertEquals("comfy", config.getLocalExecutable());
        assertEquals(600, config.getLocalTimeoutSeconds());
        assertEquals(5, config.getLocalProbeTimeoutSeconds());
    }

    @Test
    void shouldAcceptValidWhereValues() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setDefaultWhere("local");
        config.validate();
        config.setDefaultWhere("cloud");
        config.validate();
    }

    @Test
    void shouldRejectInvalidWhereValues() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setDefaultWhere("bogus");
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void shouldAcceptEnvironmentOverrides() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.getEnvironment(); // null by default
        config.setEnvironment(java.util.Collections.singletonMap("COMFY_API_KEY", "k"));
        config.validate();
        assertEquals("k", config.getEnvironment().get("COMFY_API_KEY"));
    }
}
