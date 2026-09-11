# ADR 0002 — A runtime for the Matching stage

- **Status**: accepted
- **Date**: 2026-08-31
- **Scope**: `cloud-itonami/ma` only
- **Supersedes**: nothing. Discharges the first item of ADR 0001's *Open work*
  for one stage.
- **Related**: `docs/adr/0001` (what this repository actually contains),
  superproject `manifest/ma-business.edn` and `scripts/ma-business-tick.cljs`
  (the composite this repository is one member of), superproject
  ADR-2608291400, ADR-2608136000 (the questions to ask before trusting a green
  check), ADR-2607189300 (one floor per iteration)

## Context

ADR 0001 established that this repository was two design sketches sharing a
directory, and decided — correctly for a documentation fix — **not** to implement
the missing runtime: *"Building the actors is a project, not a documentation
fix."* That project is what this ADR is.

What made it a project rather than a fix is that the choice of *which* runtime is
not obvious from inside this repository. The README names ten actors; the
JSON-LD names nine different ones. Neither list says which of them this
repository is for.

The answer came from outside. The superproject's `manifest/ma-business.edn`
records the nine-stage pipeline this repository's own `ui/app.js` draws, and
assigns each stage to an owner repository. Eight of the nine are owned by
existing, implemented actors in `cloud-itonami`:

| stage | owner |
|---|---|
| Sales, Valuation | `cloud-itonami-isco-2412` |
| Marketing | `cloud-itonami-isco-1221` |
| Screening, Diligence | `cloud-itonami-isic-6612` |
| Negotiation | `cloud-itonami-isco-3324` |
| Closing | `cloud-itonami-isic-6910` |
| PMI | `cloud-itonami-isic-7020` |
| **Matching** | **this repository** |

The ninth had no implementation anywhere in the fleet. It is also the one the
business is actually *for*: an M&A matching service sells the pairing of a buyer
with a seller. The advice around it is a real service too, and eight repositories
already provide it.

`scripts/ma-business-tick.cljs` in the superproject had been naming this the next
move, unchanged, on every run since 2026-08-29: `FLOOR matching-runtime broken
{:owner "ma", :why :no-src}`.

Note what this does **not** resolve. ADR 0001's open question "deciding whether
this repository is the M&A brokerage project or the fund management project"
stays open. This ADR settles what the repository *implements*; the
fund-management declarations are untouched and still unverified.

## Decision

**Implement the Matching stage as a governed actor in `src/matching/`, in the
same shape as the eight sibling actors that own the other stages.**

Seven namespaces, all portable `.cljc`:

| namespace | role |
|---|---|
| `matching.registry` | pure core: fit score, blind teaser, introduction record |
| `matching.facts` | four jurisdictions' approach/confidentiality rules, each cited |
| `matching.store` | `Store` protocol, `MemStore`, append-only ledger |
| `matching.matchllm` | the contained advisor — untrusted, proposal-only |
| `matching.governor` | the independent censor — eight checks, seven HARD |
| `matching.phase` | the 0→3 rollout gate |
| `matching.operation` | the langgraph-clj StateGraph binding them |

### What the actor is allowed to do, and what it is not

Five write ops (`:mandate/intake`, `:counterparty/verify`, `:pairing/screen`,
`:shortlist/rank`, `:introduction/make`) and one read op
(`:pairing/explain` — "why was I shown this" is a real question a buyer asks, and
answering it must not be a mutation).

**`:introduction/make` never auto-commits, at any phase.** Telling a named buyer
that a named seller is for sale is the one act here that cannot be taken back:
once a competitor knows a company is for sale, no later approval un-knows it. Two
independent layers enforce this — `matching.governor`'s `high-stakes` set and
`matching.phase`'s `:auto` table — and each was verified to hold the line alone
(see *Evidence* below).

### The confidentiality invariant

A seller's confidential fields may not reach a buyer without an executed NDA for
that exact pairing. Three design choices make this checkable rather than
aspirational:

1. **One definition of "confidential".** `registry/confidential-fields` is read
   by both the advisor's teaser builder and the governor's leak walk, so the two
   cannot drift apart on what the word means.
2. **The teaser is a whitelist, not a blacklist.** A sell-side field that nobody
   thought about does not reach a buyer by being forgotten. A blacklist fails
   open here, and failing open means naming a seller who did not consent.
3. **"Buyer-facing" is decided from the data, not from the op name.** The gate
   asks whether the proposal's value addresses a `:side :buy` mandate. A
   `:mandate/intake` patch legitimately carries a company name — it is the seller
   describing themselves — and is not caught, because it addresses no buyer. An
   op added later is covered without being added to a list.

The walk descends into nested maps and sequences, and returns `nil` for a leak it
cannot attribute; the governor treats an unattributable disclosure as a violation,
because it cannot be checked against any NDA.

### Deliberate differences from the sibling actors

Recorded because the differences are visible and would otherwise read as
oversights:

- **No integer safety kernel.** `cloud-itonami-isic-6612` delegates its decision
  to `brokerage.kernels.gate`, an integer-coded core compiled to Wasm. This
  governor decides in plain Clojure. Every check is written total and fail-closed
  so a kernel would be a restatement rather than a repair, but there is no kernel
  today and this ADR does not imply parity.
- **One store implementation.** The siblings ship `MemStore` *and* a
  `langchain.db`-backed `DatomicStore`. This ships `MemStore` only. The protocol
  is the seam and `store_contract_test` is driven from a list of implementations,
  so a second one is an entry in that list — but claiming a datom backend that
  does not exist is the exact failure ADR 0001 was written about.
