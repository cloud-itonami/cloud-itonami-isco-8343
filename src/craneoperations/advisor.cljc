(ns craneoperations.advisor
  "Crane Operations Advisor — the proposal layer for the ISCO-08 8343
  Independent Crane & Hoist Operations Practice actor. Proposes
  lift-plan logging, load-reading recording, maintenance/inspection
  scheduling, and safety-concern flagging operations from requests,
  but never commits records itself, never actuates any real crane or
  hoist, and never makes governance decisions — that is
  `craneoperations.governor`'s job, downstream of this namespace.")

(defprotocol Advisor
  "Advisor protocol for proposing crane/hoist operations coordination
   operations."
  (propose [advisor request context]
    "Propose a coordination operation from a request. Returns a proposal map
     with :op, :effect (always :propose), :confidence, and supporting data."))

(defn mock-advisor
  "Default deterministic advisor that proposes standard crane-operations
   coordination operations based on request type. Always returns
   :propose effect. This actor coordinates paperwork ONLY (lift plans,
   load readings, maintenance scheduling, safety flags) — it never
   proposes real crane/hoist actuation and never overrides a rated-
   capacity limit."
  []
  (reify Advisor
    (propose [this request context]
      (let [req-type (:type request)
            op (case req-type
                 :log-lift-plan
                 {:op :log-lift-plan
                  :confidence 0.85
                  :equipment-id (:equipment-id request)
                  :load-planned (:load-planned request)
                  :radius (:radius request)
                  :boom-angle (:boom-angle request)}

                 :record-load-reading
                 {:op :record-load-reading
                  :confidence 0.9
                  :equipment-id (:equipment-id request)
                  :load-actual (:load-actual request)}

                 :schedule-maintenance
                 {:op :schedule-maintenance-inspection
                  :confidence 0.8
                  :equipment-id (:equipment-id request)
                  :inspection-type (:inspection-type request)
                  :due-date (:due-date request)}

                 :flag-safety-concern
                 {:op :flag-safety-concern
                  :confidence 0.95
                  :equipment-id (:equipment-id request)
                  :concern-type (:concern-type request)
                  :description (:description request)}

                 {:op :unknown :confidence 0.0})]
        (assoc op :effect :propose)))))

(defn llm-advisor
  "Advisor backed by an LLM (ChatModel). Always returns :propose effect;
   LLM parse failures yield confidence 0.0 (forces escalation)."
  [chat-model]
  (reify Advisor
    (propose [this request context]
      ; Placeholder: real implementation would call chat-model
      ; and parse response to extract :op, :confidence, etc.
      ; On any error, return confidence 0.0
      {:op :unknown :effect :propose :confidence 0.0})))
