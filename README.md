# ma

**One implemented actor — the Matching stage of an M&A deal pipeline — plus a
design sketch for the eight stages around it, expressed as actors mapped onto
APQC (process), ISCO (occupation) and ISIC (industry), and a static dashboard
that draws that mapping.**

Read the two halves of that sentence separately, because the difference is the
whole point of this repository.

**Implemented and callable:** `src/matching/` is a governed actor for the
Matching stage — the act of deciding which buyer is shown which seller, and
disclosing it. It has a store, an advisor, an independent governor with seven
un-overridable rules, a staged rollout gate, a langgraph-clj StateGraph, and 264
assertions across 6 namespaces that run on both nbb and the JVM.

**Declared but not implemented:** everything else. The ten actor names below are
UI data, not endpoints. The deployment layer, the k8s langserver, the BPMN
processes and the second (fund-management) actor set are all still declarations
with nothing behind them, exactly as
[docs/adr/0001](docs/adr/0001-what-this-repository-actually-contains.md) found
them.

If you are trying to run something, start with
[docs/operator-quickstart.md](docs/operator-quickstart.md).

```bash
# the actor: 53 tests, 264 assertions (0 = pass, 1 = fail, 2 = could not answer)
kbb --backend sci --classpath "src:test:../../kotoba-lang/langgraph/src:../../kotoba-lang/langchain/src" \
    test/run_tests.cljk

# the repository's own claims: 7 checks (0 = pass, 1 = fail, 2 = could not answer)
kbb --backend sci scripts/verify-repo-claims.cljk

cd ui && python3 -m http.server 8731  # then open http://127.0.0.1:8731/index.html
```

---

## What is actually here

The files carried across from `etzhayyim/root` by the extraction commit, plus
the Matching actor added since.

### `src/matching/` — the Matching stage, implemented

The one stage of the nine-stage pipeline that this repository owns. What an M&A
matching service actually sells is the pairing of a buyer with a seller; the
advice around it belongs to the sibling actors in `cloud-itonami`. So that is
what is implemented here, and nothing else is.

| namespace | what it holds |
|---|---|
| `matching.registry` | the pure core — fit score, blind teaser, introduction record |
| `matching.facts` | four jurisdictions' approach/confidentiality rules, each with a real citation |
| `matching.store` | `Store` protocol + `MemStore`; append-only ledger |
| `matching.matchllm` | the contained advisor (deterministic mock, or a real `ChatModel`) |
| `matching.governor` | the independent censor — eight checks, seven of them HARD |
| `matching.phase` | the 0→3 rollout gate |
| `matching.operation` | the langgraph-clj StateGraph that binds them |
| `matching.sim` | a demo driver that walks one clean deal and seven refusals |

**The invariant.** An introduction — telling a named buyer that a named seller is
for sale — never auto-commits, at any phase. Once a competitor knows a company is
for sale, no later approval un-knows it. Two independent layers enforce this:
`matching.governor`'s high-stakes set, and `matching.phase`'s auto table. Each
was removed in turn during testing and the other held the line on its own.

**The gate that matters most.** A seller's confidential fields may not reach a
buyer without an executed NDA for that exact pairing. The check walks nested
values, is decided from the data rather than from the op name, and fails closed
when it cannot tell whose secret it is. `matching.matchllm` can be asked to leak
on purpose (`:leak?`), so that gate has been shown refusing and permitting the
same field for different pairings.

Run the demo: `kbb --backend sci --classpath "src:../../kotoba-lang/langgraph/src:../../kotoba-lang/langchain/src" -e "(require '[matching.sim :as s]) (s/-main)"`,
or `kbb -M:dev:run` from inside the monorepo checkout.


### `ui/` — a working static dashboard

Three files, no build step, no dependencies, no network calls. `ui/app.js` holds
three hardcoded tables and writes them into `ui/index.html`:

- a **9-stage deal pipeline**: Sales → Marketing → Screening → Matching →
  Diligence → Valuation → Negotiation → Closing → PMI
- a **10-row actor map** (below)
- a **4-row stage-ownership table** for Sales, Marketing, Matching, PMI

### The ten M&A actors it names

These are names in a design, not endpoints. Nothing here serves them — including
the buyer-matching row. `src/matching/` implements the Matching *stage*; it does
not implement, expose or answer for any of the ten identifiers below.

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

### `scripts/verify-repo-claims.cljk`

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
src/matching/    the Matching-stage actor — implemented, tested, governed
test/matching/   the portable .cljc suite; test/run_tests.cljk is the nbb runner
deps.edn         nbb is the primary gate, kbb -M:dev:test the compat one
ui/              static dashboard — index.html, app.js, styles.css
scripts/         verify-repo-claims.cljs (current) + two legacy scripts (do not run)
docs/            operator-quickstart.md, adr/
appview/         fork-manifest.yaml — intended forks, not performed here
reports/         fundmanager-mcp-readiness.md — not reproducible; see ADR 0001
*.edn, *.jsonld  declarations and provenance
```

## Where this sits in the deal pipeline

The nine stages the dashboard draws are owned by nine different repositories.
Only this one is here; the composition is written down in the superproject's
`manifest/ma-business.edn`, because no single repository can see it.

| stage | owner |
|---|---|
| Sales | `cloud-itonami-isco-2412` |
| Marketing | `cloud-itonami-isco-1221` |
| Screening | `cloud-itonami-isic-6612` |
| **Matching** | **this repository, `src/matching/`** |
| Diligence | `cloud-itonami-isic-6612` |
| Valuation | `cloud-itonami-isco-2412` |
| Negotiation | `cloud-itonami-isco-3324` |
| Closing | `cloud-itonami-isic-6910` (registration side only; escrow has no owner) |
| PMI | `cloud-itonami-isic-7020` |

## Licence

Apache 2.0 with the etzhayyim Charter Compliance Rider v3.1 — see `NOTICE`.
