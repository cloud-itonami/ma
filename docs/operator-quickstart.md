# Operator quickstart

Everything below was executed against commit `7295214` on 2026-08-21 and the output
is pasted verbatim. If a step does not reproduce for you, that is a bug in this
document — please fix it rather than working around it.

Tooling this was run with: `nbb v1.4.210`, `node v26.3.0`, `git 2.51.0`.
Serving the UI additionally used `python3 3.14.5`, but any static file server works.

**Read [§5](#5-what-this-repository-cannot-do) before you run anything under
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
nbb scripts/verify-repo-claims.cljs
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

## 4. What you are looking at

The UI is a **static picture of an intended design**. The ten actor IDs it lists
(`svc-apqc-3-2-2-ma-sales-origination-v1` and so on) are names, not endpoints —
nothing in this repository implements, deploys, or contacts them.

## 5. What this repository cannot do

This section exists because four of the checked-in documents describe systems that
are not present. Details and evidence are in
[docs/adr/0001](adr/0001-what-this-repository-actually-contains.md).

### There is no runtime here

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

## 6. Where to make changes

| you want to change | edit |
|---|---|
| the pipeline, actor list, or stage table shown in the UI | `ui/app.js` (then rerun step 2 — `ui-renders` and `actors-readme-eq-ui` both cover it) |
| the actor names in prose | `README.md` **and** `ui/app.js` together; step 2 fails if they diverge |
| what step 2 checks | `scripts/verify-repo-claims.cljs`; keep `expected-checks` equal to the number of `check!` calls, or the run exits 2 |

Anything involving actual actors, MCP endpoints, or deployment is open work, not a
change to an existing implementation. See docs/adr/0001.
