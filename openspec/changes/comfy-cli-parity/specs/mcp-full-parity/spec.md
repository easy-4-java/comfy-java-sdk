# MCP Full Parity Specification

## ADDED Requirements

### Requirement: Complete current first-party tool facade

The SDK MUST provide typed convenience access to the complete reviewed local comfy-mcp first-party tool catalog while preserving generic callTool.

The reviewed catalog contains server_info, auth_status, billing_status, auth_login, run_workflow, generate_image, list_partner_models, partner_model_schema, partner_generate, emit_partner_workflow, run_template, job, system_stats, free_memory, fetch_outputs, launch_comfyui, stop_comfyui, restart_comfyui, update_comfyui, switch_comfyui_version, install_node, get_logs, discover, which, project, search_templates, get_template, fetch_template, nodes, node_dependencies, workflow_deps, search_models, download_model, download, upload_file, validate_workflow, list_workflow_slots, list_workflow_notes, set_workflow_slot and vary_workflow.

#### Scenario: Current tool list

- **WHEN** a parity fixture exposes the reviewed 40 tools
- **THEN** each tool SHALL have a direct Java convenience path
- **AND** tools/list SHALL remain available for runtime discovery.

#### Scenario: Future tool

- **WHEN** the server advertises a tool unknown to this SDK release
- **THEN** the caller SHALL still be able to invoke it through generic callTool.

### Requirement: Deterministic transport state

MCP state MUST reflect the real child transport.

#### Scenario: Child exits unexpectedly

- **WHEN** connected comfy-mcp exits or stdout closes unexpectedly
- **THEN** pending RPCs SHALL fail
- **AND** state SHALL transition away from CONNECTED
- **AND** transport resources SHALL be cleaned
- **AND** documented reconnect behavior SHALL remain possible unless explicitly closed.

### Requirement: Exact pending RPC cleanup

Every request MUST be removed from the pending registry on every terminal path.

#### Scenario: Timeout

- **WHEN** a tool request times out
- **THEN** its exact pending entry SHALL be removed
- **AND** the Future SHALL complete exceptionally.

#### Scenario: Write failure

- **WHEN** serialization or stdin write fails
- **THEN** the pending entry SHALL be removed
- **AND** failure SHALL surface immediately.

### Requirement: Notification and progress support

The SDK MUST surface server notifications required for long-running tools.

#### Scenario: Progress notification

- **WHEN** the server emits MCP progress
- **THEN** a registered listener SHALL receive structured/raw notification data
- **AND** it SHALL NOT be mistaken for an RPC response.

#### Scenario: Unknown notification

- **WHEN** a future unknown notification arrives
- **THEN** transport SHALL remain healthy
- **AND** a generic callback MAY receive the raw payload.

### Requirement: Honest client capabilities

The client MUST NOT advertise MCP capabilities it cannot service.

#### Scenario: No elicitation handler

- **WHEN** no elicitation handler is configured
- **THEN** initialize SHALL NOT advertise elicitation support.

#### Scenario: Elicitation enabled

- **WHEN** elicitation support is configured
- **THEN** server-originated requests SHALL route to the handler
- **AND** the client SHALL send the matching JSON-RPC result/error.

### Requirement: Structured content preservation

#### Scenario: Image/audio/resource content

- **WHEN** a tool returns non-text content
- **THEN** type, MIME/URI/data access and raw JSON SHALL remain available
- **AND** text concatenation SHALL include only appropriate text.

#### Scenario: Large encoded content

- **WHEN** content includes a large encoded payload
- **THEN** unnecessary eager duplicate copies SHALL be avoided
- **AND** configured memory boundaries SHALL remain effective.
