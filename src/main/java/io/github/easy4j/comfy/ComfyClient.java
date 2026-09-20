/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.github.easy4j.comfy.cli.ComfyCli;
import io.github.easy4j.comfy.cli.ComfyCliExecutor;
import io.github.easy4j.comfy.cli.ComfyCliResult;
import io.github.easy4j.comfy.model.ComfyDoctorReport;
import io.github.easy4j.comfy.model.ComfyJsonEnvelope;

/**
 * High-level facade over the local first-party {@code comfy} CLI.
 *
 * <p>Advanced and newly-added CLI commands remain reachable through
 * {@link #cli()} and its raw execute escape hatch. Local MCP is exposed by
 * {@code io.github.easy4j.comfy.mcp.ComfyMcpClient}.</p>
 */
public class ComfyClient implements AutoCloseable {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final ComfyClientConfig config;
    private final ComfyCli cli;

    public ComfyClient(ComfyClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.config.validate();
        this.cli = new ComfyCli(this.config, new ComfyCliExecutor(this.config));
    }

    public ComfyClient(ComfyClientConfig config, ComfyCli cli) {
        this.config = Objects.requireNonNull(config, "config");
        this.config.validate();
        this.cli = Objects.requireNonNull(cli, "cli");
    }

    public ComfyCliResult version() { return cli.version(); }
    public ComfyCliResult help() { return cli.help(); }
    public boolean isAvailable() { return cli.executor().probe(); }

    /**
     * Runs generate with machine-readable output without mutating caller-owned
     * options.
     */
    public JsonNode generateJson(String model, ComfyCli.GenerateOptions options) {
        Objects.requireNonNull(options, "options");
        ComfyCli.GenerateOptions jsonOptions = new ComfyCli.GenerateOptions(options).json(true);
        ComfyCliResult result = cli.generate(model, jsonOptions);
        if (!result.isSuccess()) {
            throw new ComfyException("comfy generate failed: exit=" + result.getExitCode()
                    + " stderr=" + result.getStderr());
        }
        return parseJson(result.getStdout(), "comfy generate --json");
    }

    /** Runs the CLI's self-describing discovery contract. */
    public ComfyJsonEnvelope discover() {
        return requireSuccessfulEnvelope(cli.discoverJson(), "comfy --json discover");
    }

    /** Runs {@code comfy --json which}. */
    public ComfyJsonEnvelope which() {
        return requireSuccessfulEnvelope(cli.whichJson(), "comfy --json which");
    }

    /** Runs {@code comfy --json env}. */
    public ComfyJsonEnvelope environment() {
        return requireSuccessfulEnvelope(cli.envJson(), "comfy --json env");
    }

    /**
     * Performs a bounded readiness check using only local CLI calls. No
     * environment values or credentials are copied into the report.
     */
    public ComfyDoctorReport doctor() {
        boolean available = isAvailable();
        ComfyCliResult versionResult = available ? cli.version() : new ComfyCliResult(-1, "", "comfy unavailable");
        boolean versionHealthy = versionResult.isSuccess() && !versionResult.getStdout().trim().isEmpty();
        String version = versionHealthy ? versionResult.getStdout().trim() : null;

        ComfyJsonEnvelope which = null;
        ComfyJsonEnvelope env = null;
        ComfyJsonEnvelope discovery = null;
        if (available) {
            try { which = requireSuccessfulEnvelope(cli.whichJson(), "comfy --json which"); }
            catch (RuntimeException ignored) { }
            try { env = requireSuccessfulEnvelope(cli.envJson(), "comfy --json env"); }
            catch (RuntimeException ignored) { }
            try { discovery = requireSuccessfulEnvelope(cli.discoverJson(), "comfy --json discover"); }
            catch (RuntimeException ignored) { }
        }

        String workspace = null;
        boolean workspaceResolved = false;
        if (which != null && which.getData() != null) {
            JsonNode path = which.getData().path("workspace_path");
            if (!path.isMissingNode() && !path.isNull() && !path.asText().trim().isEmpty()) {
                workspace = path.asText();
                workspaceResolved = true;
            }
        }

        boolean environmentHealthy = env != null && env.isOk();
        boolean discoveryHealthy = discovery != null && discovery.isOk();
        String summary = "cli=" + available
                + ", version=" + versionHealthy
                + ", workspace=" + workspaceResolved
                + ", env=" + environmentHealthy
                + ", discover=" + discoveryHealthy;
        return new ComfyDoctorReport(available, versionHealthy, workspaceResolved,
                environmentHealthy, discoveryHealthy, version, workspace, summary);
    }

    public ComfyCliResult cloudLogin() { return cli.cloudLogin(); }
    public ComfyCliResult setup() { return cli.setupYes(); }
    public ComfyCliResult skillsInstall() { return cli.skillsInstall(); }

    public ComfyCli cli() { return cli; }
    public ComfyClientConfig getConfig() { return config; }

    public ComfyJsonEnvelope parseEnvelope(String stdout) {
        JsonNode root = parseJson(stdout, "comfy --json");
        if (!root.isObject()) {
            throw new ComfyException("comfy --json returned a non-object envelope");
        }
        return new ComfyJsonEnvelope(
                root.path("ok").asBoolean(false),
                textOrNull(root, "command"),
                textOrNull(root, "version"),
                textOrNull(root, "where"),
                root.path("data"),
                root.path("error"),
                root);
    }

    private ComfyJsonEnvelope requireSuccessfulEnvelope(ComfyCliResult result, String command) {
        if (!result.isSuccess()) {
            throw new ComfyException(command + " failed: exit=" + result.getExitCode()
                    + " stderr=" + result.getStderr());
        }
        ComfyJsonEnvelope envelope = parseEnvelope(result.getStdout());
        if (!envelope.isOk()) {
            throw new ComfyException(command + " reported ok=false"
                    + (envelope.getErrorHint() == null ? "" : ": " + envelope.getErrorHint()));
        }
        return envelope;
    }

    private static JsonNode parseJson(String stdout, String command) {
        try {
            return MAPPER.readTree(stdout);
        } catch (Exception e) {
            throw new ComfyException(command + " printed non-JSON output", e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    @Override
    public void close() {
        // CLI route owns no persistent resource; each invocation is process-scoped.
    }
}
