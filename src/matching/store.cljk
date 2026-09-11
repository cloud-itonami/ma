(ns matching.store
  "SSoT for the Matching stage, behind a `Store` protocol so the backend
  is a swap and not a rewrite -- the same seam every `cloud-itonami-isic-*`
  actor in this fleet uses.

  ONE implementation ships today: `MemStore`, an atom of EDN. It is the
  deterministic default for dev, tests and the demo, and it has no
  dependencies. There is no second backend here yet, and this namespace
  does not claim one -- the protocol is the seam, and a Datomic /
  kotobase-backed record would implement the same protocol and pass the
  same contract test. Saying that plainly is the point: the sibling
  actors ship two implementations, this one ships one, and the difference
  should be readable from the file rather than from running it.

  The ledger is append-only on every backend. 'Which buyer was shown
  which target, on whose consent, against which NDA, approved by whom'
  is always a query over an immutable log -- that log is the evidence a
  seller needs if they later ask who was told they were for sale, and it
  is the only artefact that can answer that question after the fact.

  A note on vocabulary. A `mandate` here is both the instruction and the
  party that gave it; there is no separate party table. That is a real
  simplification (one holder may run several mandates) and it is made
  here, once, rather than implied by the code."
  (:require [kotoba.lang.text :as str]
            [matching.registry :as registry]))

