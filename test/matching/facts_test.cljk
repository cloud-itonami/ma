(ns matching.facts-test
  "The approach-rule catalog as executable tests. The invariant: a
  jurisdiction that is not in the catalog must never look covered."
  (:require [clojure.test :refer [deftest is testing]]
            [matching.facts :as facts]))

(deftest every-entry-is-fully-cited
  (testing "an entry with no provenance is worse than no entry -- it looks like a citation"
    (doseq [[iso3 e] facts/catalog]
      (doseq [k [:name :owner-authority :legal-basis :national-spec :provenance]]
        (is (and (string? (get e k)) (seq (get e k)))
            (str iso3 " is missing " k)))
      (is (seq (:required-evidence e)) (str iso3 " has an empty evidence checklist"))
      (is (every? string? (:required-evidence e))
          (str iso3 " has a non-string evidence item)")))))

(deftest unknown-jurisdiction-has-no-spec-basis
  (testing "the whole point: absent means absent, not empty-but-fine"
    (is (nil? (facts/spec-basis "ATL")))
    (is (nil? (facts/spec-basis nil)))
    (is (= [] (facts/evidence-checklist "ATL")))))

(deftest coverage-reports-misses
  (let [c (facts/coverage ["JPN" "ATL" "ZZZ"])]
    (is (= 3 (:requested c)))
    (is (= 1 (:covered c)))
    (is (= ["JPN"] (:covered-jurisdictions c)))
    (is (= ["ATL" "ZZZ"] (:missing-jurisdictions c)))))

(deftest required-evidence-satisfied
  (testing "unknown jurisdiction is never satisfied -- nil, not false-because-empty"
    (is (nil? (facts/required-evidence-satisfied? "ATL" ["anything"]))))
  (testing "partial evidence does not satisfy"
    (is (false? (facts/required-evidence-satisfied?
                 "JPN" (take 2 (facts/evidence-checklist "JPN"))))))
  (testing "the full checklist satisfies"
    (is (true? (facts/required-evidence-satisfied?
                "JPN" (facts/evidence-checklist "JPN")))))
  (testing "extra items do not break satisfaction"
    (is (true? (facts/required-evidence-satisfied?
                "USA" (conj (vec (facts/evidence-checklist "USA")) "something else"))))))
