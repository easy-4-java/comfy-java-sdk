/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.easy4j.comfy.ComfyClientConfig;

/**
 * Typed command mapper for the first-party {@code comfy} CLI.
 *
 * <p>The surface models stable command families while retaining
 * {@link #execute(String...)} as a forward-compatible escape hatch for beta
 * flags and newly-added commands.</p>
 */
public class ComfyCli {

    private final ComfyCliExecutor executor;
    private final ComfyCliStreamExecutor streamExecutor;
    private final ComfyClientConfig config;

    public ComfyCli(ComfyClientConfig config, ComfyCliExecutor executor) {
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.streamExecutor = new ComfyCliStreamExecutor(config);
    }

    public ComfyCliExecutor executor() { return executor; }
    public ComfyCliStreamExecutor streamExecutor() { return streamExecutor; }
    public ComfyCliStreamSession stream(ComfyCliStreamListener listener, String... args) {
        return streamExecutor.execute(listener, args);
    }
    public ComfyCliStreamSession runStream(RunOptions options, ComfyCliStreamListener listener) {
        Objects.requireNonNull(options, "options");
        return streamExecutor.execute(listener,
                options.toArgs(config.getDefaultWhere()).toArray(new String[0]));
    }

    public ComfyCliResult version() { return executor.execute("--version"); }
    public ComfyCliResult help() { return executor.execute("--help"); }
    public ComfyCliResult helpJson() { return executor.execute("--help-json"); }
    public ComfyCliResult installCompletion() { return executor.execute("--install-completion"); }
    public ComfyCliResult discoverJson() { return executor.execute("--json", "discover"); }
    public ComfyCliResult whichJson() { return executor.execute("--json", "which"); }
    public ComfyCliResult envJson() { return executor.execute("--json", "env"); }

    public ComfyCliResult setup() { return executor.execute("setup"); }
    public ComfyCliResult setupYes() { return executor.execute("setup", "-y"); }
    public ComfyCliResult setup(SetupOptions options) {
        Objects.requireNonNull(options, "options");
        return executor.execute(options.toArgs().toArray(new String[0]));
    }
    public ComfyCliResult cloudLogin() { return executor.execute("cloud", "login"); }
    public ComfyCliResult cloudWhoami() { return executor.execute("cloud", "whoami"); }
    public ComfyCliResult cloudLogout() { return executor.execute("cloud", "logout"); }
    public ComfyCliResult cloudSetBaseUrl(String url) {
        return executor.execute("cloud", "set-base-url", requireNonBlank("url", url));
    }
    public ComfyCliResult setDefaultWhere(String where) {
        return executor.execute("set-default", "--where", requireWhere(where));
    }

