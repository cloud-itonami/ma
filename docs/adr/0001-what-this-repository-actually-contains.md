# ADR 0001 — What this repository actually contains

- **Status**: accepted
- **Date**: 2026-08-21
- **Scope**: `cloud-itonami/ma` only
- **Supersedes**: nothing
- **Related**: superproject ADR-2608052000 (maturity axes), ADR-2608080000
  (one repo, one axis per iteration), ADR-2608136800 (read `origin/main` before
  concluding), ADR-2608136000 (the five questions to ask before trusting a green
  check)

## Context

This repository was created by a single commit that lifted
`60-apps/etzhayyim-project-ma` out of `etzhayyim/root`. It has fifteen tracked
files and one commit. It had no `docs/`, no ADRs, and no way to tell a reader
which of its claims were implemented.

Reading it end to end, the problem is not that it is small. It is that **four of
its documents describe systems that are not present, in the present tense**, and
nothing in the repository distinguishes those from the parts that work.

Measured on `7295214`:

| document | describes | present in tree |
|---|---|---|
| `README.md` | ten M&A brokerage actors; `wadm/`, `k8s/`, `wit/world.wit`, `kotodama.toml`, a `pulumi up` deploy | the ten names only, as UI data. Zero of the five artefacts. |
| `PROJECT.jsonld` | a "Multi-Actor Fund Management Platform", four processes P1–P4, nine fund-management actors | nothing |
| `appview/fork-manifest.yaml` | nine forks from three sibling projects into `projects/etzhayyim-project-ma/wasm/forks/…` | nothing |
| `kotodama.edn` | a k8s langserver at `did:web:ma.etzhayyim.com`, ERC-8004 registration, MCP/A2A endpoints, three BPMN processes, four ingest pipelines | nothing |

Two further facts a reader cannot get from the files themselves:

1. **The two actor sets are disjoint.** `README.md` and `ui/app.js` name ten
   M&A brokerage actors. `PROJECT.jsonld`, `appview/fork-manifest.yaml`,
   `scripts/`, and `reports/` name nine fund-management actors. The intersection
   is empty. The repository is two design sketches sharing a directory.

2. **`reports/fundmanager-mcp-readiness.md` is not reproducible from the script
   beside it.** The report contains `HTTP /api/mcp` six times, but
   `scripts/evaluate_fundmanager_mcp.py` can only ever emit `HTTP /api/grpc`,
   `tools/list`, `AddTool`, `/api/messages`. The report cites `60-apps/…` paths;
   the script's actor table cites `projects/…`. Whichever came first, they are
   not a pair.

The temptation with a repository in this state is to read the absences as
migration loss. They are not. `migration.edn` records 13 source files plus two
allowed additions, and the extraction commit has exactly 15. The extraction was
exact; the runtime was never at this path. It lived in the `wasm/` trees of the
sibling projects `FORKED_FROM.md` names.

## Decision

**Make the documentation a checkable artefact rather than a description.**

1. Add `scripts/verify-repo-claims.cljs` — seven assertions over what is actually
   here, plus an observation section that measures the known drift. Assertions
   move the exit code; observations never do, and print under a different prefix.

2. Add `docs/operator-quickstart.md` containing only steps that were executed,
   with verbatim output, and an explicit section naming what this repository
   **cannot** do.

3. Rewrite `README.md` in two parts: what is here, and what is declared but not
   implemented. Every item in the second part is labelled as intent.

4. **Do not implement the missing runtime, and do not delete the declarations.**
   Building the actors is a project, not a documentation fix; deleting the
   declarations would destroy the only record of the intended design. Both would
   re-introduce the ambiguity this ADR removes.

### Why the verifier is shaped the way it is

Written against the five questions in superproject ADR-2608136000:

- **What does it return with no input?** Not a pass. `slurp!` throws on a missing
  file, `check!` routes that to `UNKNOWN`, and any unknown forces exit 2.
- **What does it return when it cannot run at all?** Also exit 2, distinct from
  both pass (0) and fail (1). Verified by putting a failing `git` first on
  `PATH`: two checks go `UNKNOWN`, `CHECKS 6/7` prints, exit is 2.
- **Does it discard the error body?** No. The subprocess `stderr` is carried into
  the report and collapsed onto one line, so it survives a `grep '^UNKNOWN'` of a
  log rather than sitting on a continuation line nobody greps.
