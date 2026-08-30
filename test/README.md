# Running the tests

```bash
# from orgs/cloud-itonami/ma inside the com-junkawasaki superproject
nbb --classpath "src:test:../../kotoba-lang/langgraph/src:../../kotoba-lang/langchain/src" \
    test/run_tests.cljs
```

`0` all passed · `1` something failed · `2` the run could not be trusted.

The third code is the one worth knowing about. Under a ClojureScript host
`run-tests` sets no exit code, so without the `:end-run-tests` method in
`run_tests.cljs` a failing suite exits `0`; and a run whose `:require` list broke
loads nothing, asserts nothing, and exits `0` in exactly the same way a clean run
does. `min-assertions` is the floor against the second case: raise it when the
suite grows, never lower it to make a run green.

The suite is portable `.cljc` and runs unchanged on the JVM as the compat gate:

```bash
clojure -M:dev:test
```

Both report the same counts. nbb is primary, per the runtime-priority rule in the
superproject's `CLAUDE.md`.

## The classpath is not optional

`langgraph-clj` and `langchain-clj` are `:local/root` dependencies on sibling
checkouts in the superproject, so the relative paths above resolve only from
`orgs/cloud-itonami/ma`. A standalone clone must point them at its own copies, or
override the coordinates in `deps.edn` with git ones.

## Writing a new governor case

Assert the rule, not the hold:

```clojure
(is (= [:seller-consent-missing] (mapv :rule (:violations v))))   ; yes
(is (true? (:hard? v)))                                           ; not on its own
```

Eight rules can fire on one request — an introduction against an unscreened
pairing trips four of them at once — so a test that only checks `:hard?` counts a
hold for the wrong reason as a success. That is not hypothetical here: it is how
two of the demo's cases were passing before the assertions were tightened. See
`docs/adr/0002`.

Pair every negative case with the control that must stay clean. A gate that
refuses everything is not a gate.