    public ComfyCliResult install(String... args) { return prefixed("install", args); }
    public ComfyCliResult launch(String... args) { return prefixed("launch", args); }
    public ComfyCliResult launchBackground(String... extraArgs) {
        List<String> args = new ArrayList<String>();
        args.add("launch");
        args.add("--background");
        if (extraArgs != null && extraArgs.length > 0) {
            args.add("--");
            addNonNull(args, extraArgs);
        }
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult stop() { return executor.execute("stop"); }
    public ComfyCliResult update(String... args) { return prefixed("update", args); }
    public ComfyCliResult which() { return executor.execute("which"); }
    public ComfyCliResult env() { return executor.execute("env"); }


    public ComfyCliResult outdated(String... args) { return prefixed("outdated", args); }
    public ComfyCliResult logs(String... args) { return prefixed("logs", args); }
    public ComfyCliResult systemStats(String... args) { return prefixed("system-stats", args); }
    public ComfyCliResult free(String... args) { return prefixed("free", args); }
    public ComfyCliResult runCli(String... args) { return prefixed("run-cli", args); }
    public ComfyCliResult agentReview(String... args) { return prefixed("agent-review", args); }
    public ComfyCliResult dependency(String... args) { return prefixed("dependency", args); }
    public ComfyCliResult cloudStatus(String... args) { return prefixed2("cloud", "status", args); }
    public ComfyCliResult cloudLogin(String... args) { return prefixed2("cloud", "login", args); }
    public ComfyCliResult cloudSetKey(String key) {
        return executor.execute("cloud", "set-key", "--key", requireNonBlank("key", key));
    }
    public ComfyCliResult cloudClearBaseUrl() {
        return executor.execute("cloud", "set-base-url", "--clear");
    }
    public ComfyCliResult setDefault(String... args) { return prefixed("set-default", args); }

    public ComfyCliResult generate(String model, GenerateOptions options) {
        Objects.requireNonNull(model, "model");
        requireNonBlank("model", model);
        Objects.requireNonNull(options, "options");
        List<String> args = new ArrayList<String>();
        args.add("generate");
        args.add(model);
        args.addAll(options.toArgs());
        String where = options.where != null ? options.where : config.getDefaultWhere();
        if (where != null) {
            args.add("--where");
            args.add(requireWhere(where));
        }
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult generateList(String category, String partner) {
        return generateList(null, category, partner);
    }
    public ComfyCliResult generateList(String query, String category, String partner) {
        List<String> args = words("generate", "list");
        option(args, "--query", query);
        option(args, "--category", category);
        option(args, "--partner", partner);
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult generateSchema(String model) {
        return executor.execute("generate", "schema", requireNonBlank("model", model));
    }
    public ComfyCliResult generateRefresh() { return executor.execute("generate", "refresh"); }
    public ComfyCliResult generateUpload(String file) {
        return executor.execute("generate", "upload", requireNonBlank("file", file));
    }
    public ComfyCliResult generateResume(String model, String jobId, String download) {
        List<String> args = words("generate", "resume",
                requireNonBlank("model", model), requireNonBlank("jobId", jobId));
        option(args, "--download", download);
        return executor.execute(args.toArray(new String[0]));
    }

    public ComfyCliResult run(String... args) { return prefixed("run", args); }
    public ComfyCliResult run(RunOptions options) {
        Objects.requireNonNull(options, "options");
        return executor.execute(options.toArgs(config.getDefaultWhere()).toArray(new String[0]));
    }
    public ComfyCliResult jobs(String... args) { return prefixed("jobs", args); }
    public ComfyCliResult jobsList() { return executor.execute("jobs", "ls"); }
    public ComfyCliResult jobStatus(String promptId) {
        return executor.execute("jobs", "status", requireNonBlank("promptId", promptId));
    }
    public ComfyCliResult jobsWait(String... promptIds) {
        return prefixed2("jobs", "wait", requireValues("promptIds", promptIds));
    }
    public ComfyCliResult jobWatch(String promptId) {
        return executor.execute("jobs", "watch", requireNonBlank("promptId", promptId));
    }
    public ComfyCliResult jobCancel(String promptId) {
        return executor.execute("jobs", "cancel", requireNonBlank("promptId", promptId));
    }
    public ComfyCliResult validate(String... args) { return prefixed("validate", args); }
    public ComfyCliResult validateWorkflow(String workflowPath) {
        return executor.execute("validate", "--workflow", requireNonBlank("workflowPath", workflowPath));
    }

    public ComfyCliResult templates(String... args) { return prefixed("templates", args); }
    public ComfyCliResult templatesList(String... filters) { return prefixed2("templates", "ls", filters); }
    public ComfyCliResult templateShow(String name) {
        return executor.execute("templates", "show", requireNonBlank("name", name));
    }
    public ComfyCliResult templateFetch(String name, String outPath) {
        return executor.execute("templates", "fetch", requireNonBlank("name", name),
                "--out", requireNonBlank("outPath", outPath));
    }

    public ComfyCliResult workflow(String... args) { return prefixed("workflow", args); }
    public ComfyCliResult workflowSlots(String path) {
        return executor.execute("workflow", "slots", requireNonBlank("path", path));
    }
    public ComfyCliResult workflowNotes(String path) {
        return executor.execute("workflow", "notes", requireNonBlank("path", path));
    }
    public ComfyCliResult workflowSetSlot(String path, boolean stdout, String... overrides) {
        List<String> args = words("workflow", "set-slot", requireNonBlank("path", path));
        addNonNull(args, requireValues("overrides", overrides));
        if (stdout) args.add("--stdout");
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult workflowVary(String path, String... overrides) {
        List<String> args = words("workflow", "vary", requireNonBlank("path", path));
        addNonNull(args, overrides);
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult workflowList() { return executor.execute("workflow", "list"); }
    public ComfyCliResult workflowGet(String name) {
        return executor.execute("workflow", "get", requireNonBlank("name", name));
    }
    public ComfyCliResult workflowDelete(String name) {
        return executor.execute("workflow", "delete", requireNonBlank("name", name));
    }
    public ComfyCliResult workflowCompose(String path) {
        return executor.execute("workflow", "compose", requireNonBlank("path", path));
    }
    public ComfyCliResult workflowDecompose(String path) {
        return executor.execute("workflow", "decompose", requireNonBlank("path", path));
    }

    public ComfyCliResult nodes(String... args) { return prefixed("nodes", args); }
    public ComfyCliResult nodesSearch(String query) {
        return executor.execute("nodes", "search", requireNonBlank("query", query));
    }
    public ComfyCliResult nodesShow(String name) {
        return executor.execute("nodes", "show", requireNonBlank("name", name));
    }
    public ComfyCliResult nodesList(String... filters) { return prefixed2("nodes", "ls", filters); }
    public ComfyCliResult nodeInstall(String... names) {
        return prefixed2("node", "install", requireValues("names", names));
    }

    public ComfyCliResult models(String... args) { return prefixed("models", args); }
    public ComfyCliResult model(String... args) { return prefixed("model", args); }
    public ComfyCliResult modelsListFolders() { return executor.execute("model", "list-folders"); }
    public ComfyCliResult modelsListFolder(String folder) {
        return executor.execute("model", "list-folder", requireNonBlank("folder", folder));
    }
    public ComfyCliResult modelsSearch(String query) {
        return executor.execute("model", "search", "--text", requireNonBlank("query", query));
    }
    public ComfyCliResult modelsShow(String name) {
        return executor.execute("model", "show", requireNonBlank("name", name));
    }
    public ComfyCliResult modelDownload(String url, String... args) {
        List<String> all = words("model", "download", requireNonBlank("url", url));
        addNonNull(all, args);
        return executor.execute(all.toArray(new String[0]));
    }

    public ComfyCliResult upload(String... paths) {
        return prefixed("upload", requireValues("paths", paths));
    }
    public ComfyCliResult download(String promptId, String outDir, boolean urlOnly) {
        List<String> args = words("download", requireNonBlank("promptId", promptId));
        if (outDir != null) {
            args.add("-o");
            args.add(outDir);
        }
        if (urlOnly) args.add("--url-only");
        return executor.execute(args.toArray(new String[0]));
    }

    /** Raw skills family escape hatch. */
    // Current first-party CLI top-level domains. These family wrappers keep
    // the SDK complete without freezing rapidly-evolving beta option schemas.
    public ComfyCliResult runTemplate(String... args) { return prefixed("run-template", args); }
    public ComfyCliResult preview(String... args) { return prefixed("preview", args); }
    public ComfyCliResult knowledge(String... args) { return prefixed("knowledge", args); }
    public ComfyCliResult manager(String... args) { return prefixed("manager", args); }
    public ComfyCliResult prCache(String... args) { return prefixed("pr-cache", args); }
    public ComfyCliResult codeSearch(String... args) { return prefixed("code-search", args); }
    public ComfyCliResult tracking(String... args) { return prefixed("tracking", args); }
    public ComfyCliResult auth(String... args) { return prefixed("auth", args); }
    public ComfyCliResult build(String... args) { return prefixed("build", args); }
    public ComfyCliResult deploy(String... args) { return prefixed("deploy", args); }
    public ComfyCliResult project(String... args) { return prefixed("project", args); }
    public ComfyCliResult assets(String... args) { return prefixed("assets", args); }
    public ComfyCliResult agent(String... args) { return prefixed("agent", args); }
    public ComfyCliResult feedback(String... args) { return prefixed("feedback", args); }
    public ComfyCliResult standalone(String... args) { return prefixed("standalone", args); }

    public ComfyCliResult skills(String... args) { return prefixed("skills", args); }
    public ComfyCliResult skillsInstall() { return executor.execute("skills", "install"); }
    public ComfyCliResult skillsInstall(String... args) { return prefixed2("skills", "install", args); }
    public ComfyCliResult skillsUninstall(String... args) { return prefixed2("skills", "uninstall", args); }
    public ComfyCliResult skillsList() { return executor.execute("skills", "list"); }
    public ComfyCliResult skillsShow(String name) {
        return executor.execute("skills", "show", requireNonBlank("name", name));
    }
    public ComfyCliResult skillsStatus() { return executor.execute("skills", "status"); }
    public ComfyCliResult skillsStatus(String scope) {
        return executor.execute("skills", "status", "--scope", requireSkillScope(scope));
    }
    public ComfyCliResult skillsValidate(String path) {
        return executor.execute("skills", "validate", requireNonBlank("path", path));
    }

    public ComfyCliResult execute(String... args) { return executor.execute(args); }

    private ComfyCliResult prefixed(String prefix, String... args) {
        List<String> all = words(prefix);
        addNonNull(all, args);
        return executor.execute(all.toArray(new String[0]));
    }

    private ComfyCliResult prefixed2(String first, String second, String... args) {
        List<String> all = words(first, second);
        addNonNull(all, args);
        return executor.execute(all.toArray(new String[0]));
    }

    private static List<String> words(String... values) {
        List<String> out = new ArrayList<String>();
        addNonNull(out, values);
        return out;
    }

    private static void addNonNull(List<String> out, String... values) {
        if (values == null) return;
        for (String value : values) if (value != null) out.add(value);
    }

    private static void option(List<String> args, String flag, String value) {
        if (value != null && !value.isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }

    private static String requireWhere(String where) {
        if (!"local".equals(where) && !"cloud".equals(where)) {
            throw new IllegalArgumentException("where must be 'local' or 'cloud': " + where);
        }
        return where;
    }

    private static String requireSkillScope(String scope) {
        if (!"user".equals(scope) && !"project".equals(scope)) {
            throw new IllegalArgumentException("scope must be 'user' or 'project': " + scope);
        }
        return scope;
    }

    private static String requireNonBlank(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must not contain NUL");
        }
        return value;
    }

    private static String[] requireValues(String name, String... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        for (String value : values) requireNonBlank(name, value);
        return values;
    }

    public static class SetupOptions {
        private String where;
        private String projectDir;
        private String apiKey;
        private boolean nonInteractive;
        private boolean skipSkills;
        private boolean skipVerify;

        public SetupOptions where(String value) { this.where = requireWhere(value); return this; }
        public SetupOptions projectDir(String value) { this.projectDir = value; return this; }
        public SetupOptions apiKey(String value) { this.apiKey = requireNonBlank("apiKey", value); return this; }
        public SetupOptions nonInteractive(boolean value) { this.nonInteractive = value; return this; }
        public SetupOptions skipSkills(boolean value) { this.skipSkills = value; return this; }
        public SetupOptions skipVerify(boolean value) { this.skipVerify = value; return this; }

        List<String> toArgs() {
            List<String> args = words("setup");
            option(args, "--where", where);
            option(args, "--project-dir", projectDir);
            option(args, "--api-key", apiKey);
            if (nonInteractive) args.add("--non-interactive");
            if (skipSkills) args.add("--skip-skills");
            if (skipVerify) args.add("--skip-verify");
            return args;
        }
    }

    public static class RunOptions {
        private String workflowPath;
        private Boolean wait;
        private String where;
        private String prompt;
        private final List<String> setOverrides = new ArrayList<String>();
        private Boolean notify;
        private boolean verbose;
        private String host;
        private Integer port;
        private Integer timeoutSeconds;
        private boolean printPrompt;
        private String workflowId;
        private boolean noWatch;
        private boolean allowSpend;
        private boolean json;
        private boolean jsonStream;

        public RunOptions() { }

        public RunOptions(String workflowPath) {
            this.workflowPath = requireNonBlank("workflowPath", workflowPath);
        }

        public RunOptions workflow(String value) {
            this.workflowPath = value == null ? null : requireNonBlank("workflowPath", value);
            return this;
        }
        public RunOptions wait(boolean value) { this.wait = Boolean.valueOf(value); return this; }
        public RunOptions prompt(String value) { this.prompt = value; return this; }
        public RunOptions set(String value) { this.setOverrides.add(requireNonBlank("set", value)); return this; }
        public RunOptions notify(boolean value) { this.notify = Boolean.valueOf(value); return this; }
        public RunOptions verbose(boolean value) { this.verbose = value; return this; }
        public RunOptions host(String value) { this.host = value; return this; }
        public RunOptions port(int value) { this.port = Integer.valueOf(value); return this; }
        public RunOptions timeoutSeconds(int value) { this.timeoutSeconds = Integer.valueOf(value); return this; }
        public RunOptions printPrompt(boolean value) { this.printPrompt = value; return this; }
        public RunOptions workflowId(String value) { this.workflowId = value; return this; }
        public RunOptions noWatch(boolean value) { this.noWatch = value; return this; }
        public RunOptions allowSpend(boolean value) { this.allowSpend = value; return this; }
        public RunOptions where(String value) { this.where = value == null ? null : requireWhere(value); return this; }
        public RunOptions json(boolean value) { this.json = value; return this; }
        public RunOptions jsonStream(boolean value) { this.jsonStream = value; return this; }

        List<String> toArgs(String defaultWhere) {
            if (workflowPath != null && prompt != null) {
                throw new IllegalStateException("run workflow and prompt are mutually exclusive");
            }
            if (workflowPath == null && (prompt == null || prompt.trim().isEmpty())) {
                throw new IllegalStateException("run requires workflow or prompt");
            }
            List<String> args = new ArrayList<String>();
            if (json) args.add("--json");
            if (jsonStream) args.add("--json-stream");
            args.add("run");
            if (workflowPath != null) {
                args.add("--workflow");
                args.add(workflowPath);
            }
            option(args, "--prompt", prompt);
            for (String override : setOverrides) {
                args.add("--set");
                args.add(override);
            }
            if (Boolean.TRUE.equals(wait)) args.add("--wait");
            if (notify != null) args.add(notify.booleanValue() ? "--notify" : "--no-notify");
            if (verbose) args.add("--verbose");
            option(args, "--host", host);
            if (port != null) { args.add("--port"); args.add(String.valueOf(port)); }
            if (timeoutSeconds != null) { args.add("--timeout"); args.add(String.valueOf(timeoutSeconds)); }
            if (printPrompt) args.add("--print-prompt");
            option(args, "--workflow-id", workflowId);
            if (noWatch) args.add("--no-watch");
            if (allowSpend) args.add("--allow-spend");
            String route = where != null ? where : defaultWhere;
            if (route != null) {
                args.add("--where");
                args.add(requireWhere(route));
            }
            return args;
        }
    }

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
        private String ratio;
        private String renderingSpeed;
        private Long seed;
        private String model;
        private boolean async;
        private boolean json;
        private String where;
        private Integer timeoutSeconds;
        private final Map<String, String> params = new LinkedHashMap<String, String>();

        public GenerateOptions() {}

        public GenerateOptions(GenerateOptions source) {
            Objects.requireNonNull(source, "source");
            this.prompt = source.prompt;
            this.width = source.width;
            this.height = source.height;
            this.download = source.download;
            this.image = source.image;
            this.mask = source.mask;
            this.resolution = source.resolution;
            this.duration = source.duration;
            this.aspectRatio = source.aspectRatio;
            this.ratio = source.ratio;
            this.renderingSpeed = source.renderingSpeed;
            this.seed = source.seed;
            this.model = source.model;
            this.async = source.async;
            this.json = source.json;
            this.where = source.where;
            this.timeoutSeconds = source.timeoutSeconds;
            this.params.putAll(source.params);
        }

        public GenerateOptions prompt(String v) { this.prompt = v; return this; }
        public GenerateOptions width(int v) { this.width = Integer.valueOf(v); return this; }
        public GenerateOptions height(int v) { this.height = Integer.valueOf(v); return this; }
        public GenerateOptions download(String v) { this.download = v; return this; }
        public GenerateOptions image(String v) { this.image = v; return this; }
        public GenerateOptions mask(String v) { this.mask = v; return this; }
        public GenerateOptions resolution(String v) { this.resolution = v; return this; }
        public GenerateOptions duration(int v) { this.duration = Integer.valueOf(v); return this; }
        public GenerateOptions aspectRatio(String v) { this.aspectRatio = v; return this; }
        public GenerateOptions ratio(String v) { this.ratio = v; return this; }
        public GenerateOptions renderingSpeed(String v) { this.renderingSpeed = v; return this; }
        public GenerateOptions seed(long v) { this.seed = Long.valueOf(v); return this; }
        public GenerateOptions model(String v) { this.model = v; return this; }
        public GenerateOptions async(boolean v) { this.async = v; return this; }
        public GenerateOptions json(boolean v) { this.json = v; return this; }
        public GenerateOptions where(String v) { this.where = v == null ? null : requireWhere(v); return this; }
        public GenerateOptions timeoutSeconds(int v) {
            if (v <= 0) throw new IllegalArgumentException("timeoutSeconds must be > 0");
            this.timeoutSeconds = Integer.valueOf(v);
            return this;
        }

        public GenerateOptions param(String name, Object value) {
            String n = requireNonBlank("param name", name);
            if (!n.matches("[A-Za-z0-9_][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("invalid generate param name: " + name);
            }
            if (value == null) params.remove(n);
            else params.put(n, String.valueOf(value));
            return this;
        }

        List<String> toArgs() {
            List<String> args = new ArrayList<String>();
            option(args, "--prompt", prompt);
            if (width != null) option(args, "--width", String.valueOf(width));
            if (height != null) option(args, "--height", String.valueOf(height));
            option(args, "--download", download);
            option(args, "--image", image);
            option(args, "--mask", mask);
            option(args, "--resolution", resolution);
            if (duration != null) option(args, "--duration", String.valueOf(duration));
            option(args, "--aspect_ratio", aspectRatio);
            option(args, "--ratio", ratio);
            option(args, "--rendering_speed", renderingSpeed);
            if (seed != null) option(args, "--seed", String.valueOf(seed));
            option(args, "--model", model);
            if (timeoutSeconds != null) option(args, "--timeout", String.valueOf(timeoutSeconds));
            for (Map.Entry<String, String> entry : params.entrySet()) {
                option(args, "--" + entry.getKey(), entry.getValue());
            }
            if (async) args.add("--async");
            if (json) args.add("--json");
            return args;
        }
    }
}
