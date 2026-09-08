(ns matching.operation-test
  "The OperationActor end to end: one graph run per operation, and the
  three dispositions it can reach.

  These are the tests that prove the layers actually compose -- a governor
  that refuses in isolation but whose refusal never reaches the graph
  would pass every test in `governor_contract_test` and still commit."
  (:require [kotoba.lang.text :as str]
            [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [matching.operation :as op]
            [matching.registry :as r]
            [matching.store :as store]))

(def operator {:actor-id "op-1" :actor-role :intermediary :phase 3})

(defn- exec! [actor tid request] (g/run* actor {:request request :context operator}
                                        {:thread-id tid}))
(defn- resume! [actor tid approval] (g/run* actor {:approval approval}
                                            {:thread-id tid :resume? true}))
(defn- disposition [r] (get-in r [:state :disposition]))
(defn- held-rules [r]
  (->> (get-in r [:state :audit])
       (filter #(= :governor-hold (:t %)))
       last :basis vec))

(deftest auto-eligible-op-commits-without-a-human
  (let [db (store/seed-db)
        actor (op/build db)
        res (exec! actor "t1" {:op :mandate/intake :subject "sell-9"
                               :patch {:id "sell-9" :side :sell :jurisdiction "JPN"
                                       :industry :precision-machinery}})]
    (is (= :commit (disposition res)))
    (is (= "sell-9" (:id (store/mandate db "sell-9"))))
    (is (= 1 (count (store/ledger db))))))

(deftest non-auto-op-pauses-for-approval-then-commits
  (let [db (store/seed-db)
        actor (op/build db)
        pid (r/pairing-id "buy-1" "sell-1")
        res (exec! actor "t2" {:op :pairing/screen :subject "buy-1"
                               :buy-side-id "buy-1" :sell-side-id "sell-1"})]
    (is (= :escalate (disposition res)))
    (is (nil? (store/screening-of db pid))
        "nothing may reach the SSoT while the actor is paused")
    (let [after (resume! actor "t2" {:status :approved :by "op-1"})]
      (is (= :commit (disposition after)))
      (is (= :eligible (:verdict (store/screening-of db pid)))))))

(deftest a-rejected-approval-holds-and-writes-nothing
  (let [db (store/seed-db)
        actor (op/build db)
        pid (r/pairing-id "buy-1" "sell-1")]
    (exec! actor "t3" {:op :pairing/screen :subject "buy-1"
                       :buy-side-id "buy-1" :sell-side-id "sell-1"})
    (let [after (resume! actor "t3" {:status :rejected :by "op-1"})]
      (is (= :hold (disposition after)))
      (is (nil? (store/screening-of db pid)))
      (is (= :approval-rejected (:t (last (store/ledger db))))))))

(deftest an-introduction-always-reaches-a-human
  (let [db (store/seed-db)
        actor (op/build db)
        pid (r/pairing-id "buy-1" "sell-1")]
    (exec! actor "s" {:op :pairing/screen :subject "buy-1"
                      :buy-side-id "buy-1" :sell-side-id "sell-1"})
    (resume! actor "s" {:status :approved :by "op-1"})
    (let [res (exec! actor "i" {:op :introduction/make :subject "buy-1"
                                :buy-side-id "buy-1" :sell-side-id "sell-1"})]
      (is (= :escalate (disposition res))
          "a clean governor must NOT be enough to disclose a target")
      (is (false? (store/pairing-already-introduced? db pid)))
      (let [after (resume! actor "i" {:status :approved :by "op-1"})]
        (is (= :commit (disposition after)))
        (is (true? (store/pairing-already-introduced? db pid)))))))

(deftest a-hard-hold-never-reaches-a-human
  (testing "the leaked shortlist is refused inside the graph, not at the approval node"
    (let [db (store/seed-db)
          actor (op/build db)
          res (exec! actor "t4" {:op :shortlist/rank :subject "buy-1" :leak? true})]
      (is (= :hold (disposition res)))
      (is (= [:confidentiality-breach] (held-rules res)))
      (is (nil? (store/shortlist-of db "buy-1")))
      (testing "a human cannot approve their way past it -- there is nothing paused to resume"
        (is (= :hold (disposition (resume! actor "t4" {:status :approved :by "op-1"}))))
        (is (nil? (store/shortlist-of db "buy-1")))))))

(deftest a-read-op-writes-no-record-but-is-still-logged
  (let [db (store/seed-db)
        actor (op/build db)
        before (count (store/all-mandates db))
        res (exec! actor "t5" {:op :pairing/explain :subject "buy-1"
                               :buy-side-id "buy-1" :sell-side-id "sell-1"})]
    (is (= :commit (disposition res)))
    (is (= before (count (store/all-mandates db))))
    (is (nil? (store/shortlist-of db "buy-1")))
    (testing "who was shown what is itself worth recording"
      (is (= :pairing/explain (:op (last (store/ledger db))))))))

(deftest phase-zero-writes-nothing-through-the-graph
  (let [db (store/seed-db)
        actor (op/build db)
        res (g/run* actor {:request {:op :mandate/intake :subject "sell-9"
                                     :patch {:id "sell-9"}}
                           :context (assoc operator :phase 0)}
                    {:thread-id "t6"})]
    (is (= :hold (disposition res)))
    (is (nil? (store/mandate db "sell-9")))))

(deftest the-audit-trail-does-not-repeat-the-leak
  (testing "a proposal held for leaking a name must not leak it again in the record of its rejection"
    (let [db (store/seed-db)
          actor (op/build db)]
      (exec! actor "t7" {:op :shortlist/rank :subject "buy-1" :leak? true})
      (let [trace (->> (store/ledger db) (filter #(= :governor-hold (:t %))) last pr-str)]
        (doseq [name- (keep :company-name (store/mandates-on db :sell))]
          (is (not (str/includes? trace name-))
              (str "the ledger entry repeated " name-)))))))
