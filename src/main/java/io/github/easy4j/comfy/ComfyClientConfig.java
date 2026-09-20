/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy;

import java.util.Map;
import java.util.Objects;

import lombok.Data;

/**
 * Runtime configuration for the local {@code comfy} CLI subprocess route.
 *
 * <p>Credentials belong in {@link #environment}, never in argv, because
 * command-line arguments can be visible to other local processes.</p>
 */
@Data
public class ComfyClientConfig {

    private String localExecutable = "comfy";
    private Map<String, String> environment;
    private int localTimeoutSeconds = 600;
    private int localProbeTimeoutSeconds = 5;
    private int maxOutputBytes = 16 * 1024 * 1024;
    private String defaultWhere;

    public void validate() {
        Objects.requireNonNull(localExecutable, "localExecutable");
        if (localExecutable.trim().isEmpty()) {
            throw new IllegalStateException("localExecutable must not be blank");
        }
        if (localTimeoutSeconds <= 0) {
            throw new IllegalStateException("localTimeoutSeconds must be > 0");
        }
        if (localProbeTimeoutSeconds <= 0) {
            throw new IllegalStateException("localProbeTimeoutSeconds must be > 0");
        }
        if (maxOutputBytes == 0 || maxOutputBytes < -1) {
            throw new IllegalStateException("maxOutputBytes must be -1 (unbounded) or > 0");
        }
        if (defaultWhere != null) {
            requireWhere(defaultWhere);
        }
    }

    public static void requireWhere(String where) {
        if (!"local".equals(where) && !"cloud".equals(where)) {
            throw new IllegalArgumentException("where must be 'local' or 'cloud': " + where);
        }
    }
}