(defprotocol Store
  (mandate [s id])
  (all-mandates [s])
  (mandates-on [s side] "every mandate on :buy or :sell, sorted by id")
  (verification-of [s mandate-id] "committed counterparty verification verdict, or nil")
  (screening-of [s pairing-id] "committed pairing screening verdict, or nil")
  (nda-of [s pairing-id] "executed NDA record for this pairing, or nil")
  (shortlist-of [s mandate-id] "committed shortlist for a buy-side mandate, or nil")
  (ledger [s])
  (introduction-history [s] "the append-only introduction history (matching.registry drafts)")
  (next-sequence [s jurisdiction] "next introduction sequence for a jurisdiction")
  (pairing-already-introduced? [s pairing-id] "has this pairing already been introduced?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-mandates [s mandates] "replace/seed the mandate book (map id->mandate)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained mandate book so the actor + tests run offline.

  Every record below exists to exercise exactly one path. The failure
  modes are seeded deliberately, because a demo set in which nothing can
  go wrong proves only that the happy path compiles:

    sell-1  clean JPN target; buy-1 has an NDA and consent  -> commits
    sell-2  clean USA target
    sell-3  jurisdiction ATL, absent from `matching.facts`   -> HARD no-spec-basis
    sell-4  declares a conflict of interest                  -> HARD conflict
    sell-5  has buy-1 on its no-go list                      -> HARD consent-missing
    buy-1   verified, well-specified buy-side mandate
    buy-2   named in sell-2's consent list but never verified -> HARD unverified
    buy-3   verified but a poor fit (score is low, not zero)"
  []
  {:mandates
   {"sell-1" {:id "sell-1" :side :sell :jurisdiction "JPN"
              :company-name "旭精密工業株式会社" :owner-name "旭 健一"
              :industry :precision-machinery :geography "JPN" :deal-type :majority
              :revenue 850 :employees 120
              :rationale "後継者不在による事業承継"
              :conflict-hit? false
              :consent-policy :explicit :consented #{"buy-1"} :no-go #{}}
    "sell-2" {:id "sell-2" :side :sell :jurisdiction "USA"
              :company-name "Northgate Logistics Inc." :owner-name "R. Northgate"
              :industry :saas :geography "USA" :deal-type :majority
              :revenue 1200 :employees 60
              :rationale "founder exit"
              :conflict-hit? false
              ;; :explicit, and BOTH buy-2 and buy-3 are named. That is what
              ;; lets the demo isolate the unverified-counterparty gate: with
              ;; an :any-verified seller an unverified buyer would fail the
              ;; consent check too, and a hold on two rules cannot show that
              ;; either one of them works.
              :consent-policy :explicit :consented #{"buy-2" "buy-3"} :no-go #{}}
    "sell-3" {:id "sell-3" :side :sell :jurisdiction "ATL"
              :company-name "Atlantis Marine Works" :owner-name "A. Poseidon"
              :industry :precision-machinery :geography "ATL" :deal-type :majority
              :revenue 900 :employees 90
              :rationale "demo: a jurisdiction with no spec-basis on file"
              :conflict-hit? false
              :consent-policy :any-verified :consented #{} :no-go #{}}
    "sell-4" {:id "sell-4" :side :sell :jurisdiction "JPN"
              :company-name "双葉テクノ株式会社" :owner-name "双葉 光"
              :industry :precision-machinery :geography "JPN" :deal-type :majority
              :revenue 800 :employees 75
              :rationale "demo: adviser sits on both sides"
              :conflict-hit? true
              :consent-policy :any-verified :consented #{} :no-go #{}}
    "sell-5" {:id "sell-5" :side :sell :jurisdiction "JPN"
              :company-name "峰岸工機株式会社" :owner-name "峰岸 隆"
              :industry :precision-machinery :geography "JPN" :deal-type :majority
              :revenue 950 :employees 110
              :rationale "demo: seller has excluded this buyer by name"
              :conflict-hit? false
              :consent-policy :explicit :consented #{} :no-go #{"buy-1"}}

    "buy-1" {:id "buy-1" :side :buy :jurisdiction "JPN"
             :company-name "山王ホールディングス株式会社"
             :target-industries #{:precision-machinery} :target-geographies #{"JPN"}
             :accepted-deal-types #{:majority} :size-min 500 :size-max 1500
             :conflict-hit? false}
    "buy-2" {:id "buy-2" :side :buy :jurisdiction "JPN"
             :company-name "未確認キャピタル合同会社"
             :target-industries #{:precision-machinery} :target-geographies #{"JPN"}
             :accepted-deal-types #{:majority} :size-min 500 :size-max 1500
             :conflict-hit? false}
    "buy-3" {:id "buy-3" :side :buy :jurisdiction "USA"
             :company-name "Harborline Capital LLC"
             :target-industries #{:saas} :target-geographies #{"JPN"}
             :accepted-deal-types #{:minority} :size-min 100 :size-max 400
             :conflict-hit? false}}

   ;; Verifications and NDAs are pre-seeded for the pairings the demo
   ;; drives. buy-2 is deliberately absent from :verifications.
   :verifications {"buy-1" {:mandate-id "buy-1" :verdict :verified
                            :basis ["buyer verification record"]}
                   "buy-3" {:mandate-id "buy-3" :verdict :verified
                            :basis ["Buyer verification record"]}
                   "sell-1" {:mandate-id "sell-1" :verdict :verified
                             :basis ["売却意向表明・仲介契約書 (sell-side mandate letter)"]}
                   "sell-2" {:mandate-id "sell-2" :verdict :verified
                             :basis ["Sell-side engagement letter"]}
                   "sell-3" {:mandate-id "sell-3" :verdict :verified :basis ["demo"]}
                   "sell-4" {:mandate-id "sell-4" :verdict :verified :basis ["demo"]}
                   "sell-5" {:mandate-id "sell-5" :verdict :verified :basis ["demo"]}}

   :ndas {"buy-1~sell-1" {:pairing-id "buy-1~sell-1" :status :executed :reference "NDA-JPN-0001"}
          "buy-1~sell-4" {:pairing-id "buy-1~sell-4" :status :executed :reference "NDA-JPN-0004"}
          "buy-1~sell-5" {:pairing-id "buy-1~sell-5" :status :executed :reference "NDA-JPN-0005"}
          "buy-3~sell-2" {:pairing-id "buy-3~sell-2" :status :executed :reference "NDA-USA-0002"}
          "buy-2~sell-2" {:pairing-id "buy-2~sell-2" :status :executed :reference "NDA-USA-0003"}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- make-introduction!
  "Backend-agnostic `:introduction/record` -- looks the pairing up via the
  protocol, INDEPENDENTLY recomputes the fit score via
  `registry/compute-fit-score` (never persists the score the advisor
  claimed, even though the governor has already verified the two agree),
  and returns {:result .. } for the caller to persist."
  [s pairing-id]
  (let [[buy-id sell-id] (str/split pairing-id #"~")
        b (mandate s buy-id)
        sell (mandate s sell-id)
        recomputed (registry/compute-fit-score b sell)
        seq-n (next-sequence s (:jurisdiction sell))]
    {:result (registry/register-introduction buy-id sell-id recomputed
                                             (:jurisdiction sell) seq-n)
     :jurisdiction (:jurisdiction sell)}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (mandate [_ id] (get-in @a [:mandates id]))
  (all-mandates [_] (sort-by :id (vals (:mandates @a))))
  (mandates-on [_ side] (sort-by :id (filter #(= side (:side %)) (vals (:mandates @a)))))
  (verification-of [_ id] (get-in @a [:verifications id]))
  (screening-of [_ pid] (get-in @a [:screenings pid]))
  (nda-of [_ pid] (get-in @a [:ndas pid]))
  (shortlist-of [_ id] (get-in @a [:shortlists id]))
  (ledger [_] (:ledger @a))
  (introduction-history [_] (:introductions @a))
  (next-sequence [_ jurisdiction] (get-in @a [:sequences jurisdiction] 0))
  (pairing-already-introduced? [_ pid]
    (boolean (some #(= pid (get % "pairing_id")) (:introductions @a))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :mandate/upsert
      (swap! a update-in [:mandates (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :screening/set
      (swap! a assoc-in [:screenings (first path)] payload)

      :shortlist/set
      (swap! a assoc-in [:shortlists (first path)] payload)

      :introduction/record
      (let [pairing-id (first path)
            {:keys [result jurisdiction]} (make-introduction! s pairing-id)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences jurisdiction] (fnil inc 0))
                       (update :introductions registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-mandates [s mandates] (when (seq mandates) (swap! a assoc :mandates mandates)) s))

(defn seed-db
  "A MemStore seeded with the demo mandate book. The deterministic
  default."
  []
  (->MemStore (atom (merge {:screenings {} :shortlists {} :ledger []
                            :sequences {} :introductions []}
                           (demo-data)))))

(defn empty-db
  "An empty MemStore -- the contract test's blank slate."
  []
  (->MemStore (atom {:mandates {} :verifications {} :ndas {} :screenings {}
                     :shortlists {} :ledger [] :sequences {} :introductions []})))
