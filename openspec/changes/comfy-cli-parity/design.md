# Design: comfy-java-sdk CLI and MCP parity

## Architecture

Application
  -> ComfyClient
      -> ComfyCli -> ComfyCliExecutor -> comfy
      -> ComfyMcpClient -> MCP stdio JSON-RPC -> comfy-mcp -> comfy

The Java SDK remains an orchestration/integration SDK. The Python CLI and MCP server stay authoritative for Comfy behavior.

## CLI design
ComfyCli exposes typed methods for documented stable command families and keeps execute(String...) for newly-added or beta options. Option objects use Java 8-compatible mutable builders but support copy construction so high-level helpers never mutate caller-owned options.

Global JSON parsing uses ComfyJsonEnvelope, preserving JsonNode data/error fields to remain forward compatible with the CLI self-describing contract.

GenerateOptions models common stable flags and supports param(name,value) for model-specific beta parameters.

## MCP design
ComfyMcpClient owns exactly one comfy-mcp child process. Lifecycle is NEW -> CONNECTING -> CONNECTED -> CLOSED. A failed connect cleans resources and returns to NEW so callers may retry.

Every JSON-RPC request owns:
- a monotonically increasing id
- one CompletableFuture
- one timeout guard
- one pending-map entry

Completion, timeout, serialization/write failure and close all remove the exact pending entry.

stdout is the MCP protocol channel. stderr is drained concurrently into a capped diagnostic tail so the child cannot deadlock on pipe backpressure.

close() is idempotent, closes stdin, destroys the process, waits a bounded grace interval, escalates to destroyForcibly, fails all pending RPCs and shuts down the scheduler.

## Resource and memory boundaries
- CLI stdout/stderr capture limits are configurable; <=0 means unbounded.
- MCP frame, accumulated content and stderr diagnostic caps are configurable.
- Capped buffers retain a prefix and record truncation instead of growing without bound.
- No long-lived executor exists on the CLI path.
- MCP scheduler is daemon-backed and always shutdown by close().

## Security
- Credentials are expected through environment variables (COMFY_API_KEY, COMFY_BIN); no SDK logging of environment values.
- Arguments are passed as argv without a shell.
- Executable paths are treated as a literal executable, not parsed shell text.
- Network-exposure and spend-consent policy remains enforced by upstream comfy/comfy-mcp.

## Compatibility strategy
The source is kept Java 8 syntax-compatible wherever possible. Branch 3 changes only Jackson imports/API details and Maven/JDK baseline. Tests assert identical command argv and MCP behavior.
