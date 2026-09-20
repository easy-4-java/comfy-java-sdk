/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/** Combined CLI/MCP runtime compatibility report with no credential material. */
@Getter
public class ComfyCompatibilityReport {
    private final ComfyDoctorReport doctor;
    private final ComfyCapabilityCatalog cliCapabilities;
    private final boolean mcpConnected;
    private final int advertisedMcpTools;
    private final List<String> missingFirstPartyMcpTools;

    public ComfyCompatibilityReport(ComfyDoctorReport doctor,
                                    ComfyCapabilityCatalog cliCapabilities,
                                    boolean mcpConnected,
                                    int advertisedMcpTools,
                                    List<String> missingFirstPartyMcpTools) {
        this.doctor = doctor;
        this.cliCapabilities = cliCapabilities;
        this.mcpConnected = mcpConnected;
        this.advertisedMcpTools = advertisedMcpTools;
        this.missingFirstPartyMcpTools = missingFirstPartyMcpTools == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(missingFirstPartyMcpTools));
    }

    public boolean isReady() {
        return doctor != null && doctor.isReady()
                && mcpConnected && missingFirstPartyMcpTools.isEmpty();
    }
}
