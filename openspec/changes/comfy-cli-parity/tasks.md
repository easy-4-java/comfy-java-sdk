# Tasks: Comfy Java SDK Full Parity

This task list is global to all maintained branches. A task is complete only when the required behavior is implemented consistently on feature/1.0.x, feature/2.0.x and feature/3.0.x unless explicitly marked canonical-line preparation.

## 0. Specification baseline

- [x] T0.1 Audit current first-party Comfy CLI, MCP and Skills sources.
- [x] T0.2 Record reviewed source snapshots and current 40-tool MCP catalog.
- [x] T0.3 Write full-parity architecture document.
- [x] T0.4 Expand OpenSpec proposal/design/tasks and domain specs.
- [ ] T0.5 Add identical machine-readable parity manifest to all three branches.

## 1. Canonical line

- [ ] T1.1 Treat feature/2.0.x as canonical behavior-development line for this change.
- [ ] T1.2 Reconcile useful historical hardening code without regressing the newer subprocess implementation.
- [ ] T1.3 Freeze public behavior contract before ports.

## 2. CLI full surface

- [ ] T2.1 Complete wrappers for current first-party domains including outdated, logs, system-stats, free, run-cli, agent-review, dependency and remaining cloud/set-default operations.
- [ ] T2.2 Complete SetupOptions.
- [ ] T2.3 Complete Cloud login/status/base-url/set-key options and secret policy.
- [ ] T2.4 Add typed lifecycle options for install/update/outdated/launch/stop/logs/system/free.
- [ ] T2.5 Expand RunOptions to prompt/set/notify/host/port/timeout/print-prompt/workflow-id/no-watch/allow-spend.
- [ ] T2.6 Complete jobs list/status/wait/watch/cancel options.
- [ ] T2.7 Complete template list/show/refresh/fetch/get/check/run-template.
- [ ] T2.8 Complete workflow list/get/save/delete/validate/compose/decompose/fragment/edit.
- [ ] T2.9 Complete node upstream/downstream/path/types/categories/widget-catalog/refresh.
- [ ] T2.10 Complete model download/status/cancel/remove/list/folder/search/show.
- [ ] T2.11 Complete custom-node/manager reachability and typed common lifecycle operations.
- [ ] T2.12 Preserve build/deploy/project/assets/agent/knowledge/code-search/pr-cache/tracking/auth family wrappers.
- [ ] T2.13 Preserve raw execute(String...) shell-free fallback.
- [ ] T2.14 Validate dynamic option names/values for NUL/flag-injection mistakes.

## 3. CLI discovery

- [ ] T3.1 Model --help-json.
- [ ] T3.2 Model --json discover.
- [ ] T3.3 Model --json discover --schemas-only.
- [ ] T3.4 Add ComfyCapabilityCatalog or equivalent.
- [ ] T3.5 Add installed-version compatibility reporting.
- [ ] T3.6 Add official discovery fixtures and opt-in real CLI contract test.

## 4. CLI streaming

- [ ] T4.1 Add streaming subprocess executor.
- [ ] T4.2 Add NDJSON ComfyCliEvent parsing with raw preservation.
- [ ] T4.3 Add streaming listener contract.
- [ ] T4.4 Add running stream-session handle.
- [ ] T4.5 Add completion Future, cancel, status and idempotent close.
- [ ] T4.6 Ensure listener failure deterministically cleans process.
- [ ] T4.7 Bound streaming diagnostics.
- [ ] T4.8 Support run JSON/JSON-stream and jobs watch.
- [ ] T4.9 Add cancellation/listener/partial-frame/malformed-NDJSON/final-envelope tests.

## 5. CLI hardening

- [ ] T5.1 Keep probe timeout independent.
- [ ] T5.2 Continuously drain stdout/stderr.
- [ ] T5.3 Bound retained stdout/stderr and expose truncation.
- [ ] T5.4 Keep one shared drain deadline.
- [ ] T5.5 Close stdin deterministically.
- [ ] T5.6 Reap timed-out child gracefully then forcibly.
- [ ] T5.7 Close read ends when descendants retain pipes.
- [ ] T5.8 Preserve caller interrupt status.
- [ ] T5.9 Add repeated process/timeout soak tests.

## 6. MCP 40-tool parity

- [ ] T6.1 Preserve initialize/initialized/tools-list/generic tools-call.
- [ ] T6.2 Add server_info.
- [ ] T6.3 Add auth_status/billing_status/auth_login.
- [ ] T6.4 Add run_workflow/generate_image/run_template.
- [ ] T6.5 Add list_partner_models/partner_model_schema/partner_generate/emit_partner_workflow.
- [ ] T6.6 Add job helpers status/wait/watch/cancel/queue.
- [ ] T6.7 Add system_stats/free_memory/fetch_outputs.
- [ ] T6.8 Add launch/stop/restart/update/version-switch/get-logs.
- [ ] T6.9 Add install_node.
- [ ] T6.10 Add discover/which/project.
- [ ] T6.11 Add template search/get/fetch.
- [ ] T6.12 Add nodes/node_dependencies/workflow_deps/search_models.
- [ ] T6.13 Add model download/status/wait/cancel and upload_file.
- [ ] T6.14 Add workflow validate/slots/notes/set-slot/vary.
- [ ] T6.15 Add parity test asserting reviewed 40-tool catalog.
- [ ] T6.16 Keep generic callTool for future tools.

