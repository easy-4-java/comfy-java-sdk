/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.easy4j.comfy.ComfyClientConfig;
import io.github.easy4j.comfy.model.ComfyCliEvent;

class ComfyCliStreamingTest {

    @Test
    void shouldDeliverNdjsonBeforeProcessCompletion() throws Exception {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable(Paths.get("src", "test", "resources", "comfy-stream.sh")
                .toAbsolutePath().toString());
        config.setLocalTimeoutSeconds(5);

        final List<ComfyCliEvent> events = new ArrayList<ComfyCliEvent>();
        ComfyCliStreamExecutor executor = new ComfyCliStreamExecutor(config);
        ComfyCliStreamSession session = executor.execute(new ComfyCliStreamListener() {
            @Override public synchronized void onEvent(ComfyCliEvent event) {
                events.add(event);
            }
        });

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (events.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10L);

        assertFalse(events.isEmpty(), "queued event must arrive before process completion");
        ComfyCliResult result = session.completion().get(5, TimeUnit.SECONDS);
        assertTrue(result.isSuccess());
        assertEquals(2, events.size());
        assertEquals("queued", events.get(0).getType());
        assertTrue(events.get(1).isEnvelope());
    }

    @Test
    void cancelShouldTerminateChild() throws Exception {
        ComfyClientConfig config = new ComfyClientConfig();
        config.setLocalExecutable("/bin/sh");
        config.setLocalTimeoutSeconds(30);
        config.setProcessShutdownGraceMillis(200);

        ComfyCliStreamSession session = new ComfyCliStreamExecutor(config).execute(
                new ComfyCliStreamListener() {
                    @Override public void onEvent(ComfyCliEvent event) { }
                }, "-c", "sleep 30");

        assertTrue(session.isAlive());
        assertTrue(session.cancel());
        session.completion().get(5, TimeUnit.SECONDS);
        assertFalse(session.isAlive());
    }
}
