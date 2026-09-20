# Design: Comfy Java SDK Full Parity

## 1. Architecture

~~~text
Application
  -> ComfyClient
      -> CLI route: ComfyCli -> executor/stream session -> comfy
      -> MCP route: ComfyMcpClient -> stdio JSON-RPC -> comfy-mcp -> comfy
~~~

The Python CLI and MCP server remain authoritative. Java adds type-safe requests, process ownership, async/streaming abstractions, compatibility discovery, structured errors/results, bounded memory, deterministic cleanup and production diagnostics.

## 2. Canonical implementation

Use feature/2.0.x as the canonical behavior-development line because it currently contains the newest subprocess and MCP hardening.

After canonical behavior is green:

1. back-port identical behavior to feature/1.0.x,
2. forward-port identical behavior to feature/3.0.x,
3. normalize allowed Jackson/JDK differences and compare,
4. run the same fixtures on all three.

No feature is complete until all three lines pass.

## 3. CLI API

### Three layers

A. Typed stable API for high-frequency stable commands.

B. Command-family API so each current first-party family is reachable without a shell.

C. Generic execute(String...) for future/unknown commands.

Typed option objects remain Java 8-compatible and copyable.

### Capability discovery

Add a runtime surface model populated from:

~~~text
comfy --help-json
comfy --json discover
comfy --json discover --schemas-only
~~~

It records current CLI version, command/subcommand/schema availability, error-code contract, typed-wrapper availability and generic fallback requirements.

### Machine envelope

ComfyJsonEnvelope retains stable fields, data, error and raw JSON. Truncated machine output must never be accepted as a valid envelope.

## 4. CLI streaming

The synchronous executor remains correct for short commands. Long-running run/jobs/download/build/deploy operations need incremental output.

~~~text
ComfyCliStreamExecutor
  -> Process
  -> stdout reader -> NDJSON decoder -> ComfyCliEvent -> listener
  -> stderr reader -> listener/bounded diagnostic tail
  -> completion future
~~~

Required invariants:

- events arrive before process exit,
- listener failures do not leak the child,
- cancellation kills/reaps the child,
- close is idempotent,
- stdout/stderr stay drained,
- retained memory is bounded,
- final envelope/error remains queryable.

Public behavior must remain Java 8-compatible through ordinary threads and CompletableFuture.

## 5. MCP lifecycle

~~~text
NEW
 |
 v
CONNECTING ----failure----> NEW
 |
 v
CONNECTED ----transport failure----> NEW
 |
 +-------------------------------> CLOSED
NEW -----------------------------> CLOSED
~~~

CLOSED is terminal.

Unexpected child death must transition away from CONNECTED.

Introduce one internal fatal-transport cleanup path responsible for:

1. transition state,
2. fail pending RPCs,
3. close writer,
4. destroy/reap child,
5. close child streams,
6. stop/join readers,
7. clear references.

Use it for stdout EOF, frame overflow, write failure, failed initialize and close.

## 6. MCP RPC registry

pendingRpcs contains active calls only.

~~~text
allocate id
put(id, future)
schedule timeout
write
 |
 +-- response -> remove -> complete
 +-- rpc error -> remove -> exceptional complete
 +-- timeout -> remove -> exceptional complete
 +-- write error -> remove -> exceptional complete
 +-- transport close -> failAllPending
~~~

Every request has one id, Future, pending entry and timeout guard.

## 7. MCP complete tool facade

The reviewed server exposes 40 first-party tools. Each receives a direct convenience method while generic callTool remains.

Use stable Java types for stable inputs and forward-compatible Map/DTO structures where upstream parameters are intentionally dynamic.

## 8. MCP notifications and elicitation

Frames must be classified as:

- response: id plus result/error,
- server request: id plus method,
- notification: method without id.

Add ComfyMcpNotification and ComfyMcpListener for generic/progress notifications.

If elicitation support is configured, add ComfyMcpElicitationHandler and advertise the capability. Otherwise do not advertise it.

## 9. MCP content

ComfyMcpContent keeps type, text, MIME, URI, encoded data accessor and raw JSON. Avoid eagerly duplicating large encoded payloads.

## 10. Skills

Treat comfy-cli as the source of truth for local skills.

Typed operations:

- install,
- uninstall,
- list,
- show,
- status,
- validate.

Options cover user/project scope, repeated target, repeated skill and dry-run.

Do not vendor skill contents.

## 11. Memory boundaries

CLI configuration covers stdout cap, stderr cap, command timeout, probe timeout, process shutdown grace and one shared stream-drain deadline.

MCP configuration covers frame chars, concatenated text chars, stderr tail chars, request timeout, initialize timeout and shutdown grace.

All limits are validated and all unbounded semantics are explicit.

## 12. Security

Use argv-safe ProcessBuilder execution and never concatenate shell command strings.

Prefer environment variables for secrets, never log environment values, and redact/avoid secret-bearing argv in diagnostics.

Do not silently bypass upstream confirmation gates for spend, network exposure, node installation, version switching/update-all or killing untracked listeners.

## 13. Doctor / compatibility

Expand doctor into:

- CLI binary/version check,
- workspace/env/discover check,
- minimum-supported version check,
- optional comfy-mcp initialize/tools-list check,
- optional local ComfyUI server-info check,
- runtime CLI/MCP typed-parity report.

Diagnostics contain no secret values.

## 14. CI / branch parity

JaCoCo prepare-agent must reach the forked test JVM. CI fails if jacoco.exec/report is missing or coverage is below threshold. haltOnFailure is true for production validation.

Commit one identical parity manifest on all three branches.

Each branch validates CLI families, MCP tools, Skills and lifecycle invariants against that manifest.

A normalized comparison may fetch other branches and ignore only Jackson imports, JDK adapters, POM/dependency/build differences.

## 15. Dependencies

Baseline targets at review time:

- 1.x: Jackson at least 2.18.10 on Java 8,
- 2.x: current patched Jackson 2 release compatible with Java 17,
- 3.x: Jackson at least 3.2.2.

Revalidate exact patch versions when implementing.

## 16. Tests

Unit tests cover option builders, argv, parsing and validation.

Hermetic process fixtures cover stdout/stderr, huge output, timeout, inherited pipes, stdin, UTF-8 and early exit.

Fake MCP E2E covers initialize, all typed wrapper request shapes, generic calls, concurrency, timeout, stderr flood, child crash, malformed/oversized frames, mixed content, notifications and elicitation if enabled.

Soak tests assert zero pending RPCs, no live child, no accumulating reader/timer threads and bounded diagnostics.
