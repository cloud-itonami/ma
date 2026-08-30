(ns matching.phase
  "Phase 0->3 staged rollout for the Matching stage -- the M&A analog of
  `cloud-itonami-isic-6612`'s `brokerage.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- mandate intake allowed, every write needs
                                 human approval.
    Phase 2  assisted-screen  -- adds counterparty verification and pairing
                                 screening writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:mandate/intake` and `:shortlist/rank`
                                 may auto-commit.

  `:introduction/make` is deliberately ABSENT from every phase's `:auto`
  set, phase 3 included. That is a permanent structural fact, not a
  rollout milestone still ahead. Telling a named buyer that a named seller
  is for sale is the one act here that cannot be undone -- once a
  competitor knows a company is for sale, no later approval un-knows it.
  `matching.governor`'s `:actuation/make-introduction` high-stakes gate
  enforces the same invariant independently. Two layers, not one, agree.

  Screening ops (`:counterparty/verify`, `:pairing/screen`) are never
  auto-eligible either, at any phase -- the same posture every sibling's
  KYC/conflict screening op has, even though screening itself discloses
  nothing. A screening verdict is a compliance judgement, and the actor
  that would benefit from a favourable one should not be the one signing
  it off unattended.

  `:shortlist/rank` IS auto-eligible at phase 3, and the reason is worth
  stating because it is the one debatable entry in the table: a shortlist
  is internal to the intermediary. Its rows carry blind teasers, and the
  confidentiality gate in `matching.governor` HARD-holds if any row
  carries more than that. If that gate were ever weakened, this entry
  would have to go with it.

  Unlike the sibling actors this namespace has no integer safety kernel
  behind it yet; the phase table below is the deciding copy, not a
  human-readable restatement of one.")

(def read-ops
  "Ops that write nothing. `:pairing/explain` answers 'why was I shown
  this' -- a real question a buyer asks, whose answer must not be a
  mutation."
  #{:pairing/explain})

(def write-ops
  #{:mandate/intake :counterparty/verify :pairing/screen :shortlist/rank
    :introduction/make})

;; NOTE the invariant: `:introduction/make` is a member of `write-ops`
;; (governor-gated like any write) but is NEVER a member of any phase's
;; `:auto` set below. Do not add it there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed
  to auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                     :auto #{}}
   1 {:label "assisted-intake" :writes #{:mandate/intake}      :auto #{}}
   2 {:label "assisted-screen" :writes #{:mandate/intake :counterparty/verify
                                         :pairing/screen}      :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:mandate/intake :shortlist/rank}}})

(def default-phase 3)

(defn verdict->disposition
  "Map a Matching Governor verdict to a base disposition before the phase
  gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a read op is never phase-gated; it writes nothing.
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even when the governor was clean.
  - an op this namespace does not recognise -> HOLD. Fail closed: an
    unknown op must not inherit the permissions of a known one."
  [phase {:keys [op]} governor-disposition]
  (let [p (if (contains? phases phase) phase default-phase)
        {:keys [writes auto]} (get phases p)]
    (cond
      (= :hold governor-disposition) {:disposition :hold :reason nil}
      (contains? read-ops op) {:disposition governor-disposition :reason nil}
      (not (contains? write-ops op)) {:disposition :hold :reason :phase-disabled}
      (not (contains? writes op)) {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition) (not (contains? auto op)))
      {:disposition :escalate :reason :phase-approval}
      :else {:disposition governor-disposition :reason nil})))
