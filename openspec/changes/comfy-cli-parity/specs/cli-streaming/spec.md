# CLI Streaming and Async Specification

## ADDED Requirements

### Requirement: Incremental stdout processing

Long-running CLI operations MUST be consumable before process exit.

#### Scenario: NDJSON run stream

- **WHEN** comfy run --json or --json-stream emits events incrementally
- **THEN** the SDK SHALL parse complete frames as they arrive
- **AND** deliver them to a listener without waiting for exit.

### Requirement: Async session lifecycle

The SDK MUST expose a running-session abstraction with completion and cancellation.

#### Scenario: Cancel running command

- **WHEN** the caller cancels a streaming session
- **THEN** stdin SHALL close
- **AND** the child SHALL be terminated/reaped with bounded grace
- **AND** stdout/stderr readers SHALL be released
- **AND** the completion Future SHALL become terminal.

#### Scenario: Close twice

- **WHEN** a streaming session closes multiple times
- **THEN** cleanup SHALL be idempotent.

### Requirement: Bounded streaming memory

Streaming MUST NOT reintroduce unbounded output buffering.

#### Scenario: Endless progress

- **WHEN** output exceeds diagnostic limits
- **THEN** listener delivery MAY continue
- **BUT** retained SDK buffers SHALL remain bounded
- **AND** truncation SHALL be observable where retained data exists.

### Requirement: Listener isolation

User callbacks MUST NOT leak process resources.

#### Scenario: Listener throws

- **WHEN** an event listener throws
- **THEN** documented failure/cancellation policy SHALL run
- **AND** child/helper resources SHALL still be released.

### Requirement: Java 8-compatible contract

- **WHEN** the same streaming tests run on 1.x, 2.x and 3.x
- **THEN** event, cancellation, completion and cleanup semantics SHALL match.
