# comfy-java-sdk Full Parity Architecture

> Status: target architecture and implementation baseline
> Change set: openspec/changes/comfy-cli-parity
> Branch contract: feature/1.0.x, feature/2.0.x, feature/3.0.x MUST expose the same business behavior.

## 1. Purpose

comfy-java-sdk is the Java integration SDK for the first-party Comfy agent toolchain. Its primary responsibility is full functional integration with comfy-cli and local comfy-mcp, not reimplementation of ComfyUI business logic.

The SDK has two authoritative runtime routes:

~~~text
Java Application
      |
      +----------------------+----------------------+
      |                                             |
      v                                             v
 ComfyClient / ComfyCli                      ComfyMcpClient
      |                                             |
      v                                             v
  comfy-cli process                         MCP stdio JSON-RPC
      |                                             |
      +----------------------+----------------------+
                             |
                             v
                    Local / Cloud Comfy
~~~

The Java SDK MUST stay thin around upstream behavior while adding Java-grade type safety, lifecycle management, streaming, failure recovery, diagnostics, compatibility checks, and production safety.

## 2. Authoritative sources

The following are the source of truth for capability parity:

- https://docs.comfy.org/agent-tools/cli
- https://docs.comfy.org/agent-tools/mcp
- https://docs.comfy.org/agent-tools/skills
- https://docs.comfy.org/comfy-cli/getting-started
- https://docs.comfy.org/comfy-cli/reference
- https://github.com/Comfy-Org/comfy-cli
- https://github.com/Comfy-Org/comfy-mcp
- https://github.com/Comfy-Org/comfy-skills

Baseline source snapshots reviewed on 2026-09-20:

- Comfy-Org/comfy-cli: b08adc50cca24f2d29d2115e80719cb8299e6ddf
- Comfy-Org/comfy-mcp: e5f768d31de21ea32829381cebdda5336492a8ac
- Comfy-Org/comfy-skills: d50722a53585d0ab4fd1909fd59294b978aad449

The SDK MUST prefer runtime discovery through comfy discover, comfy --help-json and MCP tools/list over permanently hard-coding volatile model/template/node catalogs.

## 3. Three-branch contract

The maintained branches are compatibility lines, not feature generations.

| Branch | Runtime | JSON | Build |
| --- | --- | --- | --- |
| feature/1.0.x | Java 8 | Jackson 2 | Maven 3-compatible POM |
| feature/2.0.x | Java 17 | Jackson 2 | Maven 3-compatible POM |
| feature/3.0.x | Java 21 | Jackson 3 | Maven 4 / POM 4.1 |

All public business operations, command mappings, MCP mappings, validation rules, error semantics, streaming semantics, resource cleanup rules and logical tests MUST remain behaviorally equivalent.

Allowed differences are restricted to JDK-compatible implementation details, Jackson 2/3 API/package differences, Maven model/plugin mechanics and dependency coordinates required by the runtime baseline.

## 4. Current state and gaps

feature/2.0.x is currently the most advanced implementation and should be treated as the canonical behavior-development line for the next parity cycle. It already contains:

- independent CLI probe timeout,
- bounded stdout/stderr capture,
- explicit subprocess pipe ownership,
- one shared stream-drain deadline,
- broad CLI family wrappers,
- Skills install/uninstall/list/show/status/validate,
- JSON envelope parsing,
- doctor report,
- MCP lifecycle state,
- stderr draining,
- pending RPC timeout cleanup,
- structured non-text MCP content,
- hardening regression tests.

The remaining work is completion and convergence rather than a rewrite.

Primary gaps:

1. feature/1.0.x and feature/3.0.x are not synchronized with feature/2.0.x.
2. CLI wrappers still trail the complete current first-party command tree and stable options.
3. Long-running CLI operations still need true incremental NDJSON streaming and cancellation.
4. The current feature/2.0.x typed MCP facade exposes only part of the current first-party MCP catalog.
5. MCP notifications/progress are ignored instead of surfaced.
6. MCP server-to-client requests such as elicitation are not supported.
7. Unexpected MCP child termination needs centralized state transition and deterministic transport cleanup.
8. JaCoCo is configured but currently ineffective because Surefire overwrites the injected agent argument.
9. Dependency/security auditing and cross-branch parity are not release gates yet.

## 5. Target architecture

