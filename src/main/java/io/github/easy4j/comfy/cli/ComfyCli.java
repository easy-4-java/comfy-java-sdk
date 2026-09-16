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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.easy4j.comfy.ComfyClientConfig;

/**
 * Maps every Java call onto a real {@code comfy} command line invocation.
 *
 * <p>Command surface mirrors the documented comfy CLI: setup, cloud auth,
 * ComfyUI lifecycle, the {@code generate} family, workflow/job/discovery
 * commands, skills management, and a raw escape hatch.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 * @see ComfyCliExecutor
 * @see ComfyCliResult
 */
public class ComfyCli {

    private final ComfyCliExecutor executor;
    private final ComfyClientConfig config;

    /**
     * Creates a new command mapper.
     *
     * @param config  runtime configuration providing the defaults.
     * @param executor subprocess executor the invocations are forwarded to.
     */
    public ComfyCli(ComfyClientConfig config, ComfyCliExecutor executor) {
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Returns the subprocess executor for advanced callers.
     *
     * @return the executor; never {@code null}.
     */
    public ComfyCliExecutor executor() {
        return executor;
    }

    // ============================================================
    // basic info
    // ============================================================

    /**
     * Runs {@code comfy --version}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult version() {
        return executor.execute("--version");
    }

    /**
     * Runs {@code comfy --help}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult help() {
        return executor.execute("--help");
    }

    /**
     * Runs {@code comfy --install-completion} (shell completion).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult installCompletion() {
        return executor.execute("--install-completion");
    }

    /**
     * Runs {@code comfy --json discover} (agent discovery mode).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult discoverJson() {
        return executor.execute("--json", "discover");
    }

    // ============================================================
    // setup / cloud auth
    // ============================================================

    /**
     * Runs {@code comfy setup} (interactive setup; the CLI prompts on its TTY).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult setup() {
        return executor.execute("setup");
    }

    /**
     * Runs {@code comfy setup -y} (non-interactive, for CI/scripts).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult setupYes() {
        return executor.execute("setup", "-y");
    }

    /**
     * Runs {@code comfy cloud login} (browser OAuth).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult cloudLogin() {
        return executor.execute("cloud", "login");
    }

    /**
     * Runs {@code comfy cloud whoami}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult cloudWhoami() {
        return executor.execute("cloud", "whoami");
    }

    /**
     * Runs {@code comfy set-default --where <where>} to persist routing.
     *
     * @param where {@code local} or {@code cloud}.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult setDefaultWhere(String where) {
        requireWhere(where);
        return executor.execute("set-default", "--where", where);
    }

    // ============================================================
    // local ComfyUI lifecycle
    // ============================================================

    /**
     * Runs {@code comfy install <args...>} (workspace installation).
     *
     * @param args extra flags forwarded to the installer.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult install(String... args) {
        return prefixed("install", args);
    }

    /**
     * Runs {@code comfy launch <args...>} (start local ComfyUI).
     *
     * @param args extra flags forwarded to the launcher.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult launch(String... args) {
        return prefixed("launch", args);
    }

    /**
     * Runs {@code comfy stop} (stop local ComfyUI).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult stop() {
        return executor.execute("stop");
    }

    /**
     * Runs {@code comfy update <args...>}.
     *
     * @param args extra flags forwarded to the updater.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult update(String... args) {
        return prefixed("update", args);
    }

    // ============================================================
    // generate
    // ============================================================

    /**
     * Runs {@code comfy generate <model>} with the given options.
     *
     * @param model   the generation model alias (e.g. {@code flux-pro}).
     * @param options the generation options; must not be {@code null}.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult generate(String model, GenerateOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(options, "options");
        List<String> args = new ArrayList<String>();
        args.add("generate");
        args.add(model);
        args.addAll(options.toArgs());
        String where = options.where != null ? options.where : config.getDefaultWhere();
        if (where != null) {
            args.add("--where");
            args.add(where);
        }
        return executor.execute(args.toArray(new String[0]));
    }

    /**
     * Runs {@code comfy generate list} with optional filters.
     *
     * @param category optional {@code --category} filter; may be {@code null}.
     * @param partner  optional {@code --partner} filter; may be {@code null}.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult generateList(String category, String partner) {
        List<String> args = new ArrayList<String>();
        args.add("generate");
        args.add("list");
        if (category != null) {
            args.add("--category");
            args.add(category);
        }
        if (partner != null) {
            args.add("--partner");
            args.add(partner);
        }
        return executor.execute(args.toArray(new String[0]));
    }

    /**
     * Runs {@code comfy generate schema <model>} (one model's parameters).
     *
     * @param model the model alias to introspect.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult generateSchema(String model) {
        return executor.execute("generate", "schema", model);
    }

    /**
     * Runs {@code comfy generate upload <file>} (prints a signed URL).
     *
     * @param file the local file to upload.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult generateUpload(String file) {
        return executor.execute("generate", "upload", file);
    }

    /**
     * Runs {@code comfy generate resume <model> <jobId>} with optional download.
     *
     * @param model    the model alias of the original job.
     * @param jobId    the async job id.
     * @param download optional {@code --download} target; may be {@code null}.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult generateResume(String model, String jobId, String download) {
        List<String> args = new ArrayList<String>();
        args.add("generate");
        args.add("resume");
        args.add(model);
        args.add(jobId);
        if (download != null) {
            args.add("--download");
            args.add(download);
        }
        return executor.execute(args.toArray(new String[0]));
    }

    // ============================================================
    // workflows / jobs / discovery
    // ============================================================

    /**
     * Runs {@code comfy run <args...>} (execute a workflow).
     *
     * @param args workflow invocation flags.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult run(String... args) {
        return prefixed("run", args);
    }

    /**
     * Runs {@code comfy jobs <args...>}.
     *
     * @param args job list/inspect flags.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult jobs(String... args) {
        return prefixed("jobs", args);
    }

    /**
     * Runs {@code comfy validate <args...>}.
     *
     * @param args validation flags.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult validate(String... args) {
        return prefixed("validate", args);
    }

    /**
     * Runs {@code comfy workflow <args...>}.
     *
     * @param args workflow management flags.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult workflow(String... args) {
        return prefixed("workflow", args);
    }

    /**
     * Runs {@code comfy templates <args...>}.
     *
     * @param args template flags.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult templates(String... args) {
        return prefixed("templates", args);
    }

    /**
     * Runs {@code comfy nodes <args...>}.
     *
     * @param args node discovery flags.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult nodes(String... args) {
        return prefixed("nodes", args);
    }

    /**
     * Runs {@code comfy models <args...>}.
     *
     * @param args model discovery flags.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult models(String... args) {
        return prefixed("models", args);
    }

    // ============================================================
    // skills
    // ============================================================

    /**
     * Runs {@code comfy skills install} (bundles agent skills).
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult skillsInstall() {
        return executor.execute("skills", "install");
    }

    /**
     * Runs {@code comfy skills list}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult skillsList() {
        return executor.execute("skills", "list");
    }

    /**
     * Runs {@code comfy skills status}.
     *
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult skillsStatus() {
        return executor.execute("skills", "status");
    }

    // ============================================================
    // passthrough
    // ============================================================

    /**
     * Runs an arbitrary {@code comfy} invocation; escape hatch for commands
     * the SDK does not model yet.
     *
     * @param args full argument list after the executable.
     * @return the raw CLI invocation result; never {@code null}.
     */
    public ComfyCliResult execute(String... args) {
        return executor.execute(args);
    }

    private ComfyCliResult prefixed(String prefix, String... args) {
        String[] all = new String[args.length + 1];
        all[0] = prefix;
        System.arraycopy(args, 0, all, 1, args.length);
        return executor.execute(all);
    }

    private static void requireWhere(String where) {
        if (!"local".equals(where) && !"cloud".equals(where)) {
            throw new IllegalArgumentException("where must be 'local' or 'cloud': " + where);
        }
    }

    /**
     * Fluent options for one {@code comfy generate <model>} run.
     */
    public static class GenerateOptions {

        private String prompt;
        private Integer width;
        private Integer height;
        private String download;
        private String image;
        private String mask;
        private String resolution;
        private Integer duration;
        private String aspectRatio;
        private String renderingSpeed;
        private boolean async;
        private boolean json;
        private String where;

        /**
         * Sets the {@code --prompt} flag.
         *
         * @param v the generation prompt.
         * @return this builder for chaining.
         */
        public GenerateOptions prompt(String v) { this.prompt = v; return this; }
        /**
         * Sets the {@code --width} flag.
         *
         * @param v image width in pixels.
         * @return this builder for chaining.
         */
        public GenerateOptions width(int v) { this.width = v; return this; }
        /**
         * Sets the {@code --height} flag.
         *
         * @param v image height in pixels.
         * @return this builder for chaining.
         */
        public GenerateOptions height(int v) { this.height = v; return this; }
        /**
         * Sets the {@code --download} flag.
         *
         * @param v local target path for the generated asset.
         * @return this builder for chaining.
         */
        public GenerateOptions download(String v) { this.download = v; return this; }
        /**
         * Sets the {@code --image} flag (input image; alias {@code --input_image}).
         *
         * @param v input image path.
         * @return this builder for chaining.
         */
        public GenerateOptions image(String v) { this.image = v; return this; }
        /**
         * Sets the {@code --mask} flag.
         *
         * @param v mask image path.
         * @return this builder for chaining.
         */
        public GenerateOptions mask(String v) { this.mask = v; return this; }
        /**
         * Sets the {@code --resolution} flag (e.g. {@code 1080p}).
         *
         * @param v video resolution preset.
         * @return this builder for chaining.
         */
        public GenerateOptions resolution(String v) { this.resolution = v; return this; }
        /**
         * Sets the {@code --duration} flag (seconds, video models).
         *
         * @param v duration in seconds.
         * @return this builder for chaining.
         */
        public GenerateOptions duration(int v) { this.duration = v; return this; }
        /**
         * Sets the {@code --aspect_ratio} flag (e.g. {@code 16:9}).
         *
         * @param v aspect ratio preset.
         * @return this builder for chaining.
         */
        public GenerateOptions aspectRatio(String v) { this.aspectRatio = v; return this; }
        /**
         * Sets the {@code --rendering_speed} flag.
         *
         * @param v rendering speed preset.
         * @return this builder for chaining.
         */
        public GenerateOptions renderingSpeed(String v) { this.renderingSpeed = v; return this; }
        /**
         * Sets the {@code --async} flag (returns a job id immediately).
         *
         * @param v {@code true} for asynchronous submission.
         * @return this builder for chaining.
         */
        public GenerateOptions async(boolean v) { this.async = v; return this; }
        /**
         * Sets the {@code --json} flag (machine-readable output).
         *
         * @param v {@code true} to emit JSON.
         * @return this builder for chaining.
         */
        public GenerateOptions json(boolean v) { this.json = v; return this; }
        /**
         * Overrides the routing for this run ({@code --where}).
         *
         * @param v {@code local} or {@code cloud}.
         * @return this builder for chaining.
         */
        public GenerateOptions where(String v) { this.where = v; return this; }

        /**
         * Materialises the configured options into flags (without routing —
         * the caller appends {@code --where} from config/override).
         *
         * @return the flag list.
         */
        List<String> toArgs() {
            List<String> args = new ArrayList<String>();
            if (prompt != null) { args.add("--prompt"); args.add(prompt); }
            if (width != null) { args.add("--width"); args.add(String.valueOf(width)); }
            if (height != null) { args.add("--height"); args.add(String.valueOf(height)); }
            if (download != null) { args.add("--download"); args.add(download); }
            if (image != null) { args.add("--image"); args.add(image); }
            if (mask != null) { args.add("--mask"); args.add(mask); }
            if (resolution != null) { args.add("--resolution"); args.add(resolution); }
            if (duration != null) { args.add("--duration"); args.add(String.valueOf(duration)); }
            if (aspectRatio != null) { args.add("--aspect_ratio"); args.add(aspectRatio); }
            if (renderingSpeed != null) { args.add("--rendering_speed"); args.add(renderingSpeed); }
            if (async) { args.add("--async"); }
            if (json) { args.add("--json"); }
            return args;
        }
    }
}
