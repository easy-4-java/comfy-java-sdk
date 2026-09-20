/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.cli;

import io.github.easy4j.comfy.model.ComfyCliEvent;

/** Listener for incremental comfy CLI NDJSON execution. */
public interface ComfyCliStreamListener {
    void onEvent(ComfyCliEvent event);

    default void onStderr(String chunk) { }

    default void onComplete(ComfyCliResult result) { }

    default void onError(Throwable error) { }
}
