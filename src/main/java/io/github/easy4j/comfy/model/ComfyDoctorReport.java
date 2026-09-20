/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.easy4j.comfy.model;

import lombok.Data;

/** Compact environment readiness report that intentionally excludes secrets. */
@Data
public class ComfyDoctorReport {
    private final boolean cliAvailable;
    private final boolean versionHealthy;
    private final boolean workspaceResolved;
    private final boolean environmentHealthy;
    private final boolean discoveryHealthy;
    private final String version;
    private final String workspace;
    private final String summary;

    public boolean isReady() {
        return cliAvailable && versionHealthy && workspaceResolved
                && environmentHealthy && discoveryHealthy;
    }
}
