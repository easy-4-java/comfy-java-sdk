/*
 * Copyright (c) 2018-present, easy-4-java.
 * Licensed under the Apache License, Version 2.0.
 */
package io.github.easy4j.comfy.cli;

import java.util.concurrent.CompletableFuture;

/** Handle for one running streaming CLI child process. */
public final class ComfyCliStreamSession implements AutoCloseable {
    private final Process process;
    private final CompletableFuture<ComfyCliResult> completion;
    private final Runnable cancelAction;

    ComfyCliStreamSession(Process process, CompletableFuture<ComfyCliResult> completion,
                          Runnable cancelAction) {
        this.process = process;
        this.completion = completion;
        this.cancelAction = cancelAction;
    }

    public CompletableFuture<ComfyCliResult> completion() { return completion; }

    public boolean isAlive() { return process.isAlive(); }

    public boolean cancel() {
        if (completion.isDone()) return false;
        cancelAction.run();
        return true;
    }

    @Override
    public void close() { cancel(); }
}
