# Comfy CLI / MCP parity proposal

## Goal
Bring comfy-java-sdk to parity with the documented Comfy CLI and the first-party local comfy-mcp surface while preserving one business contract across feature/1.0.x, feature/2.0.x and feature/3.0.x.

## Sources of truth
- https://docs.comfy.org/agent-tools/cli
- https://docs.comfy.org/comfy-cli/getting-started
- https://docs.comfy.org/comfy-cli/reference
- https://docs.comfy.org/agent-tools/mcp
- https://docs.comfy.org/agent-tools/skills
- https://github.com/Comfy-Org/comfy-cli
- https://github.com/Comfy-Org/comfy-mcp
- https://github.com/Comfy-Org/comfy-skills
- easy-4-java/codex-java-sdk hardening patterns

## Branch contract
All three feature branches expose the same Java-level business operations and tests. Only runtime baseline differences are allowed:
- 1.0.x: Java 8 / Jackson 2
- 2.0.x: Java 17 / Jackson 2
- 3.0.x: Java 21 / Jackson 3 / Maven 4

## Scope
1. Expand typed CLI wrappers for setup, cloud auth, routing, lifecycle, generate, workflows, jobs, templates, nodes, models, transfer, skills and tracking.
2. Preserve raw execute(...) and generic MCP callTool(...) escape hatches.
3. Add JSON envelope parsing and a doctor/environment report.
4. Harden subprocess execution: independent probe timeout, literal executable handling, bounded captures, UTF-8, environment merge.
5. Harden MCP stdio lifecycle: state machine, stderr draining, request timeout cleanup, write-failure cleanup, process reaping and idempotent close.
6. Add typed convenience methods for stable first-party local comfy-mcp tools.
7. Extend MCP content handling beyond text while retaining raw JSON.
8. Add branch-equivalent tests and production-readiness review.

## Non-goals
- Reimplement Comfy CLI business logic in Java.
- Freeze beta partner-model flags. Dynamic model-specific flags remain extensible.
- Replace comfy-mcp with a Java MCP server.
