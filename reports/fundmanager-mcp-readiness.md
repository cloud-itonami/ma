# Fundmanager MCP Readiness Report

- Evaluated actors: **9**
- Direct MCP-ready actors: **6**
- SDK MCP actors: **1**
- Adapter-required actors: **2**
- Verdict: **CONDITIONAL**

| Actor | MCP status | Evidence | Source path |
|---|---|---|---|
| `apqc-9-0-financial-management` | `sdk-mcp` | AddTool | `60-apps/etzhayyim-project-apqc/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-0-financial-management-cvaeukqn` |
| `apqc-9-1-2-cost-accounting` | `adapter-required` | no MCP marker found | `60-apps/etzhayyim-project-apqc/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-1-2-cost-accounting-hs5myyk4` |
| `apqc-9-4-accounts-receivable` | `adapter-required` | no MCP marker found | `60-apps/etzhayyim-project-apqc/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-4-accounts-receivable-sq8qt88a` |
| `isco-1211-treasury-manager` | `direct-mcp` | HTTP /api/mcp, tools/list | `60-apps/etzhayyim-project-open-isco/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-1211-treasury-manager-7df4q796` |
| `isco-2412-financial-and-investment-advisers` | `direct-mcp` | HTTP /api/mcp, tools/list | `60-apps/etzhayyim-project-open-isco/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-2412-financial-and-investment-advisers-hhddguqm` |
| `isco-2412-investment-analyst` | `direct-mcp` | HTTP /api/mcp, tools/list | `60-apps/etzhayyim-project-open-isco/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-2412-investment-analyst-vccbzmvf` |
| `isic-6430-trusts-funds` | `direct-mcp` | HTTP /api/mcp, tools/list, AddTool, /api/messages | `60-apps/etzhayyim-project-open-isic/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-64-643-6430-qkt6zyvr` |
| `isic-6431-mutual-funds` | `direct-mcp` | HTTP /api/mcp, tools/list, AddTool, /api/messages | `60-apps/etzhayyim-project-open-isic/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-64-643-6431-sfmtdkzd` |
| `isic-6530-pension-funding` | `direct-mcp` | HTTP /api/mcp, tools/list, AddTool, /api/messages | `60-apps/etzhayyim-project-open-isic/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-65-653-6530-rveryau2` |

## Required remediation

The following actors cannot be driven end-to-end via MCP as-is and need an MCP facade wrapper in `etzhayyim-project-ma`:
- `apqc-9-1-2-cost-accounting`
- `apqc-9-4-accounts-receivable`
