/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.cli;

import java.util.Objects;

import lombok.Data;

/** Outcome of one {@code comfy} CLI invocation. */
@Data
public class ComfyCliResult {

    private static final String TIMEOUT_PREFIX = "comfy CLI timed out after ";

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean stdoutTruncated;
    private final boolean stderrTruncated;

    public ComfyCliResult(int exitCode, String stdout, String stderr) {
        this(exitCode, stdout, stderr, false, false);
    }

    public ComfyCliResult(int exitCode, String stdout, String stderr,
            boolean stdoutTruncated, boolean stderrTruncated) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.stdoutTruncated = stdoutTruncated;
        this.stderrTruncated = stderrTruncated;
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public boolean isTimeout() {
        return exitCode == -1 && Objects.nonNull(stderr) && stderr.startsWith(TIMEOUT_PREFIX);
    }

    public boolean isTruncated() {
        return stdoutTruncated || stderrTruncated;
    }
}
