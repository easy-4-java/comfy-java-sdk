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
 * Configuration for the local {@code comfy} CLI subprocess route.
 *
 * <p>Credentials should be supplied through {@link #environment} rather than
 * command-line arguments so they are not exposed through process listings.</p>
 */
@Data
public class ComfyClientConfig {

    /** Name or absolute path of the local {@code comfy} executable. */
    private String localExecutable = "comfy";

    /** Extra environment variables merged over the parent environment. */
    private Map<String, String> environment;

    /** Normal command timeout in seconds. */
    private int localTimeoutSeconds = 600;

    /** Dedicated timeout in seconds for {@code comfy --version} probes. */
    private int localProbeTimeoutSeconds = 5;

    /** Maximum stdout bytes retained per process; {@code 0} means unbounded. */
    private int maxStdoutBytes = 16 * 1024 * 1024;

    /** Maximum stderr bytes retained per process; {@code 0} means unbounded. */
    private int maxStderrBytes = 4 * 1024 * 1024;

    /** Default route forwarded as {@code --where local|cloud} where supported. */
    private String defaultWhere;

    /** Validates configuration without reading or logging secret values. */
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
        if (maxStdoutBytes < 0 || maxStderrBytes < 0) {
            throw new IllegalStateException("output capture limits must be >= 0");
        }
        if (defaultWhere != null && !"local".equals(defaultWhere) && !"cloud".equals(defaultWhere)) {
            throw new IllegalStateException("defaultWhere must be 'local' or 'cloud': " + defaultWhere);
        }
    }
}