- **No `render_html.clj`.** The siblings render an operator console. This
  repository already has a static dashboard in `ui/`; a second one would be the
  two-design problem again.
- **The fit score is integer.** The siblings' notional values are genuinely
  continuous and need a one-cent tolerance. A fit score is a weighted count of
  criteria met, so it is integral, so the governor's cross-check is an exact
  equality with no tolerance window for a fabricated number to hide in.

## Evidence

53 tests, 264 assertions, 6 namespaces. Primary gate is nbb
(`test/run_tests.cljk`), following the superproject's runtime-priority rule;
`clojure -M:dev:test` runs the same portable `.cljc` suite on the JVM and reports
the identical counts.

Written against the questions in superproject ADR-2608136000:

- **What does it return with no input?** Not a pass. `min-assertions` in
  `test/run_tests.cljk` is an evidence floor: a run that asserts less than the
  suite is known to contain prints `REFUSING to report a result` and exits **2**,
  which is neither the pass code nor the failure code. Verified by raising the
  floor above the real count.
- **What does it return when it cannot run at all?** Also 2, by the same floor —
  a broken `:require` list loads nothing, asserts nothing, and would otherwise
  exit 0 indistinguishably from a clean run.
- **Are "skipped" and "passed" distinguishable?** Yes: `ASSERTIONS<TAB>n<TAB>
  NAMESPACES<TAB>m` is printed on every run, passing or not.
- **Has it shown both directions?** Yes. Four mutations, each restored, each
  landing where it was aimed:

  | mutation | result |
  |---|---|
  | add `:introduction/make` to phase 3's `:auto` set | 2 failures, exit 1, both in `matching.phase-test` |
  | empty the governor's `high-stakes` set | 3 failures, exit 1, all in `clean-introduction-escalates-but-does-not-hold` |
  | delete `confidentiality-violations` from the governor's check list | 10 failures, exit 1 — 5 unit, 4 end-to-end, 1 in the audit-trail test |
  | raise `min-assertions` above the real count | `REFUSING to report a result`, exit 2 |
  | *(unmutated)* | 264 assertions, `OK`, exit 0 |

  The first two rows are the two-layer claim demonstrated in both directions.
  Breaking the phase table alone did **not** fail the end-to-end introduction
  test, because the governor still escalated; emptying the governor's high-stakes
  set alone did not fail it either, because the phase gate still escalated.
  Neither layer is individually load-bearing, which is what "two layers, not one"
  is supposed to mean and is not something the code can be read to establish.

- **Does each negative test refuse for the reason it names?** This is the one that
  changed the code. Every governor case asserts `(mapv :rule violations)`, not
  `:hard?`. Writing them that way exposed that two of the demo's HARD holds were
  firing on three or four rules at once — an introduction against an unscreened
  pairing trips `:no-spec-basis` and `:evidence-incomplete` regardless of the rule
  under test, so the "unverified counterparty" and "seller consent" cases were
  passing for reasons other than the ones they claimed. Both the demo and the
  tests now screen the pairing first, and the demo's seed data was changed
  (`sell-2` moved from an `:any-verified` consent policy to an `:explicit` one
  naming `buy-2`) specifically so the unverified-counterparty gate could be
  isolated: under `:any-verified`, an unverified buyer fails the consent check
  too, and a hold on two rules is evidence about neither.

- **Was the mutation aimed where it landed?** Checked each time. The
  confidentiality mutation failed ten assertions and every one of them was in a
  confidentiality test; nothing else moved.

`scripts/verify-repo-claims.cljk` still reports `CHECKS 7/7 OK` — the actor adds
files but does not touch the claims that check pins.

## Consequences

- The superproject's `matching-runtime` floor closes. `standard-form` and
  `os-declared` remain open for this repository and for three siblings; they are
  separate iterations.
- `README.md` and `docs/operator-quickstart.md` had to be rewritten, not
  appended to. Both said "there is no runtime in this repository" in the present
  tense, which was true when ADR 0001 was written and is now false. Leaving that
  in place would have recreated the exact defect ADR 0001 exists to prevent — a
  document describing a system that is not the one in the tree — with the polarity
  reversed.
- The `:local/root` dependency on `../../kotoba-lang/langgraph` means the tests
  and the demo resolve only from inside the superproject checkout. A standalone
  fork must override the coordinates. This is the same constraint every sibling
  actor carries and it is stated in `deps.edn`, the README and the quickstart.
- Nothing here is deployed, serves HTTP, or answers an MCP tool call. The ten
  actor identifiers in `README.md` remain UI data with no endpoints behind them,
  including the buyer-matching one.

## Open work

- **`standard-form` for the siblings.** `cloud-itonami-isco-2412`, `-isco-3324`
  and `-isco-1221` are implemented actors stuck in a two-file shape without
  `phase.cljc` / `operation.cljc`, so the 営み OS cannot drive them.
- **`os-declared`.** Neither this repository nor four of the six siblings are
  declared in `network-awai/cloud-itonami`'s `os.edn`.
- **A second store backend**, to make `store_contract_test`'s implementation list
  do the work it is shaped for.
- **A safety kernel** for the governor's decision, if this actor ever runs
  unattended at a phase above 3's current auto set.
- **Escrow and funds flow at Closing.** `manifest/ma-business.edn` records that
  `cloud-itonami-isic-6910` owns the registration side only; no repository owns
  the money.
- Everything ADR 0001 listed that this ADR does not name.
