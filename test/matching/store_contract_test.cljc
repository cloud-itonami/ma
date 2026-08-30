(ns matching.store-contract-test
  "The `Store` protocol as a contract. Only one implementation ships today
  (`MemStore`), so `implementations` has one entry -- but the tests are
  written against the protocol and driven from that list, so adding a
  datom-backed store means adding one entry here and nothing else."
  (:require [clojure.test :refer [deftest is testing]]
            [matching.registry :as r]
            [matching.store :as store]))

(def implementations
  [{:label "MemStore" :seed store/seed-db :empty store/empty-db}])

(deftest empty-store-answers-absent-not-empty
  (doseq [{:keys [label empty]} implementations]
    (testing label
      (let [db (empty)]
        (is (nil? (store/mandate db "nope")))
        (is (nil? (store/verification-of db "nope")))
        (is (nil? (store/screening-of db "nope")))
        (is (nil? (store/nda-of db "nope")))
        (is (nil? (store/shortlist-of db "nope")))
        (is (= [] (vec (store/all-mandates db))))
        (is (= [] (vec (store/ledger db))))
        (is (= [] (vec (store/introduction-history db))))
        (is (= 0 (store/next-sequence db "JPN")))
        (is (false? (store/pairing-already-introduced? db "a~b")))))))

(deftest seeded-store-round-trips
  (doseq [{:keys [label seed]} implementations]
    (testing label
      (let [db (seed)]
        (is (= "sell-1" (:id (store/mandate db "sell-1"))))
        (is (= :buy (:side (store/mandate db "buy-1"))))
        (is (= :verified (:verdict (store/verification-of db "buy-1"))))
        (is (nil? (store/verification-of db "buy-2"))
            "buy-2 is deliberately unverified; the demo depends on it")
        (is (= :executed (:status (store/nda-of db "buy-1~sell-1"))))
        (testing "sides are separable and sorted"
          (is (every? #(= :sell (:side %)) (store/mandates-on db :sell)))
          (is (every? #(= :buy (:side %)) (store/mandates-on db :buy)))
          (is (= (sort (map :id (store/mandates-on db :sell)))
                 (map :id (store/mandates-on db :sell)))))))))

(deftest ledger-is-append-only-and-ordered
  (doseq [{:keys [label empty]} implementations]
    (testing label
      (let [db (empty)]
        (doseq [i (range 5)] (store/append-ledger! db {:t :committed :n i}))
        (is (= [0 1 2 3 4] (mapv :n (store/ledger db)))
            "order is the evidence; a set would answer 'what' but not 'when'")))))

(deftest every-effect-writes-where-it-says
  (doseq [{:keys [label seed]} implementations]
    (testing label
      (let [db (seed)
            pid (r/pairing-id "buy-1" "sell-1")]
        (store/commit-record! db {:effect :mandate/upsert
                                  :value {:id "sell-1" :employees 999}})
        (is (= 999 (:employees (store/mandate db "sell-1"))))
        (is (= "旭精密工業株式会社" (:company-name (store/mandate db "sell-1")))
            "upsert merges; it does not replace the record")

        (store/commit-record! db {:effect :verification/set :path ["buy-2"]
                                  :payload {:mandate-id "buy-2" :verdict :verified}})
        (is (= :verified (:verdict (store/verification-of db "buy-2"))))

        (store/commit-record! db {:effect :screening/set :path [pid]
                                  :payload {:pairing-id pid :verdict :eligible}})
        (is (= :eligible (:verdict (store/screening-of db pid))))

        (store/commit-record! db {:effect :shortlist/set :path ["buy-1"]
                                  :payload {:mandate-id "buy-1" :entries []}})
        (is (= [] (:entries (store/shortlist-of db "buy-1"))))))))

(deftest introduction-recompute-and-sequence
  (doseq [{:keys [label seed]} implementations]
    (testing label
      (let [db (seed)
            pid (r/pairing-id "buy-1" "sell-1")]
        (is (= 0 (store/next-sequence db "JPN")))
        (store/commit-record! db {:effect :introduction/record :path [pid]
                                  :payload {:pairing-id pid}})
        (is (= 1 (store/next-sequence db "JPN")))
        (is (true? (store/pairing-already-introduced? db pid)))
        (let [rec (first (store/introduction-history db))]
          (is (= "JPN-INT-000000" (get rec "record_id")))
          (testing "the persisted score is this vehicle's own recompute"
            (is (= (r/compute-fit-score (store/mandate db "buy-1")
                                        (store/mandate db "sell-1"))
                   (get rec "fit_score")))))
        (testing "a second introduction of a different pairing advances the sequence"
          (let [pid2 (r/pairing-id "buy-1" "sell-5")]
            (store/commit-record! db {:effect :introduction/record :path [pid2]
                                      :payload {:pairing-id pid2}})
            (is (= 2 (store/next-sequence db "JPN")))
            (is (= "JPN-INT-000001" (get (second (store/introduction-history db)) "record_id")))))))))

(deftest unknown-effect-writes-nothing
  (doseq [{:keys [label seed]} implementations]
    (testing label
      (let [db (seed)
            before (store/all-mandates db)]
        (store/commit-record! db {:effect :something/new :path ["x"] :payload {:a 1}})
        (is (= before (store/all-mandates db)))
        (is (= [] (vec (store/ledger db))))))))
