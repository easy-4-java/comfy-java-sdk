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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClientConfig;

/**
 * Contract tests for {@link ComfyCli} driven without a socket: outgoing
 * argument lists are verified through the echo fixture.
 *
 * @since 1.0.0
 */
class ComfyCliTest {

    /** Absolute path of the argument-echoing fixture script (surefire runs from the module base dir). */
    private static final String ECHO =
            java.nio.file.Paths.get("src", "test", "resources", "comfy-echo.sh").toAbsolutePath().toString();

    private static ComfyClientConfig echoConfig() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(ECHO);
        config.setLocalTimeoutSeconds(2);
        return config;
    }

    private static ComfyCli echoCli() {
        return new ComfyCli(echoConfig(), new ComfyCliExecutor(echoConfig()));
    }

    @Test
    void shouldExposeExecutor() {
        assertNotNull(echoCli().executor());
    }

    @Test
    void shouldDelegateVersionAndHelp() {
        assertTrue(echoCli().version().getStdout().contains("--version"));
        assertTrue(echoCli().help().getStdout().contains("--help"));
        assertTrue(echoCli().installCompletion().getStdout().contains("--install-completion"));
        assertTrue(echoCli().discoverJson().getStdout().contains("--json discover"));
    }

    @Test
    void shouldDelegateSetupAndCloudAuth() {
        assertTrue(echoCli().setup().getStdout().contains("setup"));
        assertTrue(echoCli().setupYes().getStdout().contains("-y"));
        assertTrue(echoCli().cloudLogin().getStdout().contains("cloud login"));
        assertTrue(echoCli().cloudWhoami().getStdout().contains("cloud whoami"));
    }

    @Test
    void shouldDelegateSetDefaultWhere() {
        assertTrue(echoCli().setDefaultWhere("cloud").getStdout().contains("--where cloud"));
        assertThrows(IllegalArgumentException.class, () -> echoCli().setDefaultWhere("bogus"));
    }

    @Test
    void shouldDelegateComfyUiLifecycle() {
        assertTrue(echoCli().install("--here").getStdout().contains("install --here"));
        assertTrue(echoCli().launch("--port", "8188").getStdout().contains("launch --port 8188"));
        assertTrue(echoCli().stop().getStdout().contains("stop"));
        assertTrue(echoCli().update().getStdout().contains("update"));
    }

    @Test
    void shouldBuildGenerateWithAllFlags() {
        ComfyCli.GenerateOptions options = new ComfyCli.GenerateOptions()
                .prompt("a cat on the moon")
                .width(1024).height(1024)
                .download("cat.png")
                .image("in.png").mask("mask.png")
                .resolution("1080p").duration(5).aspectRatio("16:9")
                .renderingSpeed("quality")
                .async(true).json(true)
                .where("cloud");

        ComfyCliResult result = echoCli().generate("flux-pro", options);
        String out = result.getStdout();
        assertTrue(out.contains("generate flux-pro"));
        assertTrue(out.contains("--prompt a cat on the moon"));
        assertTrue(out.contains("--width 1024"));
        assertTrue(out.contains("--height 1024"));
        assertTrue(out.contains("--download cat.png"));
        assertTrue(out.contains("--image in.png"));
        assertTrue(out.contains("--mask mask.png"));
        assertTrue(out.contains("--resolution 1080p"));
        assertTrue(out.contains("--duration 5"));
        assertTrue(out.contains("--aspect_ratio 16:9"));
        assertTrue(out.contains("--rendering_speed quality"));
        assertTrue(out.contains("--async"));
        assertTrue(out.contains("--json"));
        assertTrue(out.contains("--where cloud"), "explicit where must be appended");
    }

    @Test
    void shouldPropagateConfigDefaultWhere() {
        ComfyClientConfig config = echoConfig();
        config.setDefaultWhere("local");
        ComfyCliResult result = new ComfyCli(config, new ComfyCliExecutor(config))
                .generate("seedance", new ComfyCli.GenerateOptions().prompt("hi").where(null));
        assertTrue(result.getStdout().contains("--where local"));
    }

    @Test
    void shouldRejectNullModelAndOptions() {
        assertThrows(NullPointerException.class, () -> echoCli().generate(null, new ComfyCli.GenerateOptions()));
        assertThrows(NullPointerException.class, () -> echoCli().generate("flux-pro", null));
    }

    @Test
    void shouldDelegateGenerateVariants() {
        assertTrue(echoCli().generateList("text-to-video", "kling").getStdout()
                .contains("--category text-to-video --partner kling"));
        assertTrue(echoCli().generateSchema("flux-kontext").getStdout().contains("schema flux-kontext"));
        assertTrue(echoCli().generateUpload("in.png").getStdout().contains("upload in.png"));
        String resume = echoCli().generateResume("luma", "job-1", "out.mp4").getStdout();
        assertTrue(resume.contains("resume luma job-1 --download out.mp4"));
    }

    @Test
    void shouldDelegateWorkflowAndDiscovery() {
        assertTrue(echoCli().run("--workflow", "a.json").getStdout().contains("run --workflow a.json"));
        assertTrue(echoCli().jobs().getStdout().contains("jobs"));
        assertTrue(echoCli().validate("wf.json").getStdout().contains("validate wf.json"));
        assertTrue(echoCli().workflow("list").getStdout().contains("workflow list"));
        assertTrue(echoCli().templates().getStdout().contains("templates"));
        assertTrue(echoCli().nodes("search").getStdout().contains("nodes search"));
        assertTrue(echoCli().models("list").getStdout().contains("models list"));
    }

    @Test
    void shouldDelegateSkills() {
        assertTrue(echoCli().skillsInstall().getStdout().contains("skills install"));
        assertTrue(echoCli().skillsList().getStdout().contains("skills list"));
        assertTrue(echoCli().skillsStatus().getStdout().contains("skills status"));
    }

    @Test
    void shouldDelegateRawExecute() {
        assertTrue(echoCli().execute("--version").getStdout().contains("--version"));
    }
}
