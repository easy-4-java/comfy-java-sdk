/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClientConfig;

class ComfyCliParityTest {

    private static final String ECHO =
            Paths.get("src", "test", "resources", "comfy-echo.sh").toAbsolutePath().toString();

    private static ComfyCli cli() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(ECHO);
        config.setLocalTimeoutSeconds(2);
        return new ComfyCli(config, new ComfyCliExecutor(config));
    }

    @Test
    void shouldMapStableCliFamilies() {
        ComfyCli cli = cli();
        assertTrue(cli.helpJson().getStdout().contains("--help-json"));
        assertTrue(cli.setup(new ComfyCli.SetupOptions().where("local").nonInteractive(true)
                .skipSkills(true).skipVerify(true)).getStdout()
                .contains("setup --where local --non-interactive --skip-skills --skip-verify"));
        assertTrue(cli.cloudLogout().getStdout().contains("cloud logout"));
        assertTrue(cli.launchBackground("--port", "8188").getStdout()
                .contains("launch --background -- --port 8188"));
        assertTrue(cli.jobsList().getStdout().contains("jobs ls"));
        assertTrue(cli.jobStatus("p1").getStdout().contains("jobs status p1"));
        assertTrue(cli.jobsWait("p1", "p2").getStdout().contains("jobs wait p1 p2"));
        assertTrue(cli.validateWorkflow("wf.json").getStdout().contains("validate --workflow wf.json"));
        assertTrue(cli.templateFetch("image_flux", "wf.json").getStdout()
                .contains("templates fetch image_flux --out wf.json"));
        assertTrue(cli.workflowSlots("wf.json").getStdout().contains("workflow slots wf.json"));
        assertTrue(cli.nodesSearch("ksampler").getStdout().contains("nodes search ksampler"));
        assertTrue(cli.modelsSearch("sdxl base").getStdout().contains("model search --text sdxl base"));
        assertTrue(cli.upload("a.png", "b.png").getStdout().contains("upload a.png b.png"));
        assertTrue(cli.download("p1", "out", true).getStdout().contains("download p1 -o out --url-only"));
    }

    @Test
    void shouldSupportGenerateSchemaDriftWithoutRawShell() {
        ComfyCliResult result = cli().generate("partner-model",
                new ComfyCli.GenerateOptions()
                        .prompt("cat")
                        .seed(42)
                        .timeoutSeconds(60)
                        .param("negative_prompt", "blur")
                        .param("guidance_scale", 3.5)
                        .where("cloud"));
        String out = result.getStdout();
        assertTrue(out.contains("--negative_prompt blur"));
        assertTrue(out.contains("--guidance_scale 3.5"));
        assertTrue(out.contains("--seed 42"));
        assertTrue(out.contains("--timeout 60"));
        assertTrue(out.contains("--where cloud"));
    }

    @Test
    void shouldValidateRoutingAndDynamicParameterNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComfyCli.GenerateOptions().where("edge"));
        assertThrows(IllegalArgumentException.class,
                () -> new ComfyCli.GenerateOptions().param("--json", "x"));
    }

    @Test
    void copiedGenerateOptionsMustBeIndependent() {
        ComfyCli.GenerateOptions original = new ComfyCli.GenerateOptions().prompt("cat").param("quality", "high");
        ComfyCli.GenerateOptions copy = new ComfyCli.GenerateOptions(original).json(true).param("quality", "low");
        assertFalse(original.toArgs().contains("--json"));
        assertTrue(copy.toArgs().contains("--json"));
        assertEquals("high", original.toArgs().get(original.toArgs().indexOf("--quality") + 1));
        assertEquals("low", copy.toArgs().get(copy.toArgs().indexOf("--quality") + 1));
    }
}
