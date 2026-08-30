(ns matching.registry
  "Pure-function core of the Matching stage: the fit score, the blind
  teaser, and the introduction record draft.

  No I/O, no network, no LLM. Everything here is a total function of its
  arguments, which is what lets `matching.governor` cross-check the
  advisor's claimed numbers against an independent recompute -- the same
  'reimplement the math independently, never trust a claimed figure'
  discipline `brokerage.registry/compute-order-value` establishes in
  `cloud-itonami-isic-6612`.

  Two things here carry the weight of this whole stage:

  `compute-fit-score` is INTEGER arithmetic on purpose. A fit score is
  a weighted count of criteria met, and criteria are met or not met --
  there is no continuous quantity underneath. Keeping it integral means
  the governor's cross-check is an EXACT equality, with no tolerance
  window for a fabricated score to hide inside. (Contrast the sibling
  brokerage actor, whose notional value is genuinely continuous and so
  needs a one-cent tolerance.)

  `blind-teaser` is the anonymised profile a buyer may see BEFORE an NDA
  exists for that pairing -- the real artefact this industry calls a
  teaser or blind profile. It is defined here, as a pure function, so
  that `confidential-fields` has exactly one definition and the governor
  and the advisor cannot drift apart on what 'non-public' means. A field
  is confidential unless this function passes it through."
  (:require [clojure.string :as str]))

;; ----------------------------- fit score -----------------------------

(def criteria-weights
  "The four criteria a buy-side mandate is matched on, and what each is
  worth. Weights sum to 100 so a score reads as a percentage without any
  division at compare time.

  A REAL, simplified model. What it does NOT capture: cultural fit,
  management continuity, customer concentration, earnings quality,
  synergy modelling, competitive process dynamics, or anything a human
  banker would actually weigh. It captures the four screens that are
  mechanically checkable from a mandate, which is exactly the part a
  machine should do."
  {:industry 40 :size 30 :geography 20 :deal-type 10})

(defn- criterion-met?
  [k buy-side sell-side]
  (case k
    :industry  (boolean (contains? (set (:target-industries buy-side)) (:industry sell-side)))
    :geography (boolean (contains? (set (:target-geographies buy-side)) (:geography sell-side)))
    :deal-type (boolean (contains? (set (:accepted-deal-types buy-side)) (:deal-type sell-side)))
    :size      (let [r (:revenue sell-side)
                     lo (:size-min buy-side)
                     hi (:size-max buy-side)]
                 (boolean (and (number? r) (number? lo) (number? hi)
                               (<= lo r) (<= r hi))))
    false))