~~~text
io.github.easy4j.comfy
|
+-- ComfyClient
+-- ComfyClientConfig
+-- ComfyException
+-- capability/
|   +-- ComfyCapabilityCatalog
|   +-- ComfyCliSurface
|   +-- ComfyCompatibilityReport
|
+-- cli/
|   +-- ComfyCli
|   +-- ComfyCliExecutor
|   +-- ComfyCliResult
|   +-- ComfyCliStreamExecutor
|   +-- ComfyCliStreamSession
|   +-- ComfyCliStreamListener
|   +-- typed options
|
+-- model/
|   +-- ComfyJsonEnvelope
|   +-- ComfyCliEvent
|   +-- ComfyDoctorReport
|
+-- mcp/
|   +-- ComfyMcpClient
|   +-- ComfyMcpConfig
|   +-- ComfyMcpTool
|   +-- ComfyMcpCallResult
|   +-- ComfyMcpContent
|   +-- ComfyMcpNotification
|   +-- ComfyMcpListener
|   +-- ComfyMcpElicitationHandler
|
+-- skills/
    +-- typed Skills options/facade when useful
~~~

Exact package decomposition may remain compact, but the behavioral contract is mandatory.

## 6. CLI full-surface parity

Full parity does not mean every upstream flag must be frozen forever in Java source. Comfy CLI evolves quickly and exposes dynamic model/provider schemas.

The SDK SHALL implement three complementary levels:

1. Typed stable APIs for stable/high-frequency commands and options.
2. Command-family APIs so every first-party command group is reachable without a shell.
3. Discovery-driven generic invocation so new upstream commands work before a new SDK release.

The maintained Java surface MUST cover the current official domains:

~~~text
setup
install
update
outdated
launch
stop
logs
run
validate
upload
download
system-stats
free
which
env
discover
set-default

generate
run-template
preview
jobs
templates
workflow
node
nodes
model
models

cloud
auth

project
assets
knowledge
build
deploy
agent

manager
pr-cache
code-search
tracking
skills

feedback
agent-review
dependency
standalone
~~~

### Important typed gaps

Setup must cover where, project-dir, non-interactive, skip-skills, skip-verify and a safe policy for api-key.

Cloud must cover login/no-browser/timeout, logout, whoami, status/plans, set-base-url/clear and set-key with explicit secret-handling warnings.

Lifecycle typed options should cover install target/version/hardware/dependency behavior, update target/version/stash/error policy, outdated refresh, launch background/frontend PR/extra args, stop port/dry-run, logs tail/port, system stats and free memory/model unloading.

RunOptions must grow to current execution semantics:

- workflow path or prompt-driven default workflow,
- repeatable --set,
- wait,
- notify,
- verbose,
- host/port,
- event timeout,
- local/cloud route,
- JSON / JSON-stream,
- print-prompt,
- workflow id,
- --no-watch,
- spend consent.

For programmatic/agent callers, --no-watch should be easy to enable because an unnecessary detached watcher can retain credentials and process resources after the parent exits.

Jobs typed support should cover list/status/wait/watch/cancel.

Templates typed support should cover list/search filters, show, refresh, fetch, get, check and run-template.

Workflow typed support should cover slots, set-slot, vary, notes, print, list/get/save/delete, validate, compose/decompose and current fragment/edit commands.

Nodes typed support should cover list/show/search/upstream/downstream/path/types/categories/widget-catalog/refresh.

Model and custom-node management should provide direct family access plus typed common operations for model download/remove/list, download status/cancel, discovery/folders/search/show, custom-node lifecycle, snapshots, manager lifecycle, registry/publish/scaffold/bisect where stable.

## 7. Discovery-driven parity guard

The SDK SHALL use upstream self-description:

~~~text
comfy --help-json
comfy --json discover
comfy --json discover --schemas-only
~~~

Add a capability model that records:

- current CLI version,
- commands/subcommands,
- schemas,
- error-code contract,
- whether a typed Java wrapper is available,
- whether execution must fall back to generic invocation.

A compatibility test SHOULD compare an official discovery fixture or opt-in installed CLI against an identical SDK parity manifest.

## 8. CLI streaming and async execution

Buffered synchronous execution is insufficient for full integration because run --json, jobs watch, model downloads, build/deploy operations and other long-running commands emit useful progress incrementally.

Required flow:

~~~text
Process stdout
   |
   +--> NDJSON decoder --> ComfyCliEvent --> listener/callback
   |
   +--> final envelope --> completion future
~~~

The SDK SHALL provide:

- executeAsync,
- streaming stdout callbacks,
- bounded streaming stderr diagnostics,
- NDJSON event parsing,
- final envelope parsing,
- cancellation,
- process termination,
- bounded buffers,
- CompletableFuture completion,
- idempotent close/cancel.

Java 8 compatibility MUST use ordinary threads/executors and CompletableFuture; no virtual-thread requirement may leak into the shared contract.

