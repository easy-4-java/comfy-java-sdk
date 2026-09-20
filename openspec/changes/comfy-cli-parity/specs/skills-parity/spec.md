# Skills Parity Specification

## ADDED Requirements

### Requirement: Current local Skills management

The SDK MUST integrate with the Skills functionality shipped by current comfy-cli.

Required operations are install, uninstall, list, show, status and validate.

#### Scenario: Scoped install

- **WHEN** a caller installs skills with user/project scope, target filters, skill filters or dry-run
- **THEN** the SDK SHALL map those options to the current CLI contract without a shell.

#### Scenario: Validate third-party skill

- **WHEN** a caller supplies a skill directory or SKILL.md
- **THEN** the SDK SHALL expose the CLI validate operation and return structured output/result.

### Requirement: Do not freeze upstream skill bodies

The Java SDK MUST NOT vendor or hard-code current first-party skill contents.

#### Scenario: Skills change in a new CLI release

- **WHEN** the upstream bundled skill set changes
- **THEN** the SDK SHALL use skills list/show/status runtime behavior
- **AND** a Java SDK release SHALL NOT be required merely to update skill prose.

### Requirement: Distinguish local CLI skills from cloud plugin commands

The SDK documentation MUST distinguish local Skills bundled with comfy-cli from the separate Comfy-Org/comfy-skills cloud plugin/command repository.

#### Scenario: Local agent skills

- **WHEN** local comfy skills install behavior is requested
- **THEN** the CLI-bundled Skills contract SHALL be authoritative.