(defn met-criteria
  "The criteria this pairing actually meets, sorted -- the human-readable
  evidence behind a score. `:pairing/explain` returns this; it writes
  nothing."
  [buy-side sell-side]
  (vec (sort (filter #(criterion-met? % buy-side sell-side) (keys criteria-weights)))))

(defn compute-fit-score
  "Integer 0..100 fit score for one (buy-side mandate, sell-side mandate)
  pairing. Sum of the weights of the criteria met. Total function: an
  absent field simply fails its criterion rather than throwing, because a
  half-filled mandate is a normal state of the world, not an error."
  [buy-side sell-side]
  (reduce + 0 (map criteria-weights (met-criteria buy-side sell-side))))

;; ----------------------------- confidentiality -----------------------------

(def confidential-fields
  "The sell-side fields that identify the target. THE definition -- the
  advisor may not widen it and the governor reads the same set, so the
  two cannot drift.

  Everything not listed here is disclosable pre-NDA. That polarity is
  deliberate: a set of 'safe' fields fails open when a new field is added
  to a mandate, and failing open here means naming a seller who did not
  consent to be named."
  #{:company-name :legal-name :domain :address :contact :owner-name :registry-id})

(defn blind-teaser
  "The anonymised profile a buyer may see before an NDA exists for the
  pairing. Drops every `confidential-fields` key and everything else that
  is not one of the disclosable screening attributes.

  Whitelist, not blacklist: a field this function has never heard of does
  not reach the buyer."
  [sell-side]
  (-> (select-keys sell-side [:id :industry :geography :deal-type :revenue :employees :rationale])
      (assoc :anonymised true)))

(defn leaking-nodes
  "Every map inside `value` that carries at least one confidential key,
  with the fields it carries and whatever sell-side id names it.

  Walks nested maps and sequences, because a leak one level down is still
  a leak and a shallow check that misses it returns exactly what a clean
  value returns. `:named-id` is taken from the leaking map itself, or
  inherited from the nearest enclosing map that named one -- a teaser
  nested under `{:sell-side-id \"sell-1\" :teaser {...}}` is about sell-1
  even though the teaser map does not repeat the id.

  A `nil` `:named-id` means the walk could not tell whose secret this is.
  Callers must treat that as worse than a known leak, not as an absence:
  an unattributable disclosure cannot be checked against any NDA."
  [value]
  (let [found (atom [])
        node-id (fn [m] (or (:sell-side-id m) (:id m)))
        walk (fn walk [v inherited]
               (cond
                 (map? v)
                 (let [here (or (node-id v) inherited)
                       ks (vec (sort (filter confidential-fields (keys v))))]
                   (when (seq ks) (swap! found conj {:fields ks :named-id here}))
                   (doseq [[_ vv] v] (walk vv here)))
                 (sequential? v) (doseq [vv v] (walk vv inherited))
                 :else nil))]
    (walk value nil)
    @found))

(defn confidential-leak
  "The confidential keys present in `value`, flattened -- the short answer
  to 'what is this proposal about to disclose'. `leaking-nodes` is the
  long answer, and is the one the governor uses, because knowing WHOSE
  secret leaked is what makes it checkable against an NDA."
  [value]
  (vec (sort (distinct (mapcat :fields (leaking-nodes value))))))

;; ----------------------------- introduction record -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signing an
  introduction is the licensed intermediary's act, not this actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn pairing-id
  "Stable identity of one (buy-side, sell-side) pairing. Every screening,
  NDA and consent record in `matching.store` is keyed by this, so it must
  be a pure function of the two ids and nothing else."
  [buy-side-id sell-side-id]
  (str buy-side-id "~" sell-side-id))

(defn register-introduction
  "Validate + construct the INTRODUCTION record draft -- the intermediary's
  own record of having disclosed a named target to a named buyer. Pure
  function: it builds the RECORD, it does not send anything to anyone.

  `matching.governor` independently re-derives `fit-score` via
  `compute-fit-score`, re-checks the NDA/consent/verification evidence and
  blocks a repeat introduction, before this is ever allowed to commit."
  [buy-side-id sell-side-id fit-score jurisdiction sequence]
  (when-not (and buy-side-id (not= buy-side-id ""))
    (throw (ex-info "introduction: buy_side_id required" {})))
  (when-not (and sell-side-id (not= sell-side-id ""))
    (throw (ex-info "introduction: sell_side_id required" {})))
  (when (or (neg? fit-score) (> fit-score 100))
    (throw (ex-info "introduction: fit_score must be within 0..100" {:fit-score fit-score})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "introduction: jurisdiction required" {})))
  (when (neg? sequence)
    (throw (ex-info "introduction: sequence must be >= 0" {})))
  (let [intro-number (str (str/upper-case jurisdiction) "-INT-" (zero-pad sequence 6))
        record {"record_id" intro-number
                "kind" "introduction-draft"
                "pairing_id" (pairing-id buy-side-id sell-side-id)
                "buy_side_id" buy-side-id
                "sell_side_id" sell-side-id
                "fit_score" fit-score
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record
     "introduction_number" intro-number
     "certificate" (unsigned-certificate "IntroductionCertificate" intro-number intro-number)}))

(defn append
  "Append an introduction record, returning a NEW list (never mutate
  history in place)."
  [history result]
  (conj (vec history) (get result "record")))
