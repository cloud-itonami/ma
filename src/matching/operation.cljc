(ns matching.operation
  "OperationActor -- one Matching-stage operation = one supervised actor
  run, expressed as a langgraph-clj StateGraph. The advisor (Match-LLM)
  is sealed into a single node (`:advise`); its proposal is ALWAYS routed
  through the Matching Governor (`:govern`) and the rollout phase gate
  (`:decide`) before anything reaches the SSoT.

  This is the runtime of the Matching stage of the deal pipeline that
  `ui/app.js` draws -- Sales, Marketing, Screening, **Matching**,
  Diligence, Valuation, Negotiation, Closing, PMI. The other eight stages
  are owned by sibling actors in `cloud-itonami`; the superproject's
  `manifest/ma-business.edn` is where that composition is written down,
  because no single repository can see it.

  Everything the actor depends on is injected, so each is a swap and not
  a rewrite:
    - the Store    (MemStore today; a datom-backed store is the next seam) -- `store` arg
    - the Advisor  (mock | real LLM)                                       -- :advisor opt
    - the Phase    (0->3 rollout)                                          -- :phase in ctx

  One graph run = one operation (intake -> advise -> govern -> decide ->
  commit | hold | approval). No unbounded inner loop: every operation is
  auditable and checkpointed.

  Human-in-the-loop is a real approval workflow.
  `interrupt-before #{:request-approval}` pauses the actor and hands the
  decision to a human intermediary. The approver resumes with
  `{:approval {:status :approved}}` (or `:rejected`). An
  `:introduction/make` ALWAYS reaches this node when the governor is
  clean -- see `matching.phase`."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [matching.matchllm :as matchllm]
            [matching.governor :as governor]
            [matching.phase :as phase]
            [matching.registry :as registry]
            [matching.store :as store]))

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-path
  "Which SSoT key a committed proposal writes under. Pairing-scoped
  effects are keyed by the pairing id so that screenings, NDAs and
  introductions all share one identity -- `matching.registry/pairing-id`
  is the single definition of it."
  [request proposal]
  (let [value (:value proposal)]
    (case (:effect proposal)
      :screening/set      [(or (:pairing-id value)
                               (registry/pairing-id (:buy-side-id request)
                                                    (:sell-side-id request)))]
      :introduction/record [(or (:pairing-id value)
                                (registry/pairing-id (:buy-side-id request)
                                                     (:sell-side-id request)))]
      :verification/set   [(or (:mandate-id value) (:subject request))]
      :shortlist/set      [(or (:mandate-id value) (:subject request))]
      [(:subject request)])))

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    (commit-path request proposal)
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store` (any
  `matching.store/Store`).
  opts:
    :advisor      -- a `matching.matchllm/Advisor` (default: mock-advisor)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (matchllm/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}   ; injected actor-id/role/phase
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}   ; :commit | :hold | :escalate
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; Match-LLM inference (the contained intelligence node) -- proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (matchllm/-advise advisor store request)]
            {:proposal p :audit [(matchllm/trace request p)]})))

      ;; Matching Governor -- independent censor (a separate system from the advisor).
      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      ;; Decide: governor disposition, then the rollout-phase gate (which can
      ;; only add caution). HARD governor violations -> HOLD, no override.
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:subject request)
                        :reason (or reason
                                    (cond (:high-stakes? verdict) :actuation
                                          :else :low-confidence))
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      ;; Approval handoff -- paused by interrupt-before; a human intermediary
      ;; resumes with :approval. Then route commit/hold.
      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal)
                                            :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (assoc verdict :violations
                                                       [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      ;; Commit -- the ONLY node that writes the SSoT + audit ledger.
      (g/add-node :commit
        (fn [{:keys [request context proposal record]}]
          (when-not (= :noop (:effect record))
            (store/commit-record! store record))
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! store f)
            {:audit [f]})))

      ;; Hold -- write the rejection to the ledger; no SSoT mutation.
      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
