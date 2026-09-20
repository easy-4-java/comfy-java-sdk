# Runtime Hardening and Production Safety Specification

## ADDED Requirements

### Requirement: Shell-free process execution

Normal CLI and MCP child execution MUST use argv-safe process APIs.

#### Scenario: Shell metacharacters in argument

- **WHEN** an argument contains whitespace, quotes, dollar, semicolon, ampersand or other shell syntax
- **THEN** it SHALL be delivered as one argv element
- **AND** SHALL NOT be interpreted by a shell.

### Requirement: Independent probe timeout

#### Scenario: Slow version command

- **WHEN** comfy --version exceeds localProbeTimeoutSeconds
- **THEN** probe SHALL terminate within probe policy
- **AND** normal command timeout SHALL remain unchanged.

### Requirement: Bounded CLI output

#### Scenario: Excessive output

- **WHEN** retained output exceeds configured cap
- **THEN** retained memory SHALL stay bounded
- **AND** truncation SHALL be marked
- **AND** the pipe SHALL continue draining to avoid deadlock.

### Requirement: Shared stream-drain deadline

#### Scenario: Both readers are slow to terminate

- **WHEN** stdout/stderr readers remain alive after child exit
- **THEN** they SHALL share one drain deadline
- **AND** cleanup time SHALL NOT multiply by stream count.

### Requirement: MCP stderr backpressure protection

#### Scenario: MCP floods stderr

- **WHEN** the child emits stderr larger than pipe capacity
- **THEN** RPCs SHALL continue
- **AND** retained diagnostics SHALL stay bounded.

### Requirement: Deterministic resource release

#### Scenario: Normal close

- **WHEN** a client closes
- **THEN** stdin, process, streams, reader threads, scheduler and pending Futures SHALL reach terminal cleanup.

#### Scenario: Failed connect

- **WHEN** initialize fails after process/thread creation
- **THEN** the same cleanup guarantees SHALL apply.

### Requirement: No unbounded request registry

#### Scenario: Repeated timeouts

- **WHEN** many calls time out
- **THEN** pending RPC count SHALL return to zero
- **AND** memory SHALL not grow with historical calls.

### Requirement: Security-sensitive diagnostics

#### Scenario: Environment contains API key

- **WHEN** environment contains credentials
- **THEN** logs/doctor/errors SHALL NOT print the secret value.

### Requirement: Dependency security gate

#### Scenario: Known vulnerable dependency

- **WHEN** policy identifies a vulnerability above the allowed threshold
- **THEN** production validation SHALL fail or require an explicit documented exception.

### Requirement: Real code coverage gate

#### Scenario: JaCoCo agent missing

- **WHEN** verify does not produce jacoco.exec/report
- **THEN** CI SHALL fail
- **AND** SHALL NOT claim coverage passed.

#### Scenario: Coverage below threshold

- **WHEN** measured coverage is below configured threshold
- **THEN** the build SHALL fail.