## 7. MCP notifications / elicitation

- [ ] T7.1 Add ComfyMcpNotification.
- [ ] T7.2 Add generic notification listener.
- [ ] T7.3 Surface progress/log notifications.
- [ ] T7.4 Distinguish responses from server-originated requests.
- [ ] T7.5 Add optional elicitation handler.
- [ ] T7.6 Advertise elicitation only when configured.
- [ ] T7.7 Add fake-server notification/progress/elicitation tests.

## 8. MCP transport hardening

- [ ] T8.1 Centralize fatal transport cleanup.
- [ ] T8.2 On unexpected child/stdout end, transition away from CONNECTED.
- [ ] T8.3 Close writer/child streams on failed connect.
- [ ] T8.4 Join readers on failed connect and close.
- [ ] T8.5 Remove pending RPC on every terminal path.
- [ ] T8.6 Bound frame memory before JSON parsing.
- [ ] T8.7 Bound text aggregation and stderr tail.
- [ ] T8.8 Make close idempotent.
- [ ] T8.9 Define/test reconnect semantics.
- [ ] T8.10 Add concurrent and repeated connect/call/close soak tests.

## 9. MCP content

- [ ] T9.1 Preserve text.
- [ ] T9.2 Preserve image/audio MIME/data without eager duplicate copies.
- [ ] T9.3 Preserve resource/resource-link URI/raw JSON.
- [ ] T9.4 Preserve unknown future content through raw JSON.
- [ ] T9.5 Add content-size boundary tests.

## 10. Skills

- [ ] T10.1 Keep typed install.
- [ ] T10.2 Keep typed uninstall.
- [ ] T10.3 Keep typed list/show/status/validate.
- [ ] T10.4 Add typed scope/target/skill/dry-run options.
- [ ] T10.5 Do not vendor/freeze upstream SKILL.md bodies.
- [ ] T10.6 Test bundled/reference skill discovery through CLI contract.

## 11. Doctor / compatibility

- [ ] T11.1 Add comfy version/minimum support checks.
- [ ] T11.2 Check workspace/env/discover without leaking values.
- [ ] T11.3 Optionally check comfy-mcp initialize/tools-list.
- [ ] T11.4 Optionally check local ComfyUI server-info.
- [ ] T11.5 Report runtime CLI/MCP typed parity gaps.
- [ ] T11.6 Add ComfyCompatibilityReport.

## 12. Security / dependency readiness

- [ ] T12.1 Upgrade 1.x Jackson to current patched Java-8-compatible 2.18.x, at least 2.18.10 at this review baseline.
- [ ] T12.2 Keep 2.x on current patched Java-17-compatible Jackson 2.
- [ ] T12.3 Upgrade 3.x Jackson to current patched 3.2.x, at least 3.2.2 at this review baseline.
- [ ] T12.4 Add automated dependency vulnerability check.
- [ ] T12.5 Audit transitive CVEs.
- [ ] T12.6 Verify no secret/environment values are logged.
- [ ] T12.7 Verify argv diagnostics avoid/redact secrets.
- [ ] T12.8 Verify no helper introduces shell injection.
- [ ] T12.9 Verify confirmation-sensitive MCP wrappers preserve upstream gates.

## 13. Coverage / CI

- [ ] T13.1 Fix Surefire argLine so JaCoCo prepare-agent survives.
- [ ] T13.2 Fail when jacoco.exec/report is missing.
- [ ] T13.3 Make coverage gate halt build on failure.
- [ ] T13.4 Upload working JaCoCo artifacts.
- [ ] T13.5 Keep Surefire reports on every branch.
- [ ] T13.6 Add resource/memory soak profile/stage.
- [ ] T13.7 Add static/dependency security stage per runtime baseline.

## 14. Three-branch parity

- [ ] T14.1 Port canonical implementation to feature/1.0.x.
- [ ] T14.2 Port canonical implementation to feature/3.0.x.
- [ ] T14.3 Keep identical logical tests/fixtures on all three.
- [ ] T14.4 Add identical parity manifest on all three.
- [ ] T14.5 Add normalized public API/capability comparison.
- [ ] T14.6 Add normalized shared-source comparison or allow-list.
- [ ] T14.7 Verify only JDK/Jackson/Maven differences remain.

## 15. Final production readiness

- [ ] T15.1 Clean Maven verify on JDK 8.
- [ ] T15.2 Clean Maven verify on JDK 17.
- [ ] T15.3 Clean Maven verify on JDK 21/Maven 4.
- [ ] T15.4 CLI process/resource leak review.
- [ ] T15.5 MCP process/thread/timer/Future leak review.
- [ ] T15.6 Bounded-memory review.
- [ ] T15.7 Concurrency/race/deadlock review.
- [ ] T15.8 Dependency/security review.
- [ ] T15.9 Verify real JaCoCo report/threshold.
- [ ] T15.10 Record branch SHAs, tests, CI, coverage, dependency audit and residual risks.
- [ ] T15.11 Do not declare production-ready until all three branches pass the same gates.
