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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ComfyCliResult} semantics. @since 1.0.0 */
class ComfyCliResultTest {

    @Test
    void shouldTreatZeroExitAsSuccess() {
        ComfyCliResult result = new ComfyCliResult(0, "out", "");
        assertTrue(result.isSuccess());
        assertFalse(result.isTimeout());
        assertEquals("out", result.getStdout());
    }

    @Test
    void shouldTreatNonZeroExitAsFailureButPreserveCode() {
        ComfyCliResult result = new ComfyCliResult(7, "out", "err");
        assertFalse(result.isSuccess());
        assertEquals(7, result.getExitCode());
        assertFalse(result.isTimeout());
    }

    @Test
    void shouldDetectTimeoutByNotice() {
        ComfyCliResult result = new ComfyCliResult(-1, "", "comfy CLI timed out after 600000 ms\n");
        assertFalse(result.isSuccess());
        assertTrue(result.isTimeout());
    }

    @Test
    void shouldNotFlagOtherNegativeExitsAsTimeout() {
        ComfyCliResult result = new ComfyCliResult(-1, "", "some other failure");
        assertFalse(result.isTimeout());
    }
}
