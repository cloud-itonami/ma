#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "projects/etzhayyim-project-ma/reports/fundmanager-mcp-readiness.md"

ACTORS = [
    ("apqc-9-0-financial-management", "projects/etzhayyim-project-apqc/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-0-financial-management-cvaeukqn"),
    ("apqc-9-1-2-cost-accounting", "projects/etzhayyim-project-apqc/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-1-2-cost-accounting-hs5myyk4"),
    ("apqc-9-4-accounts-receivable", "projects/etzhayyim-project-apqc/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-4-accounts-receivable-sq8qt88a"),
    ("isco-1211-treasury-manager", "projects/etzhayyim-project-open-isco/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-1211-treasury-manager-7df4q796"),
    ("isco-2412-financial-and-investment-advisers", "projects/etzhayyim-project-open-isco/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-2412-financial-and-investment-advisers-hhddguqm"),
    ("isco-2412-investment-analyst", "projects/etzhayyim-project-open-isco/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-2412-investment-analyst-vccbzmvf"),
    ("isic-6430-trusts-funds", "projects/etzhayyim-project-open-isic/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-64-643-6430-qkt6zyvr"),
    ("isic-6431-mutual-funds", "projects/etzhayyim-project-open-isic/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-64-643-6431-sfmtdkzd"),
    ("isic-6530-pension-funding", "projects/etzhayyim-project-open-isic/wasm/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-65-653-6530-rveryau2"),
]


def detect(path: Path) -> tuple[str, str]:
    go_files = list(path.rglob("*.go"))
    combined = "\n".join([f.read_text(encoding="utf-8", errors="ignore") for f in go_files])

    has_api_mcp = "/api/grpc" in combined
    has_tools_list = "tools/list" in combined or "list_tools" in combined
    has_add_tool = "AddTool(" in combined or "mcp.AddTool" in combined
    has_message_api = "/api/messages" in combined

    evidences = []
    if has_api_mcp:
        evidences.append("HTTP /api/grpc")
    if has_tools_list:
        evidences.append("tools/list")
    if has_add_tool:
        evidences.append("AddTool")
    if has_message_api:
        evidences.append("/api/messages")

    if has_api_mcp and has_tools_list:
        return "direct-mcp", ", ".join(evidences)
    if has_add_tool:
        return "sdk-mcp", ", ".join(evidences)
    return "adapter-required", ", ".join(evidences) if evidences else "no MCP marker found"


def main() -> None:
    rows = []
    gaps = 0
    for actor_id, rel in ACTORS:
        actor_path = ROOT / rel
        status, evidence = detect(actor_path)
        if status == "adapter-required":
            gaps += 1
        rows.append((actor_id, status, evidence, rel))

    verdict = "PASS" if gaps == 0 else "CONDITIONAL"

    lines = [
        "# Fundmanager MCP Readiness Report",
        "",
        f"- Evaluated actors: **{len(rows)}**",
        f"- Direct MCP-ready actors: **{sum(1 for r in rows if r[1] == 'direct-mcp')}**",
        f"- SDK MCP actors: **{sum(1 for r in rows if r[1] == 'sdk-mcp')}**",
        f"- Adapter-required actors: **{sum(1 for r in rows if r[1] == 'adapter-required')}**",
        f"- Verdict: **{verdict}**",
        "",
        "| Actor | MCP status | Evidence | Source path |",
        "|---|---|---|---|",
    ]
    for actor_id, status, evidence, rel in rows:
        lines.append(f"| `{actor_id}` | `{status}` | {evidence} | `{rel}` |")

    if gaps:
        lines.extend([
            "",
            "## Required remediation",
            "",
            "The following actors cannot be driven end-to-end via MCP as-is and need an MCP facade wrapper in `etzhayyim-project-ma`:",
        ])
        for actor_id, status, _, _ in rows:
            if status == "adapter-required":
                lines.append(f"- `{actor_id}`")

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