## 9. MCP full parity

The reviewed comfy-mcp baseline exposes 40 first-party tools:

~~~text
server_info
auth_status
billing_status
auth_login

run_workflow
generate_image
list_partner_models
partner_model_schema
partner_generate
emit_partner_workflow
run_template

job
system_stats
free_memory
fetch_outputs

launch_comfyui
stop_comfyui
restart_comfyui
update_comfyui
switch_comfyui_version
install_node
get_logs

discover
which
project

search_templates
get_template
fetch_template

nodes
node_dependencies
workflow_deps
search_models

download_model
download
upload_file

validate_workflow
list_workflow_slots
list_workflow_notes
set_workflow_slot
vary_workflow
~~~

The Java typed facade MUST expose all current tools while preserving generic callTool and tools/list so future tools remain callable before a new SDK release.

### MCP lifecycle

Required state behavior:

~~~text
NEW -> CONNECTING -> CONNECTED
 ^          |            |
 |          v            v
 +------ failure      transport failure
                         |
                         v
                        NEW

CONNECTED/NEW -> CLOSED
~~~

A fatal transport failure MUST fail pending RPCs, close stdin, destroy/reap the child, close/join readers, clear references, transition away from CONNECTED and allow documented reconnect behavior unless explicitly closed.

### Pending RPC invariant

Every request owns one id, one CompletableFuture, one pending-map entry and one timeout guard.

All terminal paths MUST remove the exact pending entry: response, JSON-RPC error, timeout, serialization/write failure, child exit, oversized frame and close.

### MCP notifications/progress

Ignoring every notification frame is not full MCP integration.

Add a listener contract able to receive generic notifications and progress/log/tool progress. Unknown notifications remain forward-compatible and MUST NOT fail the transport.

### MCP elicitation/server requests

Current comfy-mcp can use MCP elicitation for confirmation-sensitive operations when the client advertises support.

The SDK SHALL either:

1. not advertise elicitation and require explicit Java confirmation parameters; or
2. advertise elicitation only when a real ComfyMcpElicitationHandler is configured.

If enabled, server-originated JSON-RPC requests must be distinguished from responses and routed to the handler rather than discarded as unknown ids.

### MCP content

Preserve text, image, audio, resource/resource-link, MIME, URI, encoded data and raw JSON. Avoid eager duplicate copies of large base64 payloads.

## 10. Skills integration

Current local comfy-cli ships its own skills. Comfy-Org/comfy-skills is now primarily the Comfy Cloud plugin/command repository.

Current bundled CLI skills reviewed:

~~~text
comfy
comfy-debug
comfy-relay
comfy-director
comfy-build
comfy-deploy
comfy-custom-nodes
~~~

Current reference skills reviewed:

~~~text
comfy-build-authoring
comfy-build-pins
comfy-build-failures
comfy-deploy-failures
comfy-agent-permissions
~~~

The SDK MUST NOT freeze skill file content or dynamic catalogs.

Required skill operations:

- install,
- uninstall,
- list,
- show,
- status,
- validate.

Install/uninstall options must support scope, target, skill selection and dry-run.

## 11. JSON envelopes and doctor

The CLI machine contract is a first-class SDK boundary. Preserve stable fields such as schema, type, ok, command, version, where, data and error while retaining raw JSON.

Malformed JSON, truncated JSON and ok=false must be distinguishable.

doctor() SHALL evolve into a compatibility report capable of checking:

- comfy available,
- comfy-mcp available,
- CLI version/minimum supported version,
- MCP server version,
- workspace resolution,
- environment/discover health,
- optional local ComfyUI reachability,
- CLI surface discovery,
- MCP tools/list,
- missing typed parity,
- branch/runtime metadata.

No secret values may be copied into diagnostics.

## 12. Production resource safety

CLI subprocess calls MUST:

- avoid a shell by default,
- treat executable path literally,
- merge environment overrides without dropping parent PATH,
- close stdin,
- continuously drain stdout/stderr,
- cap retained stdout/stderr,
- apply command timeout,
- apply independent probe timeout,
- terminate and escalate to force-kill,
- use one shared drain deadline,
- close inherited-pipe read ends when descendants keep them open,
- join helper threads with bounded waits.

MCP clients MUST:

- drain stderr concurrently,
- bound stderr diagnostics,
- bound line/frame memory before parse,
- bound accumulated content,
- close writer,
- reap child,
- join readers,
- shut down timer/scheduler,
- clear all pending RPCs,
- make close idempotent.

