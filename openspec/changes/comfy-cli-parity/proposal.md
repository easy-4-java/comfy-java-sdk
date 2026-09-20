# Change Proposal: Comfy Java SDK Full CLI / MCP / Skills Parity

## Why

comfy-java-sdk exists to provide complete Java integration with first-party comfy-cli and local comfy-mcp.

The current SDK already has a strong two-route foundation, but parity is incomplete:

- the three maintained branches are not yet behaviorally aligned,
- CLI coverage still trails the current first-party command tree,
- long-running CLI operations need real streaming/async APIs,
- the typed MCP facade does not yet expose the complete current tool catalog,
- MCP progress/notifications and optional elicitation are not surfaced,
- abnormal MCP child termination needs stronger state recovery,
- JaCoCo is configured but the agent is not actually reaching Surefire,
- dependency/security and cross-branch parity need automated release gates.

## Goal

Deliver one business contract for comfy-java-sdk that fully integrates:

1. current first-party comfy-cli,
2. current local comfy-mcp,
3. first-party CLI Skills management,
4. JSON envelope and streaming event contracts,
5. production-safe subprocess/MCP lifecycle behavior,
6. automated branch parity across Java 8, 17 and 21 lines.

## Sources of Truth

- https://docs.comfy.org/agent-tools/cli
- https://docs.comfy.org/agent-tools/mcp
- https://docs.comfy.org/agent-tools/skills
- https://docs.comfy.org/comfy-cli/getting-started
- https://docs.comfy.org/comfy-cli/reference
- https://github.com/Comfy-Org/comfy-cli
- https://github.com/Comfy-Org/comfy-mcp
- https://github.com/Comfy-Org/comfy-skills

Baseline source snapshots reviewed on 2026-09-20:

- Comfy-Org/comfy-cli b08adc50cca24f2d29d2115e80719cb8299e6ddf
- Comfy-Org/comfy-mcp e5f768d31de21ea32829381cebdda5336492a8ac
- Comfy-Org/comfy-skills d50722a53585d0ab4fd1909fd59294b978aad449

## Branch Contract

All maintained feature branches MUST expose the same Java-level business operations, semantics, safety behavior and logical test suite:

- feature/1.0.x: Java 8 / Jackson 2
- feature/2.0.x: Java 17 / Jackson 2
- feature/3.0.x: Java 21 / Jackson 3 / Maven 4

Only JDK/Jackson/Maven compatibility differences are permitted.

## Scope

### CLI

- Cover every current first-party command family.
- Add typed stable option objects for high-frequency operations.
- Preserve execute(String...) as the forward-compatible escape hatch.
- Use discover and --help-json as a runtime capability contract.
- Add true streaming/async execution for NDJSON and progress-producing commands.

### MCP

- Preserve generic tools/list and callTool.
- Add typed convenience methods for the complete current first-party local tool catalog.
- Harden state transitions, process failure recovery, timeout cleanup, stderr drain and process/thread/timer release.
- Preserve all MCP content types.
- Surface notifications/progress.
- Support elicitation only when a real handler is configured and advertised.

### Skills

- Support install, uninstall, list, show, status and validate.
- Support scope, target, skill selection and dry-run.
- Do not hard-code skill bodies or volatile model/template/node catalogs.

### Production readiness

- Fix effective JaCoCo execution.
- Add dependency/security audit.
- Add resource/memory soak tests.
- Add branch-parity CI and capability manifest.

## Non-goals

- Reimplement Comfy CLI business logic in Java.
- Replace comfy-mcp with a Java MCP server.
- Freeze dynamic partner model schemas into permanent Java enums.
- Hard-code currently available templates, models, node ids or skill bodies.
- Add hosted Comfy Cloud MCP transport unless separately specified.

## Success Criteria

The change is complete only when all three maintained branches:

- pass the same logical parity suite,
- expose the same public business surface,
- can reach the complete current CLI and local MCP capabilities,
- provide streaming for long-running CLI workflows,
- clean up every subprocess/thread/timer/Future on all terminal paths,
- enforce bounded memory,
- pass security/dependency checks,
- generate real JaCoCo coverage and enforce the configured threshold,
- pass automated cross-branch parity checks.
