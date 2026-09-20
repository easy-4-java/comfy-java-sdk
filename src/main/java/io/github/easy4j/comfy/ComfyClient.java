/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy;

import java.util.Objects;

import io.github.easy4j.comfy.cli.ComfyCli;
import io.github.easy4j.comfy.cli.ComfyCliExecutor;
import io.github.easy4j.comfy.cli.ComfyCliResult;
import io.github.easy4j.comfy.model.ComfyCliEnvelope;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * High-level Java facade for the local {@code comfy} CLI route.
 *
 * <p>The lower-level {@link ComfyCli} mirrors the CLI command tree; this class
 * adds parsed JSON helpers while retaining access to the raw mapper.</p>
 */
public class ComfyClient implements AutoCloseable {

    private static final ObjectMapper MAPPER =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

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
    public ComfyCliResult cloudLogin() { return cli.cloudLogin(); }
    public ComfyCliResult setup() { return cli.setupYes(); }
    public ComfyCliResult skillsInstall() { return cli.skillsInstall(); }

    /**
     * Runs partner generation with command-level JSON output without mutating
     * the caller's reusable options object.
     */
    public JsonNode generateJson(String model, ComfyCli.GenerateOptions options) {
        Objects.requireNonNull(options, "options");
        ComfyCliResult result = cli.generate(model, options.copy().json(true));
        requireSuccess(result, "comfy generate");
        try {
            return MAPPER.readTree(result.getStdout());
        } catch (Exception e) {
            throw new ComfyException("comfy generate --json printed non-JSON output", e);
        }
    }

    /** Executes any CLI command using the global uniform {@code --json} envelope. */
    public ComfyCliEnvelope executeJson(String... args) {
        ComfyCliResult result = cli.executeJson(args);
        requireSuccess(result, "comfy --json");
        if (result.isTruncated()) {
            throw new ComfyException("comfy --json output exceeded maxOutputBytes="
                    + config.getMaxOutputBytes());
        }
        try {
            return MAPPER.readValue(result.getStdout(), ComfyCliEnvelope.class);
        } catch (Exception e) {
            throw new ComfyException("comfy --json printed an invalid envelope", e);
        }
    }

    public ComfyCliEnvelope environment() { return executeJson("env"); }
    public ComfyCliEnvelope whichJson() { return executeJson("which"); }
    public ComfyCliEnvelope discover() { return executeJson("discover"); }

    public ComfyCli cli() { return cli; }
    public ComfyClientConfig getConfig() { return config; }

    private static void requireSuccess(ComfyCliResult result, String operation) {
        if (!result.isSuccess()) {
            throw new ComfyException(operation + " failed: exit=" + result.getExitCode()
                    + " stderr=" + result.getStderr());
        }
    }

    @Override
    public void close() {
        // CLI route owns no persistent subprocess or executor.
    }
}