Production validation SHOULD repeat CLI calls/timeouts and MCP connect/call/close, timeout, crash, oversized-frame, stderr-flood, large-content and concurrent-call scenarios. Assertions should verify stable named-thread counts, zero pending RPCs, dead child processes and bounded output.

## 13. Security

1. No shell concatenation for ordinary execution.
2. No logging of environment values/API keys.
3. Prefer environment variables for secrets where upstream supports them.
4. Errors must avoid dumping secret-bearing argv/environment.
5. MCP stderr and CLI stdout/stderr are bounded.
6. Dynamic option names reject NUL/invalid flag injection.
7. Paths remain argv items, never shell fragments.
8. Network exposure, spend, version switch/update-all and node-install confirmations preserve upstream safety.
9. Dependencies receive automated vulnerability checks.

Dependency baseline to fix at the time of this review:

- 1.x should move Jackson 2.18.9 to at least 2.18.10.
- 2.x currently uses Jackson 2.22.2.
- 3.x should move Jackson 3.2.1 to at least 3.2.2.

Exact patch versions must be revalidated at implementation time.

## 14. Coverage and CI

The current POMs configure JaCoCo but Surefire supplies a fixed argLine, preventing the JaCoCo prepare-agent value from reaching the forked test JVM. Current CI therefore reports missing jacoco.exec.

Production CI SHALL:

1. preserve JaCoCo-injected JVM arguments,
2. generate coverage report,
3. fail when coverage data/report is missing,
4. enforce the agreed threshold with haltOnFailure=true,
5. upload test/coverage artifacts,
6. execute the correct JDK per branch,
7. run branch-parity contract tests,
8. run dependency/security checks.

Coverage percentage is a guard, not a substitute for failure-path tests.

## 15. Cross-branch parity

Three-branch consistency MUST become machine-verifiable.

Layer 1: commit an identical capability manifest to all branches containing CLI families, typed stable operations, MCP tools, Skills operations and behavioral invariants.

Layer 2: optionally fetch the other branches in CI and normalize Jackson imports, JDK compatibility helpers and POM/dependency differences. Shared implementation/tests should compare equal or be covered by an explicit allow-list.

Layer 3: use the same fake CLI fixtures and fake MCP server scenarios on all branches.

## 16. Test matrix

Every branch must run the same logical scenarios:

CLI:
- argv mapping,
- spaces/unicode/paths,
- environment merge,
- stdin EOF,
- non-zero exits,
- probe timeout,
- command timeout,
- inherited descendant pipes,
- large output truncation,
- UTF-8,
- interruption,
- streaming listener/cancel.

CLI parity:
- command manifest,
- setup/cloud/lifecycle,
- run/jobs/templates/workflow,
- nodes/models/custom nodes,
- build/deploy/project/assets,
- skills,
- discovery/help JSON.

MCP:
- initialize,
- tools/list,
- all typed tool request shapes,
- generic fallback,
- business isError,
- JSON-RPC error,
- concurrent calls,
- timeout/write cleanup,
- child exit,
- stderr flood,
- malformed/oversized frame,
- mixed content,
- notification/progress,
- elicitation if enabled,
- close/reconnect.

Production soak:
- repeated CLI calls,
- repeated MCP lifecycle,
- concurrent load,
- bounded heap/output,
- thread cleanup.

## 17. Implementation order

Use feature/2.0.x as canonical behavior-development line.

~~~text
Phase A: finish canonical Java 17 behavior
    |
    +-- complete CLI surface
    +-- streaming API
    +-- complete all 40 MCP typed wrappers
    +-- notifications / optional elicitation
    +-- transport-failure state cleanup
    +-- coverage/security gates
    |
Phase B: back-port to Java 8 / Jackson 2
    |
Phase C: forward-port to Java 21 / Jackson 3
    |
Phase D: parity + production readiness validation on all three
~~~

No branch is considered complete until all three maintained feature branches pass the same behavioral acceptance suite.

## 18. Definition of done

comfy-java-sdk is production-ready only when:

- every current first-party CLI domain is reachable without shell construction,
- stable/high-frequency CLI operations have typed Java APIs,
- dynamic CLI surfaces remain reachable through discovery-driven generic invocation,
- streaming commands have incremental Java APIs,
- all current local comfy-mcp tools have typed convenience methods,
- generic MCP tools/list and callTool remain available,
- MCP notification/progress is surfaced,
- advertised MCP capabilities are actually implemented,
- all processes/threads/timers/streams/futures are deterministically released,
- output/frame/content memory is bounded,
- security/dependency audit passes,
- JaCoCo is genuinely active,
- all three branches have equivalent business behavior/tests,
- CI verifies branch parity automatically.
