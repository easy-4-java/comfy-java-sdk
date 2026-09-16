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

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.easy4j.comfy.cli.ComfyCli;
import io.github.easy4j.comfy.cli.ComfyCliExecutor;
import io.github.easy4j.comfy.cli.ComfyCliResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * High-level Java facade that wraps every local {@code comfy} CLI invocation
 * behind ergonomic, strongly-typed methods.
 *
 * <p>This class is the recommended entry point for the CLI route. It owns a
 * single {@link ComfyClientConfig} and a single {@link ComfyCli}, forwarding
 * the configured defaults to every call. For the MCP route (spawn
 * {@code comfy-mcp} and speak JSON-RPC over stdio) use
 * {@code io.github.easy4j.comfy.mcp.ComfyMcpClient}.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyClientConfig
 * @see ComfyCli
 */
public class ComfyClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ComfyClient.class);
    private static final JsonMapper MAPPER = new JsonMapper();

    private final ComfyClientConfig config;
    private final ComfyCli cli;

    /**
     * Creates a new client backed by the given configuration. A default
     * {@link ComfyCli} and {@link ComfyCliExecutor} are constructed
     * automatically.
     *
     * @param config runtime configuration; must not be {@code null}.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    public ComfyClient(ComfyClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.config.validate();
        this.cli = new ComfyCli(this.config, new ComfyCliExecutor(this.config));
    }

    /**
     * Creates a new client that delegates to the supplied {@link ComfyCli}.
     *
     * <p>This constructor exists primarily for testing &mdash; it lets a
     * caller substitute a {@link ComfyCli} backed by a mocked executor while
     * still using the default behaviour of the surrounding facade.</p>
     *
     * @param config runtime configuration; must not be {@code null}.
     * @param cli    the CLI facade to delegate to; must not be {@code null}.
     * @throws NullPointerException if either argument is {@code null}.
     */
    public ComfyClient(ComfyClientConfig config, ComfyCli cli) {
        this.config = Objects.requireNonNull(config, "config");
        this.cli = Objects.requireNonNull(cli, "cli");
    }

    /**
     * Runs {@code comfy --version}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult version() {
        return cli.version();
    }

    /**
     * Runs {@code comfy --help}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult help() {
        return cli.help();
    }

    /**
     * Probes CLI availability with {@code comfy --version} and the configured
     * probe timeout.
     *
     * @return {@code true} when the local CLI is reachable.
     */
    public boolean isAvailable() {
        return cli.executor().probe();
    }

    /**
     * Sends a generation request ({@code comfy generate <model>}) with
     * {@code --json} so the standard output can be parsed as JSON.
     *
     * @param model   the generation model alias.
     * @param options the generation options; must not be {@code null}.
     * @return the parsed JSON root of the {@code --json} output; never
     *         {@code null}.
     * @throws ComfyException when the invocation fails or prints non-JSON.
     */
    public JsonNode generateJson(String model, ComfyCli.GenerateOptions options) {
        ComfyCli.GenerateOptions jsonOptions = options.json(true);
        ComfyCliResult result = cli.generate(model, jsonOptions);
        if (!result.isSuccess()) {
            throw new ComfyException("comfy generate failed: exit=" + result.getExitCode()
                    + " stderr=" + result.getStderr());
        }
        try {
            return MAPPER.readTree(result.getStdout());
        } catch (Exception e) {
            throw new ComfyException("comfy generate --json printed non-JSON output", e);
        }
    }

    /**
     * Runs {@code comfy cloud login} (browser OAuth).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult cloudLogin() {
        return cli.cloudLogin();
    }

    /**
     * Runs {@code comfy setup -y} (non-interactive setup).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult setup() {
        return cli.setupYes();
    }

    /**
     * Runs {@code comfy skills install}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult skillsInstall() {
        return cli.skillsInstall();
    }

    /**
     * Returns the underlying {@link ComfyCli} for advanced callers.
     *
     * @return the CLI facade backing this client; never {@code null}.
     */
    public ComfyCli cli() {
        return cli;
    }

    /**
     * Returns the runtime configuration used by this client.
     *
     * @return the configuration; never {@code null}.
     */
    public ComfyClientConfig getConfig() {
        return config;
    }

    /**
     * Closes this client. The default implementation is a no-op because the
     * underlying {@link ComfyCliExecutor} does not hold any long-lived
     * resources.
     */
    @Override
    public void close() {
    }
}
