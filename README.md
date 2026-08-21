# ma

**A design sketch for a global M&A brokerage service, expressed as actors mapped
onto APQC (process), ISCO (occupation), and ISIC (industry) — plus a static
dashboard that draws that mapping.**

There is no runtime in this repository. No actor is implemented, deployed, or
callable. What is here is a UI that renders the intended actor map, the
declaration files that describe the intended system, and a verifier that keeps
the two honest about which is which.

If you are trying to run something, start with
[docs/operator-quickstart.md](docs/operator-quickstart.md). If you are trying to
understand why this repository claims more than it contains, read
[docs/adr/0001](docs/adr/0001-what-this-repository-actually-contains.md).

```bash
nbb scripts/verify-repo-claims.cljs   # 7 checks; 0 = pass, 1 = fail, 2 = could not answer
cd ui && python3 -m http.server 8731  # then open http://127.0.0.1:8731/index.html
```

---

## What is actually here

Fifteen files, one commit, extracted whole from `etzhayyim/root`.

### `ui/` — a working static dashboard

Three files, no build step, no dependencies, no network calls. `ui/app.js` holds
three hardcoded tables and writes them into `ui/index.html`:

- a **9-stage deal pipeline**: Sales → Marketing → Screening → Matching →
  Diligence → Valuation → Negotiation → Closing → PMI
- a **10-row actor map** (below)
- a **4-row stage-ownership table** for Sales, Marketing, Matching, PMI

### The ten M&A actors it names

These are names in a design, not endpoints. Nothing here serves them.

| layer | actor | role |
|---|---|---|
| MA Core | `org-ma-global-m-a-brokerage-orchestrator-v1` | Sales / Closing / PMI orchestration |
| APQC | `svc-apqc-3-2-2-ma-sales-origination-v1` | Sales funnel workflow |
| APQC | `svc-apqc-3-1-1-ma-marketing-campaign-v1` | Marketing campaign workflow |
| APQC | `svc-apqc-2-6-4-ma-target-screening-v1` | Screening + diligence coordination |
| APQC | `svc-apqc-5-2-3-ma-buyer-matching-v1` | Buyer matching process |
| ISCO | `psn-isco-1221-ma-marketing-manager-v1` | Marketing strategy owner |
| ISCO | `psn-isco-3324-ma-trade-broker-v1` | Matching + negotiation intermediary |
| ISCO | `psn-isco-2412-ma-investment-adviser-v1` | Valuation + structuring adviser |
| ISIC | `org-isic-k-66-662-6619-ma-advisory-v1` | M&A execution support |
| ISIC | `org-isic-m-70-702-7020-ma-integration-v1` | PMI and transformation support |

This table and `ui/app.js` are pinned to each other — `verify-repo-claims.cljs`
fails if they diverge.

### `scripts/verify-repo-claims.cljs`

Checks that the repository still matches its own claims: the EDN and JSON-LD
files parse, the extraction arithmetic in `migration.edn` holds at the extraction
commit, this README and `ui/app.js` name the same actors, and the shipped
`ui/app.js` still populates all three tables when executed. Exits `2` — not `0` —
when it cannot read an input.

### Declaration and provenance files

`README.edn`, `kotodama.edn`, `PROJECT.jsonld`, `migration.edn`,
`FORKED_FROM.md`, `NOTICE`, `appview/fork-manifest.yaml`.

## What is declared but not implemented

Everything in this section is **intent**. None of it exists in this repository.
It is kept because it records the intended design, and labelled because an
earlier version of this README described it in the present tense.

- **The ten actors above.** `/health`, `/api/mcp/tools` and `/api/mcp` were
  declared for all ten; implemented for none. There are no `.wasm`, `.go` or
  `.toml` files here.
- **The deployment layer.** An earlier README said `wadm/ma-mcp.wadm.yaml`,
  `k8s/http-routes.yaml`, `wit/world.wit` and a `kotodama.toml` `[component]`
  block had "already been added", and gave a `cd infra/pulumi && pulumi up`
  command. None of those paths exist. That command cannot run from here.
- **`kotodama.edn`'s platform.** A k8s langserver at `did:web:ma.etzhayyim.com`,
  ERC-8004 agent registration, MCP and A2A endpoints, three BPMN processes, and
  four ingest pipelines (fund manager, business person, LEI, mail). All declared,
  none wired.
- **A second, different actor set.** `PROJECT.jsonld`,
  `appview/fork-manifest.yaml`, `scripts/fork_fm_actors.sh`,
  `scripts/evaluate_fundmanager_mcp.py` and `reports/` describe a *fund
  management* platform with nine actors (treasury manager, mutual funds, pension
  funding, …). It shares **zero** actors with the M&A set above. This repository
  currently claims to be both projects.

None of this is migration loss: `migration.edn` accounts for every file, and the
verifier checks the arithmetic. The runtime was never at this path — it lived in
the sibling projects `FORKED_FROM.md` names.

## Do not run the two legacy scripts

Both resolve paths outside this repository, because they assumed a monorepo depth
it no longer has.

- **`scripts/fork_fm_actors.sh`** — `ROOT_DIR` resolves to
  `…/com-junkawasaki/orgs` from the west checkout, or `/` from a standalone
  clone, and it runs `rm -rf` and `mkdir -p` there.
- **`scripts/evaluate_fundmanager_mcp.py`** — same path problem, and its
  `detect()` returns an identical verdict for "no MCP marker" and "I read zero
  files", so it would write a confident readiness report from no input at all.

`reports/fundmanager-mcp-readiness.md` was not produced by the script beside it
(the evidence strings and path prefixes disagree); treat it as an undated note
about a different checkout. Details in
[docs/adr/0001](docs/adr/0001-what-this-repository-actually-contains.md).

## Layout

```
ui/              static dashboard — index.html, app.js, styles.css
scripts/         verify-repo-claims.cljs (current) + two legacy scripts (do not run)
docs/            operator-quickstart.md, adr/
appview/         fork-manifest.yaml — intended forks, not performed here
reports/         fundmanager-mcp-readiness.md — not reproducible; see ADR 0001
*.edn, *.jsonld  declarations and provenance
```

## Licence

Apache 2.0 with the etzhayyim Charter Compliance Rider v3.1 — see `NOTICE`.
