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
        assertTrue(cli.skillsInstall("--scope", "project", "--dry-run").getStdout()
                .contains("skills install --scope project --dry-run"));
        assertTrue(cli.skillsUninstall("--scope", "project").getStdout()
                .contains("skills uninstall --scope project"));
        assertTrue(cli.skillsShow("comfy-debug").getStdout().contains("skills show comfy-debug"));
        assertTrue(cli.skillsStatus("user").getStdout().contains("skills status --scope user"));
        assertTrue(cli.skillsValidate("./my-skill").getStdout().contains("skills validate ./my-skill"));
        assertTrue(cli.runTemplate("image_basic", "--param", "prompt=cat").getStdout()
                .contains("run-template image_basic --param prompt=cat"));
        assertTrue(cli.preview("clip.mp4").getStdout().contains("preview clip.mp4"));
        assertTrue(cli.knowledge("status").getStdout().contains("knowledge status"));
        assertTrue(cli.manager("enable").getStdout().contains("manager enable"));
        assertTrue(cli.prCache("list").getStdout().contains("pr-cache list"));
        assertTrue(cli.codeSearch("KSampler").getStdout().contains("code-search KSampler"));
        assertTrue(cli.tracking("status").getStdout().contains("tracking status"));
        assertTrue(cli.auth("list").getStdout().contains("auth list"));
        assertTrue(cli.build("--help").getStdout().contains("build --help"));
        assertTrue(cli.deploy("--help").getStdout().contains("deploy --help"));
        assertTrue(cli.project("status").getStdout().contains("project status"));
        assertTrue(cli.assets("status").getStdout().contains("assets status"));
        assertTrue(cli.agent("--help").getStdout().contains("agent --help"));
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
        assertThrows(IllegalArgumentException.class,
                () -> cli().skillsStatus("workspace"));
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
