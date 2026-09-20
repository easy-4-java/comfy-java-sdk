# CLI Full-Surface Parity Specification

## ADDED Requirements

### Requirement: Complete first-party command reachability

The SDK MUST provide shell-free Java reachability for every current first-party comfy-cli command family.

At minimum, the maintained surface SHALL include setup, install, update, outdated, launch, stop, logs, run, validate, upload, download, system-stats, free, which, env, discover, set-default, generate, run-template, preview, jobs, templates, workflow, node, nodes, model, models, cloud, auth, project, assets, knowledge, build, deploy, agent, manager, pr-cache, code-search, tracking, skills, feedback, agent-review, dependency and standalone.

#### Scenario: Current stable command has no dedicated typed method

- **WHEN** the installed CLI exposes a current stable command family
- **THEN** the SDK SHALL provide at least a direct family wrapper accepting argv-safe arguments
- **AND** the caller SHALL NOT need a shell command string.

#### Scenario: Future CLI command exists before SDK release

- **WHEN** a caller needs a command not yet modeled
- **THEN** execute(String...) SHALL remain available
- **AND** arguments SHALL be passed directly as argv without a shell.

### Requirement: Typed stable options

High-frequency stable commands MUST expose Java option/request objects.

#### Scenario: Run workflow programmatically

- **WHEN** a caller configures workflow/prompt, wait, route, timeout, no-watch, spend consent or other stable run options
- **THEN** the SDK SHALL construct the current CLI argv
- **AND** validate SDK-owned invariants before launch.

#### Scenario: Reuse option object

- **WHEN** a helper needs to force machine-readable output
- **THEN** it SHALL copy caller-owned mutable options
- **AND** SHALL NOT mutate the original.

### Requirement: Runtime discovery contract

The SDK MUST expose the CLI self-describing interfaces.

#### Scenario: Discover installed CLI surface

- **WHEN** the caller requests CLI capabilities
- **THEN** the SDK SHALL invoke/parse --help-json and discover
- **AND** preserve unknown fields.

#### Scenario: Installed CLI lacks modeled command

- **WHEN** runtime discovery shows a command unavailable
- **THEN** compatibility reporting SHALL identify the mismatch
- **AND** typed execution SHOULD fail with a compatibility error rather than a misleading transport error.

### Requirement: Machine JSON envelope

The SDK MUST preserve the global JSON envelope contract.

#### Scenario: Successful envelope

- **WHEN** a JSON command returns ok=true
- **THEN** command/version/where/data and raw JSON SHALL be available.

#### Scenario: Business error envelope

- **WHEN** the CLI returns ok=false
- **THEN** structured error/hint SHALL be preserved
- **AND** it SHALL be distinguishable from malformed JSON and OS process failure.
