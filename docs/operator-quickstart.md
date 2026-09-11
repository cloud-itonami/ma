# Operator quickstart

Everything below was executed and the output is pasted verbatim. If a step does
not reproduce for you, that is a bug in this document — please fix it rather than
working around it.

Steps 1–3 and 5–7 were run against commit `7295214` on 2026-08-21 with
`nbb v1.4.210`, `node v26.3.0`, `git 2.51.0`. **Step 4** was added on 2026-08-31
against the tree that introduced `src/matching/`, with `nbb v1.5.212`,
`node v26.7.0`, `git 2.51.0`. Serving the UI additionally used `python3 3.14.5`,
but any static file server works.

**Read [§6](#6-what-this-repository-cannot-do) before you run anything under
`scripts/`.** Two of the three scripts in this repository resolve paths *outside*
it, and one of them deletes directories.

---

## 1. Get the repository

Standalone:

```bash
git clone git@github.com:cloud-itonami/ma.git
cd ma
```

Or, inside the `com-junkawasaki` superproject, it is a west project at
`orgs/cloud-itonami/ma`:

```bash
west update --fetch smart ma
```

## 2. Check that the repository still matches its own claims

```bash
nbb scripts/verify-repo-claims.cljk
```

```
PASS      edn-parses:README.edn — 4 top-level keys
PASS      edn-parses:kotodama.edn — 6 top-level keys
PASS      edn-parses:migration.edn — 4 top-level keys
PASS      json-parses:PROJECT.jsonld — @id https://etzhayyim.com/projects/etzhayyim-project-ma
PASS      migration-identity — 13 source + 2 allowed additions = 15; extraction commit 7295214 has 15
PASS      actors-readme-eq-ui — 10 ids identical
PASS      ui-renders — rows {"actors" 10, "pipeline" 9, "stage-owner" 4}

OBSERVED  report-evidence-strings — report says 'HTTP /api/mcp' x6; script can only ever emit ["/api/messages" "AddTool" "HTTP /api/grpc" "tools/list"]
OBSERVED  report-path-prefixes — report cites 60-apps/ x9, script cites projects/ x10
OBSERVED  declared-absent:wadm — README.md's deploy section names it; present in tree? false
...
CHECKS	7/7
OK
```

Exit codes are three-valued and the difference matters:

| exit | meaning |
|---|---|
| `0` | all 7 checks ran and passed |
| `1` | a check ran and failed — the repository contradicts itself |
| `2` | a check **could not run**. The answer is unknown, not clean. |

`OBSERVED` lines are measurements of a known defect (see
[docs/adr/0001](adr/0001-what-this-repository-actually-contains.md)). They are
printed, never scored, and never affect the exit code — so "we looked at it"
cannot be misread as "it is fine".

`ui-renders` executes the real `ui/app.js` against a stub DOM rather than
re-encoding its data, so it fails if the page stops populating a table. Verified
in both directions: deleting one `stageOwner` row yields
`FAIL ui-renders — rows {"stage-owner" 3} — expected {"stage-owner" 4}` and exit 1.

## 3. Run the UI

There is no build step, no package manager, and no dependencies — `ui/` is three
static files.

```bash
cd ui && python3 -m http.server 8731 --bind 127.0.0.1
```

```
$ for f in index.html app.js styles.css; do
    printf "%-12s %s  %s bytes\n" "$f" \
      "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8731/$f)" \
      "$(curl -s http://127.0.0.1:8731/$f | wc -c | tr -d ' ')"
  done
index.html   200  997 bytes
app.js       200  2047 bytes
styles.css   200  450 bytes
```

Open <http://127.0.0.1:8731/index.html>. The page renders three tables from data
hardcoded in `ui/app.js`: a 9-stage deal pipeline, a 10-row actor mapping, and a
4-row stage-ownership table. It reads no network and calls no backend.

## 4. Run the Matching actor

`src/matching/` is the one implemented part of this repository. It needs
`langgraph-clj` and `langchain-clj`, which live in the `com-junkawasaki`
superproject — so the classpath below resolves only from inside the west
checkout at `orgs/cloud-itonami/ma`. A standalone clone must point the two
`../../kotoba-lang/...` paths at its own copies.

```bash
nbb --classpath "src:test:../../kotoba-lang/langgraph/src:../../kotoba-lang/langchain/src" \
    test/run_tests.cljk
```

```
Testing matching.facts-test

Testing matching.registry-test

Testing matching.phase-test

Testing matching.governor-contract-test

Testing matching.store-contract-test

Testing matching.operation-test

Ran 53 tests containing 264 assertions.
0 failures, 0 errors.

ASSERTIONS	264	NAMESPACES	6
OK
```

The exit code is three-valued for the same reason step 2's is, and for one more:

| exit | meaning |
|---|---|
| `0` | every assertion ran and passed |
| `1` | an assertion failed |
| `2` | fewer assertions ran than the suite is known to contain — the run measured too little to be trusted, which is neither a pass nor a failure |

That third case is not decoration. Under a ClojureScript host `run-tests` sets no
exit code at all, so without `test/run_tests.cljk`'s `:end-run-tests` method a
failing suite prints `FAIL` and exits `0`; and a run whose `:require` list broke
loads nothing, asserts nothing, and exits `0` in exactly the same way a clean run
does. Both were measured before this was committed:

| mutation | result |
|---|---|
| add `:introduction/make` to phase 3's `:auto` set | 2 failures, exit `1` |
| raise `min-assertions` above the real count | `REFUSING to report a result`, exit `2` |
| delete the confidentiality check from the governor | 10 failures, exit `1` — and every one of them in a confidentiality test |
| empty the governor's `high-stakes` set | 3 failures, exit `1`; the end-to-end introduction test stayed green, because `matching.phase` held the line on its own |
| *(unmutated)* | `264` assertions, `OK`, exit `0` |

The last two rows are the point of having two layers. Removing either one is
caught, and neither one alone is what stops an introduction from committing
unattended.

The same suite is portable `.cljc` and runs unchanged on the JVM
(`clojure -M:dev:test` from inside the monorepo checkout) — `Ran 53 tests
containing 264 assertions. 0 failures, 0 errors.` nbb is the primary gate and the
JVM the compat one, following the runtime-priority rule in the superproject's
`CLAUDE.md`.

To watch one deal walk the pipeline, plus the seven refusals:

```bash
nbb --classpath "src:../../kotoba-lang/langgraph/src:../../kotoba-lang/langchain/src" \
    -e "(require '[matching.sim :as s]) (s/-main)"
```

It prints a clean pairing committing through mandate intake, counterparty
verification, screening, shortlist and a human-approved introduction, and then
seven HARD holds — one per rule, each isolated so that the hold it produces names
exactly the rule the line above it claims to be demonstrating.

## 5. What you are looking at in the UI

The UI is a **static picture of an intended design**. The ten actor IDs it lists
(`svc-apqc-3-2-2-ma-sales-origination-v1` and so on) are names, not endpoints —
nothing in this repository implements, deploys, or contacts them. That includes
the buyer-matching row: `src/matching/` implements the Matching *stage*, and does
not answer for any of the ten identifiers.

## 6. What this repository cannot do

This section exists because four of the checked-in documents describe systems that
are not present. Details and evidence are in
[docs/adr/0001](adr/0001-what-this-repository-actually-contains.md).

### The ten named actors are still not implemented

`src/matching/` is a runtime, and it is the only one. It is not any of the ten
identifiers in the actor table: it implements the Matching stage of the pipeline,
serves no HTTP, and exposes no MCP tool.

```
$ git ls-files '*.wasm' '*.go' '*.toml' | wc -l
       0
```

No compiled actors, no Go sources, no component manifests. (Step 2 prints the full
extension census as an `OBSERVED` line, which — being generated — cannot go stale
the way a pasted count can.) `README.md`'s deploy section used to say
four artefacts had already been added (`wadm/ma-mcp.wadm.yaml`,
`k8s/http-routes.yaml`, `wit/world.wit`, `kotodama.toml`); none of them, and no
`infra/pulumi`, exist in the tree. The `pulumi up` invocation that section gave
cannot run from this repository.

This is **not** migration loss. `migration.edn` records the source as 13 tracked
files plus two allowed additions, and the extraction commit has exactly 15 — the
`migration-identity` check in step 2 verifies that arithmetic. The runtime was
never at this path; it lived in the `wasm/` trees of the *other* etzhayyim
projects that `FORKED_FROM.md` names.

### Do not run `scripts/fork_fm_actors.sh`

It computes `ROOT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"`, which assumed a
monorepo depth this repository no longer has. Measured, without executing it:

| run from | `ROOT_DIR` resolves to |
|---|---|
| `orgs/cloud-itonami/ma` in the superproject | `…/com-junkawasaki/orgs` |
| a standalone clone in `/tmp` | `/` |

It then runs `rm -rf` and `mkdir -p` under that root. Neither target is inside the
repository.

### `scripts/evaluate_fundmanager_mcp.py` cannot run here, and fails silently

`ROOT = Path(__file__).resolve().parents[3]` resolves outside the repository the
same way, so its output path does not exist. Worse, its `detect()` cannot tell an
absent directory from a clean one:

```
$ python3 -c 'import importlib.util; from pathlib import Path
spec = importlib.util.spec_from_file_location("ev","scripts/evaluate_fundmanager_mcp.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
print(m.detect(Path("/definitely/not/here")))
print(m.detect(Path("ui")))'
('adapter-required', 'no MCP marker found')
('adapter-required', 'no MCP marker found')
```

Both return the same verdict. Pointed at paths that do not exist, the script would
still write a confident nine-row readiness table — a report that measured nothing,
formatted exactly like a report that measured everything.

### `reports/fundmanager-mcp-readiness.md` is not reproducible

It cannot have been produced by the script shipped beside it. Two independent
signatures:

- the report says `HTTP /api/mcp` six times; the script's only reachable
  evidence strings are `/api/messages`, `AddTool`, `HTTP /api/grpc`, `tools/list`
- the report cites `60-apps/…` paths nine times; the script's actor table cites
  `projects/…`

Treat its verdict (`CONDITIONAL`, 2 actors needing adapters) as an undated
historical note about a different checkout, not as a current measurement.

### The repository describes two different systems

`README.md` and `ui/app.js` agree on ten **M&A brokerage** actors.
`PROJECT.jsonld`, `appview/fork-manifest.yaml`, `scripts/`, and `reports/` describe
nine **fund management** actors. The two sets share zero members. Step 2 pins the
first pair; the second group is unverified design material.

## 7. Where to make changes

| you want to change | edit |
|---|---|
| the pipeline, actor list, or stage table shown in the UI | `ui/app.js` (then rerun step 2 — `ui-renders` and `actors-readme-eq-ui` both cover it) |
| the actor names in prose | `README.md` **and** `ui/app.js` together; step 2 fails if they diverge |
| what step 2 checks | `scripts/verify-repo-claims.cljk`; keep `expected-checks` equal to the number of `check!` calls, or the run exits 2 |
| the Matching actor's rules | `src/matching/governor.cljk` — and add the paired case to `test/matching/governor_contract_test.cljk`, asserting the rule name rather than only that something was held |
| which jurisdictions can be screened | `src/matching/facts.cljk`; cite a real source, never invent one |
| what a buyer may see pre-NDA | `src/matching/registry.cljk`'s `confidential-fields` / `blind-teaser` — one definition, read by both the advisor and the governor |
| the rollout gate | `src/matching/phase.cljk`; `:introduction/make` must stay out of every `:auto` set |

MCP endpoints and deployment are open work, not changes to an existing
implementation. See docs/adr/0001 and docs/adr/0002.
