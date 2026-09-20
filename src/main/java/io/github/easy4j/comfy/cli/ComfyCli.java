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
 * Command mapper for the documented {@code comfy} CLI surface.
 *
 * <p>Named methods cover stable/high-frequency verbs while every command
 * family also retains a varargs form and {@link #execute(String...)} remains
 * the forward-compatible escape hatch. Use {@link #executeJson(String...)}
 * with {@code comfy --json discover} when a newer CLI adds a verb before this
 * SDK ships a named method.</p>
 */
public class ComfyCli {

    private final ComfyCliExecutor executor;
    private final ComfyClientConfig config;

    public ComfyCli(ComfyClientConfig config, ComfyCliExecutor executor) {
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public ComfyCliExecutor executor() {
        return executor;
    }

    // ---- global/basic ------------------------------------------------------

    public ComfyCliResult version() { return executor.execute("--version"); }
    public ComfyCliResult help() { return executor.execute("--help"); }
    public ComfyCliResult installCompletion() { return executor.execute("--install-completion"); }
    public ComfyCliResult discoverJson() { return executeJson("discover"); }
    public ComfyCliResult executeJson(String... args) { return executor.execute(prepend("--json", args)); }
    public ComfyCliResult executeJsonStream(String... args) { return executor.execute(prepend("--json-stream", args)); }

    // ---- setup/routing/cloud ----------------------------------------------

    public ComfyCliResult setup() { return executor.execute("setup"); }
    public ComfyCliResult setup(String... args) { return prefixed("setup", args); }
    public ComfyCliResult setupYes() { return executor.execute("setup", "-y"); }

    public ComfyCliResult cloud(String... args) { return prefixed("cloud", args); }
    public ComfyCliResult cloudLogin() { return executor.execute("cloud", "login"); }
    public ComfyCliResult cloudLoginNoBrowser() { return executor.execute("cloud", "login", "--no-browser"); }
    public ComfyCliResult cloudWhoami() { return executor.execute("cloud", "whoami"); }
    public ComfyCliResult cloudLogout() { return executor.execute("cloud", "logout"); }
    public ComfyCliResult cloudStatus() { return executor.execute("cloud", "status"); }
    public ComfyCliResult cloudSetBaseUrl(String url) {
        return executor.execute("cloud", "set-base-url", Objects.requireNonNull(url, "url"));
    }

    public ComfyCliResult setDefaultWhere(String where) {
        ComfyClientConfig.requireWhere(where);
        return executor.execute("set-default", "--where", where);
    }

    public ComfyCliResult setDefaultWorkspace(String path) {
        return executor.execute("set-default", Objects.requireNonNull(path, "path"));
    }

    // ---- local ComfyUI lifecycle/environment -------------------------------

    public ComfyCliResult install(String... args) { return prefixed("install", args); }
    public ComfyCliResult launch(String... args) { return prefixed("launch", args); }
    public ComfyCliResult launchBackground(String... args) {
        List<String> all = new ArrayList<String>();
        all.add("launch");
        all.add("--background");
        addAll(all, args);
        return executor.execute(all.toArray(new String[0]));
    }
    public ComfyCliResult stop() { return executor.execute("stop"); }
    public ComfyCliResult stop(String... args) { return prefixed("stop", args); }
    public ComfyCliResult update(String... args) { return prefixed("update", args); }
    public ComfyCliResult which() { return executor.execute("which"); }
    public ComfyCliResult env() { return executor.execute("env"); }
    public ComfyCliResult outdated() { return executor.execute("outdated"); }
    public ComfyCliResult logs(String... args) { return prefixed("logs", args); }
    public ComfyCliResult systemStats() { return executor.execute("system-stats"); }
    public ComfyCliResult freeMemory() { return executor.execute("free-memory"); }
    public ComfyCliResult project(String... args) { return prefixed("project", args); }

    // ---- partner generation ------------------------------------------------

    public ComfyCliResult generate(String model, GenerateOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(options, "options");
        List<String> args = new ArrayList<String>();
        args.add("generate");
        args.add(model);
        args.addAll(options.toArgs());
        String where = options.where != null ? options.where : config.getDefaultWhere();
        if (where != null) {
            ComfyClientConfig.requireWhere(where);
            args.add("--where");
            args.add(where);
        }
        return executor.execute(args.toArray(new String[0]));
    }

    public ComfyCliResult generate(String... args) { return prefixed("generate", args); }

    public ComfyCliResult generateList(String category, String partner) {
        return generateList(category, partner, null);
    }

    public ComfyCliResult generateList(String category, String partner, String query) {
        List<String> args = new ArrayList<String>();
        args.add("generate");
        args.add("list");
        if (category != null) { args.add("--category"); args.add(category); }
        if (partner != null) { args.add("--partner"); args.add(partner); }
        if (query != null) { args.add("--query"); args.add(query); }
        return executor.execute(args.toArray(new String[0]));
    }

    public ComfyCliResult generateSchema(String model) {
        return executor.execute("generate", "schema", Objects.requireNonNull(model, "model"));
    }

    public ComfyCliResult generateRefresh() { return executor.execute("generate", "refresh"); }

    public ComfyCliResult generateUpload(String file) {
        return executor.execute("generate", "upload", Objects.requireNonNull(file, "file"));
    }

    public ComfyCliResult generateResume(String model, String jobId, String download) {
        List<String> args = new ArrayList<String>();
        args.add("generate");
        args.add("resume");
        args.add(Objects.requireNonNull(model, "model"));
        args.add(Objects.requireNonNull(jobId, "jobId"));
        if (download != null) { args.add("--download"); args.add(download); }
        return executor.execute(args.toArray(new String[0]));
    }

    // ---- workflows/jobs/templates -----------------------------------------

    public ComfyCliResult run(String... args) { return prefixed("run", args); }

    public ComfyCliResult runWorkflow(String workflowPath, boolean wait) {
        List<String> args = new ArrayList<String>();
        args.add("run");
        args.add("--workflow");
        args.add(Objects.requireNonNull(workflowPath, "workflowPath"));
        if (wait) { args.add("--wait"); }
        return executor.execute(args.toArray(new String[0]));
    }

    public ComfyCliResult runTemplate(String... args) { return prefixed("run-template", args); }

    public ComfyCliResult jobs(String... args) { return prefixed("jobs", args); }
    public ComfyCliResult jobsList() { return executor.execute("jobs", "ls"); }
    public ComfyCliResult jobStatus(String promptId) {
        return executor.execute("jobs", "status", Objects.requireNonNull(promptId, "promptId"));
    }
    public ComfyCliResult jobWatch(String promptId) {
        return executor.execute("jobs", "watch", Objects.requireNonNull(promptId, "promptId"));
    }
    public ComfyCliResult jobsWait(String... promptIds) {
        List<String> args = new ArrayList<String>();
        args.add("jobs");
        args.add("wait");
        addAll(args, promptIds);
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult jobCancel(String promptId) {
        return executor.execute("jobs", "cancel", Objects.requireNonNull(promptId, "promptId"));
    }

    public ComfyCliResult validate(String... args) { return prefixed("validate", args); }
    public ComfyCliResult validateWorkflow(String workflowPath) {
        return executor.execute("validate", "--workflow", Objects.requireNonNull(workflowPath, "workflowPath"));
    }

    public ComfyCliResult templates(String... args) { return prefixed("templates", args); }
    public ComfyCliResult templatesList(String type, String tag) {
        List<String> args = new ArrayList<String>();
        args.add("templates"); args.add("ls");
        if (type != null) { args.add("--type"); args.add(type); }
        if (tag != null) { args.add("--tag"); args.add(tag); }
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult templateShow(String name) {
        return executor.execute("templates", "show", Objects.requireNonNull(name, "name"));
    }
    public ComfyCliResult templateFetch(String name, String outPath) {
        return executor.execute("templates", "fetch", Objects.requireNonNull(name, "name"),
                "--out", Objects.requireNonNull(outPath, "outPath"));
    }

    public ComfyCliResult workflow(String... args) { return prefixed("workflow", args); }
    public ComfyCliResult workflowSlots(String path) {
        return executor.execute("workflow", "slots", Objects.requireNonNull(path, "path"));
    }
    public ComfyCliResult workflowNotes(String path) {
        return executor.execute("workflow", "notes", Objects.requireNonNull(path, "path"));
    }
    public ComfyCliResult workflowSetSlot(String path, String... overrides) {
        List<String> args = new ArrayList<String>();
        args.add("workflow"); args.add("set-slot"); args.add(Objects.requireNonNull(path, "path"));
        addAll(args, overrides);
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult workflowVary(String path, String outDir, String... slotSpecs) {
        List<String> args = new ArrayList<String>();
        args.add("workflow"); args.add("vary"); args.add(Objects.requireNonNull(path, "path"));
        if (slotSpecs != null) {
            for (String slot : slotSpecs) {
                if (slot != null) { args.add("--slot"); args.add(slot); }
            }
        }
        if (outDir != null) { args.add("--out-dir"); args.add(outDir); }
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult workflowList() { return executor.execute("workflow", "list"); }
    public ComfyCliResult workflowGet(String id, String outPath) {
        List<String> args = new ArrayList<String>();
        args.add("workflow"); args.add("get"); args.add(Objects.requireNonNull(id, "id"));
        if (outPath != null) { args.add("--out"); args.add(outPath); }
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult workflowSave(String path, String name) {
        List<String> args = new ArrayList<String>();
        args.add("workflow"); args.add("save"); args.add(Objects.requireNonNull(path, "path"));
        if (name != null) { args.add("--name"); args.add(name); }
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult workflowDelete(String id) {
        return executor.execute("workflow", "delete", Objects.requireNonNull(id, "id"));
    }
    public ComfyCliResult workflowCompose(String blueprint, String outPath) {
        return executor.execute("workflow", "compose", Objects.requireNonNull(blueprint, "blueprint"),
                "-o", Objects.requireNonNull(outPath, "outPath"));
    }
    public ComfyCliResult workflowDecompose(String workflowPath) {
        return executor.execute("workflow", "decompose", Objects.requireNonNull(workflowPath, "workflowPath"));
    }

    // ---- discovery/assets/package management -------------------------------

    public ComfyCliResult nodes(String... args) { return prefixed("nodes", args); }
    public ComfyCliResult nodesSearch(String query) {
        return executor.execute("nodes", "search", Objects.requireNonNull(query, "query"));
    }
    public ComfyCliResult nodeShow(String name) {
        return executor.execute("nodes", "show", Objects.requireNonNull(name, "name"));
    }
    public ComfyCliResult nodesList(String... filters) {
        List<String> args = new ArrayList<String>();
        args.add("nodes"); args.add("ls"); addAll(args, filters);
        return executor.execute(args.toArray(new String[0]));
    }

    public ComfyCliResult models(String... args) { return prefixed("models", args); }
    public ComfyCliResult modelFolders() { return executor.execute("models", "list-folders"); }
    public ComfyCliResult modelsSearch(String text, String type) {
        List<String> args = new ArrayList<String>();
        args.add("models"); args.add("search");
        if (text != null) { args.add("--text"); args.add(text); }
        if (type != null) { args.add("--type"); args.add(type); }
        return executor.execute(args.toArray(new String[0]));
    }
    public ComfyCliResult modelShow(String name) {
        return executor.execute("models", "show", Objects.requireNonNull(name, "name"));
    }

    /** Custom-node management family ({@code comfy node ...}). */
    public ComfyCliResult node(String... args) { return prefixed("node", args); }
    public ComfyCliResult nodeInstall(String name) {
        return executor.execute("node", "install", Objects.requireNonNull(name, "name"));
    }

    /** Local model-management family ({@code comfy model ...}). */
    public ComfyCliResult model(String... args) { return prefixed("model", args); }
    public ComfyCliResult modelDownload(String url, String relativePath) {
        List<String> args = new ArrayList<String>();
        args.add("model"); args.add("download"); args.add("--url");
        args.add(Objects.requireNonNull(url, "url"));
        if (relativePath != null) { args.add("--relative-path"); args.add(relativePath); }
        return executor.execute(args.toArray(new String[0]));
    }

    public ComfyCliResult upload(String... files) { return prefixed("upload", files); }
    public ComfyCliResult download(String promptId, String... args) {
        List<String> all = new ArrayList<String>();
        all.add("download");
        all.add(Objects.requireNonNull(promptId, "promptId"));
        addAll(all, args);
        return executor.execute(all.toArray(new String[0]));
    }

    // ---- skills/telemetry --------------------------------------------------

    public ComfyCliResult skills(String... args) { return prefixed("skills", args); }
    public ComfyCliResult skillsInstall() { return executor.execute("skills", "install"); }
    public ComfyCliResult skillsList() { return executor.execute("skills", "list"); }
    public ComfyCliResult skillsStatus() { return executor.execute("skills", "status"); }
    public ComfyCliResult trackingEnable() { return executor.execute("tracking", "enable"); }
    public ComfyCliResult trackingDisable() { return executor.execute("tracking", "disable"); }

    // ---- escape hatch ------------------------------------------------------

    public ComfyCliResult execute(String... args) { return executor.execute(args); }

    private ComfyCliResult prefixed(String prefix, String... args) {
        return executor.execute(prepend(prefix, args));
    }

    private static String[] prepend(String first, String... args) {
        int length = args == null ? 0 : args.length;
        String[] all = new String[length + 1];
        all[0] = first;
        if (length > 0) {
            System.arraycopy(args, 0, all, 1, length);
        }
        return all;
    }

    private static void addAll(List<String> target, String... values) {
        if (values == null) { return; }
        for (String value : values) {
            if (value != null) { target.add(value); }
        }
    }

    /**
     * Fluent options for one {@code comfy generate <model>} run.
     *
     * <p>The common cross-provider flags are typed. {@link #option(String, Object)}
     * and {@link #flag(String)} preserve forward compatibility with provider
     * parameters returned by {@code comfy generate schema <model>}.</p>
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
        private Integer timeoutSeconds;
        private boolean async;
        private boolean json;
        private String where;
        private final Map<String, String> extraOptions = new LinkedHashMap<String, String>();
        private final List<String> extraFlags = new ArrayList<String>();

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
            this.renderingSpeed = source.renderingSpeed;
            this.timeoutSeconds = source.timeoutSeconds;
            this.async = source.async;
            this.json = source.json;
            this.where = source.where;
            this.extraOptions.putAll(source.extraOptions);
            this.extraFlags.addAll(source.extraFlags);
        }

        public GenerateOptions copy() { return new GenerateOptions(this); }
        public GenerateOptions prompt(String v) { this.prompt = v; return this; }
        public GenerateOptions width(int v) { this.width = v; return this; }
        public GenerateOptions height(int v) { this.height = v; return this; }
        public GenerateOptions download(String v) { this.download = v; return this; }
        public GenerateOptions image(String v) { this.image = v; return this; }
        public GenerateOptions mask(String v) { this.mask = v; return this; }
        public GenerateOptions resolution(String v) { this.resolution = v; return this; }
        public GenerateOptions duration(int v) { this.duration = v; return this; }
        public GenerateOptions aspectRatio(String v) { this.aspectRatio = v; return this; }
        public GenerateOptions renderingSpeed(String v) { this.renderingSpeed = v; return this; }
        public GenerateOptions timeoutSeconds(int v) {
            if (v <= 0) { throw new IllegalArgumentException("timeoutSeconds must be > 0"); }
            this.timeoutSeconds = v; return this;
        }
        public GenerateOptions async(boolean v) { this.async = v; return this; }
        public GenerateOptions json(boolean v) { this.json = v; return this; }
        public GenerateOptions where(String v) {
            if (v != null) { ComfyClientConfig.requireWhere(v); }
            this.where = v; return this;
        }
        public GenerateOptions option(String name, Object value) {
            extraOptions.put(optionName(name), String.valueOf(Objects.requireNonNull(value, "value")));
            return this;
        }
        public GenerateOptions flag(String name) {
            extraFlags.add(optionName(name));
            return this;
        }

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
            if (timeoutSeconds != null) { args.add("--timeout"); args.add(String.valueOf(timeoutSeconds)); }
            if (async) { args.add("--async"); }
            if (json) { args.add("--json"); }
            for (Map.Entry<String, String> entry : extraOptions.entrySet()) {
                args.add("--" + entry.getKey()); args.add(entry.getValue());
            }
            for (String flag : extraFlags) { args.add("--" + flag); }
            return args;
        }

        private static String optionName(String name) {
            Objects.requireNonNull(name, "name");
            String normalized = name.startsWith("--") ? name.substring(2) : name;
            if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("invalid option name: " + name);
            }
            return normalized;
        }
    }
}
