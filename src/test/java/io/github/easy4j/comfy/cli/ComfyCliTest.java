/*
 * Copyright (c) 2018-present, easy-4-java.
 */
package io.github.easy4j.comfy.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClientConfig;

class ComfyCliTest {

    private static final String ECHO =
            Paths.get("src", "test", "resources", "comfy-echo.sh").toAbsolutePath().toString();

    private static ComfyCli cli() {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(ECHO);
        config.setLocalTimeoutSeconds(2);
        return new ComfyCli(config, new ComfyCliExecutor(config));
    }

    @Test
    void shouldMapGlobalAndEnvironmentCommands() {
        assertTrue(cli().discoverJson().getStdout().contains("--json discover"));
        assertTrue(cli().executeJsonStream("jobs", "watch", "p1").getStdout()
                .contains("--json-stream jobs watch p1"));
        assertTrue(cli().which().getStdout().contains("which"));
        assertTrue(cli().env().getStdout().contains("env"));
        assertTrue(cli().outdated().getStdout().contains("outdated"));
        assertTrue(cli().systemStats().getStdout().contains("system-stats"));
        assertTrue(cli().freeMemory().getStdout().contains("free-memory"));
    }

    @Test
    void shouldMapSetupCloudAndRouting() {
        assertTrue(cli().setupYes().getStdout().contains("setup -y"));
        assertTrue(cli().cloudLoginNoBrowser().getStdout().contains("cloud login --no-browser"));
        assertTrue(cli().cloudWhoami().getStdout().contains("cloud whoami"));
        assertTrue(cli().cloudLogout().getStdout().contains("cloud logout"));
        assertTrue(cli().cloudStatus().getStdout().contains("cloud status"));
        assertTrue(cli().cloudSetBaseUrl("https://example.test").getStdout()
                .contains("cloud set-base-url https://example.test"));
        assertTrue(cli().setDefaultWhere("local").getStdout().contains("set-default --where local"));
        assertThrows(IllegalArgumentException.class, () -> cli().setDefaultWhere("bogus"));
    }

    @Test
    void shouldMapLifecycle() {
        assertTrue(cli().install("--here").getStdout().contains("install --here"));
        assertTrue(cli().launchBackground("--port", "8188").getStdout()
                .contains("launch --background --port 8188"));
        assertTrue(cli().stop().getStdout().contains("stop"));
        assertTrue(cli().update("comfy").getStdout().contains("update comfy"));
        assertTrue(cli().logs("--tail", "20").getStdout().contains("logs --tail 20"));
    }

    @Test
    void shouldBuildGenerationAndValidateDynamicOptions() {
        ComfyCli.GenerateOptions options = new ComfyCli.GenerateOptions()
                .prompt("a cat")
                .width(1024).height(768)
                .duration(5)
                .timeoutSeconds(90)
                .where("cloud")
                .option("seed", 42)
                .flag("enhance-prompt");

        String out = cli().generate("flux-pro", options).getStdout();
        assertTrue(out.contains("generate flux-pro"));
        assertTrue(out.contains("--prompt a cat"));
        assertTrue(out.contains("--width 1024"));
        assertTrue(out.contains("--timeout 90"));
        assertTrue(out.contains("--seed 42"));
        assertTrue(out.contains("--enhance-prompt"));
        assertTrue(out.contains("--where cloud"));

        assertThrows(IllegalArgumentException.class, () -> new ComfyCli.GenerateOptions().where("x"));
        assertThrows(IllegalArgumentException.class, () -> new ComfyCli.GenerateOptions().option("bad flag", 1));
    }

    @Test
    void shouldMapWorkflowJobAndTemplateCommands() {
        assertTrue(cli().runWorkflow("wf.json", true).getStdout()
                .contains("run --workflow wf.json --wait"));
        assertTrue(cli().jobsList().getStdout().contains("jobs ls"));
        assertTrue(cli().jobStatus("p1").getStdout().contains("jobs status p1"));
        assertTrue(cli().jobsWait("p1", "p2").getStdout().contains("jobs wait p1 p2"));
        assertTrue(cli().jobCancel("p1").getStdout().contains("jobs cancel p1"));
        assertTrue(cli().validateWorkflow("wf.json").getStdout()
                .contains("validate --workflow wf.json"));
        assertTrue(cli().templatesList("image", "Text to Image").getStdout()
                .contains("templates ls --type image --tag Text to Image"));
        assertTrue(cli().templateFetch("basic", "out.json").getStdout()
                .contains("templates fetch basic --out out.json"));
    }

    @Test
    void shouldMapWorkflowEditing() {
        assertTrue(cli().workflowSlots("wf.json").getStdout().contains("workflow slots wf.json"));
        assertTrue(cli().workflowSetSlot("wf.json", "6.text=a fox").getStdout()
                .contains("workflow set-slot wf.json 6.text=a fox"));
        assertTrue(cli().workflowVary("wf.json", "variants", "6.seed=[1,2]").getStdout()
                .contains("workflow vary wf.json --slot 6.seed=[1,2] --out-dir variants"));
        assertTrue(cli().workflowList().getStdout().contains("workflow list"));
        assertTrue(cli().workflowGet("id1", "wf.json").getStdout()
                .contains("workflow get id1 --out wf.json"));
        assertTrue(cli().workflowSave("wf.json", "My Flow").getStdout()
                .contains("workflow save wf.json --name My Flow"));
        assertTrue(cli().workflowDelete("id1").getStdout().contains("workflow delete id1"));
        assertTrue(cli().workflowCompose("pipe.yaml", "wf.json").getStdout()
                .contains("workflow compose pipe.yaml -o wf.json"));
        assertTrue(cli().workflowDecompose("wf.json").getStdout()
                .contains("workflow decompose wf.json"));
    }

    @Test
    void shouldMapDiscoveryAssetsAndManagementFamilies() {
        assertTrue(cli().nodesSearch("checkpoint").getStdout().contains("nodes search checkpoint"));
        assertTrue(cli().nodeShow("KSampler").getStdout().contains("nodes show KSampler"));
        assertTrue(cli().modelFolders().getStdout().contains("models list-folders"));
        assertTrue(cli().modelsSearch("wan", "lora").getStdout()
                .contains("models search --text wan --type lora"));
        assertTrue(cli().nodeInstall("pack").getStdout().contains("node install pack"));
        assertTrue(cli().modelDownload("https://example/model", "models/checkpoints").getStdout()
                .contains("model download --url https://example/model --relative-path models/checkpoints"));
        assertTrue(cli().upload("a.png", "b.png").getStdout().contains("upload a.png b.png"));
        assertTrue(cli().download("p1", "-o", "outputs").getStdout()
                .contains("download p1 -o outputs"));
    }

    @Test
    void shouldMapSkillsAndTrackingAndKeepEscapeHatch() {
        assertTrue(cli().skillsInstall().getStdout().contains("skills install"));
        assertTrue(cli().skillsList().getStdout().contains("skills list"));
        assertTrue(cli().skillsStatus().getStdout().contains("skills status"));
        assertTrue(cli().trackingDisable().getStdout().contains("tracking disable"));
        assertTrue(cli().trackingEnable().getStdout().contains("tracking enable"));
        assertTrue(cli().execute("future-command", "--x").getStdout()
                .contains("future-command --x"));
    }
}
