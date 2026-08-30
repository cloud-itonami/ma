(ns matching.governor
  "Matching Governor -- the independent censor that earns the Match-LLM
  the right to commit. The M&A analog of `cloud-itonami-isic-6612`'s
  BrokerageGovernor.

  The advisor has no notion of whether an NDA is actually executed for a
  pairing, whether the seller actually consented to be shown to THIS
  buyer, whether a counterparty was ever verified, whether the fit score
  it just quoted is the one the arithmetic produces, or when naming a
  target stops being a draft and becomes a disclosure that cannot be
  taken back. So this MUST be a separate system, able to REJECT a
  proposal and fall back to HOLD.

  Eight checks. Seven are HARD: a human approver cannot override them.
  You do not get to approve your way past an invented jurisdiction rule,
  an unverified counterparty, a seller's name sent to a buyer with no
  NDA, a seller who said no to this buyer, a conflicted adviser, a
  fabricated fit score, or introducing the same pair twice. The eighth,
  the confidence/actuation gate, is SOFT: it asks a human to look, and
  the human may say yes.

    1. Spec-basis            -- did the proposal cite an OFFICIAL source
                                (`matching.facts`) for this jurisdiction's
                                approach rules, or invent one? For
                                `:introduction/make`, only when the pairing's
                                mandates actually exist.
    2. Evidence incomplete   -- for `:introduction/make`, is there a committed
                                `:eligible` screening on file, and does the
                                jurisdiction's required-evidence checklist
                                actually hold?
    3. Counterparty unverified
                             -- for `:shortlist/rank` and
                                `:introduction/make`, does EVERY counterparty
                                involved carry a committed `:verified`
                                verdict? An unverified party may not enter a
                                shortlist, let alone an introduction.
    4. Confidentiality       -- THE invariant of this stage. Does the
                                proposal's own `:value` carry a seller's
                                confidential fields into something addressed
                                to a BUYER, without an executed NDA for that
                                exact pairing? Evaluated UNCONDITIONALLY --
                                not scoped to an op -- so that a shortlist,
                                an introduction, or an op that does not exist
                                yet are all covered by the same rule.
                                Buyer-facing is decided from the DATA (does
                                the value address a buy-side mandate?), never
                                from the op name, so a new op cannot slip past
                                by not being on a list.
    5. Seller consent        -- for `:introduction/make`, does the seller's
                                own consent policy admit this buyer, and is
                                the buyer absent from the seller's no-go list?
                                A seller's veto over who is told is not
                                advisory.
    6. Conflict of interest  -- does THIS proposal report a conflict, or does
                                either side of the pairing carry one on file?
                                Evaluated UNCONDITIONALLY, so `:pairing/screen`
                                can HARD-hold on its own finding rather than
                                needing an actuation op to be attempted first.
    7. Fit-score mismatch    -- INDEPENDENTLY recompute every quoted score via
                                `matching.registry/compute-fit-score` and
                                compare EXACTLY. The score is an integer sum
                                of criterion weights, so there is no tolerance
                                window a fabricated number could hide in.
    8. Confidence floor / actuation gate
                             -- confidence below the floor, OR the op is
                                `:introduction/make` (a real, irreversible
                                disclosure) -> escalate to a human.

  One further guard, `double-introduction-violations`, is enforced but not
  numbered above because it needs no cross-record comparison at all: it
  refuses to introduce the same pairing twice, off this actor's own
  introduction history.

  A note on what is NOT here. The sibling actors delegate the decision to
  an integer-coded safety kernel (`brokerage.kernels.gate`) compiled to
  Wasm. This governor has no kernel yet; the decision is plain Clojure.
  Saying so is better than implying parity -- and every check below is
  written total and fail-closed so that a kernel, when it arrives, is a
  restatement rather than a repair."
  (:require [matching.facts :as facts]
            [matching.registry :as registry]
            [matching.store :as store]))

