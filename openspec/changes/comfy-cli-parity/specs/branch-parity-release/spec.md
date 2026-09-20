# Three-Branch Parity and Release Specification

## ADDED Requirements

### Requirement: One business contract

feature/1.0.x, feature/2.0.x and feature/3.0.x MUST expose equivalent business behavior.

#### Scenario: Public capability comparison

- **WHEN** public CLI/MCP/Skills capabilities are enumerated
- **THEN** the capability sets SHALL match after excluding documented compatibility adapters.

### Requirement: Allowed differences are explicit

Only JDK/Jackson/Maven compatibility differences are allowed.

#### Scenario: Shared source differs

- **WHEN** normalized shared implementation differs
- **THEN** CI SHALL fail unless the difference is in an explicit compatibility allow-list.

### Requirement: Identical logical tests

#### Scenario: Regression test added on canonical line

- **WHEN** a production bug adds a test to the canonical branch
- **THEN** an equivalent test SHALL be ported to the other maintained branches before completion.

### Requirement: Capability manifest

An identical machine-readable parity manifest MUST be stored on all three branches.

#### Scenario: New MCP wrapper

- **WHEN** a typed MCP wrapper is added
- **THEN** the manifest SHALL update once
- **AND** all three branches SHALL validate against the same expected tool set.

### Requirement: Patched branch-specific dependencies

Each branch MUST use a security-patched dependency baseline compatible with its runtime.

#### Scenario: Java 8 line

- **WHEN** a patched Jackson 2.18.x release is needed for a security fix
- **THEN** 1.x SHALL update within Java 8 constraints.

#### Scenario: Java 21 line

- **WHEN** a patched Jackson 3.x release is needed
- **THEN** 3.x SHALL update while preserving the same business behavior.

### Requirement: Collective production-ready verdict

#### Scenario: Two branches pass and one fails

- **WHEN** only two branches pass acceptance
- **THEN** the full-parity change SHALL remain incomplete.

### Requirement: Release evidence

Final evidence SHALL record per branch: final SHA, JDK/Jackson baseline, tests, real JaCoCo coverage, CI, dependency/security result, resource/leak review and residual risks.
