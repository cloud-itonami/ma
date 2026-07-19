# Fund Management Workbench UI (etzhayyim-project-ma)

## Goals
- Provide one screen to monitor APQC + ISCO + ISIC actor workflow progression.
- Enable operator control for replay/hold/resume at actor-step level.
- Trace each customer/fund workflow with event and compliance evidence.
- Surface MCP connectivity status (`direct`, `sdk`, `adapter-required`) per actor.

## Main Screens
1. **Workflow Control Tower**
   - Swimlane by actor domain: APQC / ISCO / ISIC.
   - Real-time states: `queued`, `running`, `blocked`, `completed`.
2. **Portfolio Decision Board**
   - Proposal from `isco-2412-investment-analyst`.
   - Approval widget from `isco-1211-treasury-manager`.
3. **NAV & Cost Close Console**
   - Financial close status from `apqc-9-0-financial-management`.
   - Cost attribution details from `apqc-9-1-2-cost-accounting` via adapter actor.
4. **Compliance & Disclosure Desk**
   - ISIC rule snapshots (6430/6431/6530).
   - Audit package export for regulators and LP reporting.

## UI Component Model
- `ProcessTimeline.svelte`: ordered actor step timeline with SLA badges.
- `ActorCard.svelte`: actor profile, role, tool endpoints, health.
- `McpCoverageBadge.svelte`: readiness label (`direct`, `sdk`, `adapter-required`).
- `PolicyPanel.svelte`: policy checks resolved by ISIC gateway.
- `ExceptionDrawer.svelte`: blocked items with remediation actions.

## API Contracts (UI -> MA Orchestrator)
- `GET /api/ma/workflows/:id`
- `POST /api/ma/workflows/:id/actions/{hold|resume|replay}`
- `GET /api/ma/actors/health`
- `GET /api/ma/actors/mcp-readiness`
- `GET /api/ma/policies/evidence/:workflowId`

## Delivery Notes
- Start with read-only dashboard mode, then enable control actions.
- Use SSE/websocket event stream for process timeline refresh.
- Keep actor IDs identical to forked wasm actor IDs for traceability.
- Block "full-MCP mode" release until adapter-required actors are wrapped by MA MCP adapter.