(def confidence-floor
  "Below this, escalate to a human even with no violations."
  0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Telling a named buyer that a named seller is for sale is the ONE
  real-world act of this stage, and it cannot be taken back."
  #{:actuation/make-introduction})

;; ----------------------------- helpers -----------------------------

(defn- pairing-sides
  "The (buy-side-id, sell-side-id) a request concerns, or nil. Reads the
  request rather than the proposal: the advisor does not get to choose
  which pairing it is judged against."
  [{:keys [op subject buy-side-id sell-side-id]}]
  (cond
    (and buy-side-id sell-side-id) [buy-side-id sell-side-id]
    (= op :shortlist/rank) [subject nil]
    :else nil))

(defn- verified? [st id]
  (and id (= :verified (:verdict (store/verification-of st id)))))

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:pairing/screen` (or `:introduction/make`) proposal with no
  spec-basis citation is a HARD violation -- never invent a jurisdiction's
  approach rules. For `:introduction/make`, only applies once both
  mandates actually exist."
  [{:keys [op] :as request} proposal st]
  (when (contains? #{:pairing/screen :introduction/make} op)
    (let [[buy-id sell-id] (pairing-sides request)]
      (when (or (not= op :introduction/make)
                (and (store/mandate st buy-id) (store/mandate st sell-id)))
        (let [value (:value proposal)]
          (when (or (empty? (:cites proposal))
                    (and (contains? value :spec-basis) (nil? (:spec-basis value))))
            [{:rule :no-spec-basis
              :detail "公式spec-basisの引用が無い提案を法域の開示要件として扱えない"}]))))))

(defn- evidence-incomplete-violations
  "For `:introduction/make`, the pairing must carry a committed
  `:eligible` screening AND satisfy the jurisdiction's required-evidence
  checklist. The advisor's own confidence is not evidence."
  [{:keys [op] :as request} st]
  (when (= op :introduction/make)
    (let [[buy-id sell-id] (pairing-sides request)
          sell (store/mandate st sell-id)]
      (when (and (store/mandate st buy-id) sell)
        (let [pid (registry/pairing-id buy-id sell-id)
              sc (store/screening-of st pid)]
          (when-not (and sc
                         (= :eligible (:verdict sc))
                         (facts/required-evidence-satisfied?
                          (:jurisdiction sell) (:checklist sc)))
            [{:rule :evidence-incomplete
              :detail (str pid " のスクリーニング結果 (適合判定・必要書類) が充足していない")}]))))))

(defn- counterparties-in
  "Every counterparty id a proposal involves. For a shortlist that is the
  buy-side mandate plus every sell-side id it ranks; for an introduction,
  both sides."
  [{:keys [op] :as request} proposal]
  (case op
    :shortlist/rank
    (into [(:subject request)]
          (keep :sell-side-id (get-in proposal [:value :entries])))
    :introduction/make (vec (remove nil? (pairing-sides request)))
    []))

(defn- counterparty-unverified-violations
  "For `:shortlist/rank` and `:introduction/make`, every counterparty must
  carry a committed `:verified` verdict. HARD -- an unverified party does
  not enter a shortlist."
  [request proposal st]
  (let [ids (counterparties-in request proposal)
        bad (vec (sort (distinct (remove #(verified? st %) ids))))]
    (when (seq bad)
      [{:rule :counterparty-unverified
        :detail (str "本人確認が未了の相手方: " (pr-str bad))}])))

(defn- buyer-addressee
  "The buy-side mandate this proposal's value is addressed TO, or nil.
  Decided from the data -- an id in the value that resolves to a `:side
  :buy` mandate -- never from the op name, so an op that does not exist
  yet is covered by the same rule."
  [proposal st]
  (let [value (:value proposal)
        candidates (remove nil? [(:buy-side-id value) (:mandate-id value) (:id value)])]
    (first (filter #(= :buy (:side (store/mandate st %))) candidates))))

(defn- confidentiality-violations
  "THE invariant. A seller's confidential fields may not reach a buyer
  without an executed NDA for that exact pairing.

  Three ways to fail, all HARD:
    - a leak whose owner cannot be identified (fail closed: an
      unattributable disclosure cannot be checked against any NDA)
    - a leak attributed to a seller with no executed NDA for the pairing
    - a leak attributed to an id that is not a sell-side mandate at all

  Evaluated UNCONDITIONALLY. A `:mandate/intake` patch legitimately
  carries a company name -- it is the seller describing themselves -- and
  is not caught here because its value addresses no buyer."
  [proposal st]
  (let [nodes (registry/leaking-nodes (:value proposal))]
    (when (seq nodes)
      (when-let [buyer (buyer-addressee proposal st)]
        (let [bad (for [{:keys [fields named-id]} nodes
                        :let [sell (when named-id (store/mandate st named-id))
                              nda (when named-id
                                    (store/nda-of st (registry/pairing-id buyer named-id)))]
                        :when (not (and sell
                                        (= :sell (:side sell))
                                        (= :executed (:status nda))))]
                    {:named-id named-id :fields fields})]
          (when (seq bad)
            [{:rule :confidentiality-breach
              :detail (str buyer " 向けの提案に、NDA未締結(または帰属不明)の売り手の非公開情報が含まれる: "
                           (pr-str (vec bad)))}]))))))

(defn- seller-consent-violations
  "For `:introduction/make`, the seller's own consent policy must admit
  this buyer. `:no-go` is an absolute veto; `:explicit` requires the buyer
  to be named in `:consented`; `:any-verified` admits any verified buyer.
  An unrecognized policy admits no one -- fail closed."
  [{:keys [op] :as request} st]
  (when (= op :introduction/make)
    (let [[buy-id sell-id] (pairing-sides request)
          sell (store/mandate st sell-id)]
      (when sell
        (let [policy (:consent-policy sell)
              ok? (and (not (contains? (set (:no-go sell)) buy-id))
                       (case policy
                         :any-verified (verified? st buy-id)
                         :explicit (contains? (set (:consented sell)) buy-id)
                         false))]
          (when-not ok?
            [{:rule :seller-consent-missing
              :detail (str sell-id " は " buy-id " への開示に同意していない (policy "
                           (pr-str policy) ")")}]))))))

(defn- conflict-of-interest-violations
  "A conflict reported by THIS proposal, or carried on file by either side
  of the pairing. Evaluated UNCONDITIONALLY so that `:pairing/screen` can
  HARD-hold on its own finding."
  [request proposal st]
  (let [in-proposal? (= :conflict-of-interest (get-in proposal [:value :reason]))
        sides (remove nil? (or (pairing-sides request) []))
        on-file? (boolean (some #(:conflict-hit? (store/mandate st %)) sides))]
    (when (or in-proposal? on-file?)
      [{:rule :conflict-of-interest
        :detail "利益相反のある当事者を含む提案は進められない"}])))

(defn- quoted-scores
  "Every (buy-side-id, sell-side-id, claimed-score) a proposal quotes."
  [{:keys [op] :as request} proposal]
  (case op
    :shortlist/rank
    (for [e (get-in proposal [:value :entries])]
      [(:subject request) (:sell-side-id e) (:fit-score e)])
    :introduction/make
    (let [[b s] (pairing-sides request)]
      [[b s (get-in proposal [:value :fit-score])]])
    []))

(defn- fit-score-mismatch-violations
  "INDEPENDENTLY recompute every quoted fit score and compare EXACTLY.
  Never trusts a claimed figure. Integer arithmetic on both sides, so
  there is no tolerance window."
  [request proposal st]
  (let [bad (for [[buy-id sell-id claimed] (quoted-scores request proposal)
                  :let [b (store/mandate st buy-id)
                        sell (store/mandate st sell-id)]
                  :when (and b sell)
                  :let [recomputed (registry/compute-fit-score b sell)]
                  :when (not= recomputed claimed)]
              {:pairing (registry/pairing-id buy-id sell-id)
               :claimed claimed :recomputed recomputed})]
    (when (seq bad)
      [{:rule :fit-score-mismatch
        :detail (str "申告された適合スコアが独自再計算値と一致しない: " (pr-str (vec bad)))}])))

(defn- double-introduction-violations
  "Refuses to introduce the same pairing twice, off this actor's own
  introduction history."
  [{:keys [op] :as request} st]
  (when (= op :introduction/make)
    (let [[buy-id sell-id] (pairing-sides request)
          pid (registry/pairing-id buy-id sell-id)]
      (when (store/pairing-already-introduced? st pid)
        [{:rule :double-introduction
          :detail (str pid " は既に引き合わせ済み")}]))))

;; ----------------------------- verdict -----------------------------

(defn check
  "Censors a Match-LLM proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
   :high-stakes? bool :hard? bool}.

  Fail-closed by construction: a confidence that is not a number, or one
  outside 0..1, escalates rather than counting as high."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal st)
                           (evidence-incomplete-violations request st)
                           (counterparty-unverified-violations request proposal st)
                           (confidentiality-violations proposal st)
                           (seller-consent-violations request st)
                           (conflict-of-interest-violations request proposal st)
                           (fit-score-mismatch-violations request proposal st)
                           (double-introduction-violations request st)))
        raw (:confidence proposal)
        conf (if (number? raw) (double raw) 0.0)
        in-range? (and (number? raw) (<= 0.0 conf 1.0))
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (seq hard)
        escalate? (and (not hard?) (or stakes? (not in-range?) (< conf confidence-floor)))]
    {:ok?          (boolean (and (not hard?) (not escalate?)))
     :violations   hard
     :confidence   conf
     :hard?        (boolean hard?)
     :escalate?    (boolean escalate?)
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