- **Are "skipped" and "passed" distinguishable in the output?** Yes — `CHECKS n/7`
  is an evidence floor. A run that reaches fewer checks than the file defines
  refuses to report a pass even if every line printed was `PASS`. This floor
  caught a real error while it was being written: `expected-checks` was 8 and only
  7 checks existed, and the first green-looking run exited 2 instead of 0.
- **Has it shown both directions?** Yes, five mutations, each restored, each
  landing on the check it was aimed at and nothing else:

  | mutation | result |
  |---|---|
  | rename one actor id in `ui/app.js` | `FAIL actors-readme-eq-ui`, exit 1 |
  | delete one `stageOwner` row in `ui/app.js` | `FAIL ui-renders` (`stage-owner` 3, expected 4), exit 1 |
  | change `migration.edn` `:tracked-files` 13 → 12 | `FAIL migration-identity`, exit 1 |
  | move `README.edn` away | `UNKNOWN`, `CHECKS 6/7`, exit 2 |
  | a `git` on `PATH` that exits 128 | two `UNKNOWN` with the stderr text preserved, exit 2 |
  | *(unmutated)* | `CHECKS 7/7`, `OK`, exit 0 |

  The `git`-unavailable mutation was not decoration: it found a real defect in
  the verifier. The observation section threw outside any handler, which exited 1
  — reading as "a check failed" — and skipped the summary entirely, so the run
  reported neither a pass, a failure, nor the evidence floor. Observations now go
  through a wrapper that routes a throw to `UNKNOWN`.

- **`migration-identity` counts the extraction commit, not `HEAD`.** An earlier
  draft counted the working tree, which would have turned red on this very commit
  and stayed red forever — growth reading as breakage. The claim in
  `migration.edn` is about what the migration carried across, so it is checked
  where it was made.

### Two hazards found while walking the quickstart

Both are recorded rather than fixed, because fixing them means deciding what these
scripts are *for*, which is a separate decision from documenting the repository.

- **`scripts/fork_fm_actors.sh` must not be run.** `ROOT_DIR` is
  `$(cd "$(dirname "$0")/../../.." && pwd)`, which assumed a monorepo depth this
  repository no longer has. From the west checkout it resolves to
  `…/com-junkawasaki/orgs`; from a standalone clone in `/tmp`, to `/`. It then
  runs `rm -rf` and `mkdir -p` under that root. It was **not** executed to
  establish this — the paths were resolved with `cd`/`pwd` alone.

- **`scripts/evaluate_fundmanager_mcp.py` fails silently.** Its `detect()` returns
  `('adapter-required', 'no MCP marker found')` both for a directory that does not
  exist and for one that exists with no MCP markers. Pointed at the paths it
  currently names — none of which resolve — it would still write a confident
  nine-row readiness table. This is the same shape as the defect the verifier's
  evidence floor exists to prevent: *a check that measured nothing returning the
  same value as a check that measured everything and found no problem.*

## Consequences

- A reader can now tell, in one command, whether this repository still matches its
  own claims, and can tell "unknown" from "clean".
- `README.md` and `ui/app.js` are pinned to each other. Renaming an actor in one
  and not the other now fails.
- The fund-management material (`PROJECT.jsonld`, `appview/fork-manifest.yaml`,
  `scripts/`, `reports/`) remains unverified design material. It is labelled as
  such and left in place.
- The two script hazards are documented but still present. Anyone running
  `scripts/fork_fm_actors.sh` from a checkout will still write outside the
  repository.

## Open work

Not started, and deliberately not partially faked:

- the M&A actors themselves — `/health`, `/api/mcp/tools`, `/api/mcp` are declared
  by `README.md` for all ten and implemented for none
- the k8s langserver, ERC-8004 registration, MCP/A2A endpoints, three BPMN
  processes, and four ingest pipelines declared in `kotodama.edn`
- the `wadm/`, `k8s/`, `wit/`, `kotodama.toml`, `infra/pulumi` deploy layer
- deciding whether this repository is the M&A brokerage project or the fund
  management project — currently it claims to be both
- repairing or retiring the two scripts, and regenerating or dating
  `reports/fundmanager-mcp-readiness.md`
- tests. There are none, and the repository ships no Clojure, so the superproject
  maturity scan's `test/` + `.cljc|.cljs|.clj|.kotoba` convention measures zero
  here. `scripts/verify-repo-claims.cljs` is a verifier, not a test suite, and
  sits outside `test/` on purpose — it is not counted, and was not written to be.
