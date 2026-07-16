(ns craneoperations.actor
  "Crane Operations Actor — the langgraph StateGraph wiring and runtime
  for the ISCO-08 8343 Independent Crane & Hoist Operations Practice
  advisor. Implements the itonami actor pattern with Advisor/Governor
  separation and an append-only audit trail. This actor never controls
  a real crane or hoist — 'policy, not control' — and never overrides
  a real load-chart/rated-capacity limit; see `craneoperations.governor`
  for the independent checks that enforce this boundary.

  Wired against `langgraph.graph`'s REAL API
  (`state-graph`/`add-node`/`add-edge`/`add-conditional-edges`/
  `compile-graph`/`invoke`) — NOT a `state-graph-builder` convenience
  fn, which does not exist in the pinned `kotoba-lang/langgraph`
  dependency. `build-graph` is exercised end-to-end (compiles AND
  actually runs through `graph/invoke`) in `craneoperations.actor-
  test`, not just required-without-erroring.

  This build does NOT wire a checkpointer/thread-id, so it does not
  implement genuine cross-turn interrupt suspension — `:request-
  approval` and `:hold` are terminal outcomes of a single `graph/
  invoke` call (the graph runs straight through to one of :complete/
  :awaiting-approval/:rejected in one pass), not a paused execution a
  later call resumes via `langgraph.graph/update-state!`. `approve!`
  is provided for API symmetry with this fleet's other itonami actors
  (compute the post-approval commit state from an :awaiting-approval
  state) — it does not resume a suspended graph. Real cross-turn
  human-in-the-loop suspension (checkpointer + thread-id +
  `:interrupt-before`) is a follow-up, not implemented here."
  (:require [langgraph.graph :as graph]
            [craneoperations.store :as store]
            [craneoperations.advisor :as advisor]
            [craneoperations.governor :as governor]))

(def default-state
  {:phase :intake
   :request nil
   :context {}
   :proposal nil
   :decision nil
   :records []
   :error nil})

(defn- intake-node
  "Intake node: accept and validate incoming request."
  [state]
  (assoc state :phase :advise))

(defn- advise-node
  "Advise node: Advisor proposes an operation."
  [state advisor-instance]
  (let [request (:request state)
        context (:context state)
        proposal (advisor/propose advisor-instance request context)]
    (assoc state :proposal proposal :phase :govern)))

(defn- govern-node
  "Govern node: Governor evaluates the proposal."
  [state store-instance]
  (let [request (:request state)
        context (:context state)
        proposal (:proposal state)
        verdict (governor/check request context proposal store-instance)]
    (assoc state :decision verdict :phase :decide)))

(defn- decide-node
  "Decide node: pure routing decision based on governor verdict. Used
   both to update :phase and, via `route-decision` below, as the
   `langgraph.graph/add-conditional-edges` router (the resulting
   :phase value doubles as the target node name — :commit /
   :request-approval / :hold are graph node names)."
  [state]
  (let [decision (:decision state)]
    (cond
      (:hard? decision)      (assoc state :phase :hold)
      (:escalate? decision)  (assoc state :phase :request-approval)
      true                   (assoc state :phase :commit))))

(defn- route-decision
  "Conditional-edge router for the :govern -> {:commit,
   :request-approval, :hold} branch. Returns the target node name."
  [state]
  (:phase (decide-node state)))

(defn- commit-node
  "Commit node: Store the proposal as a record."
  [state store-instance]
  (let [proposal (:proposal state)
        op (:op proposal)]
    (-> state
        (assoc :phase :complete)
        (update :records (fn [r] (conj (or r []) {:recorded true :op op}))))))

(defn- request-approval-node
  "Request approval node: terminal outcome for this invoke, marking the
   proposal as awaiting human sign-off (see ns docstring — no real
   checkpoint-based suspension is wired in this build)."
  [state]
  (assoc state :phase :awaiting-approval))

(defn- hold-node
  "Hold node: Reject the proposal (hard violation, never overridable)."
  [state]
  (assoc state
         :phase :rejected
         :error (str "Hard governance violation: " (-> state :decision :violations))))

(defn build-graph
  "Build and compile the StateGraph for the crane operations actor:
   intake -> advise -> govern -> {commit | request-approval | hold}.
   Returns a `langgraph.graph/CompiledGraph`, invokable via
   `langgraph.graph/invoke`."
  [advisor-instance store-instance]
  (-> (graph/state-graph)
      (graph/add-node :intake (fn [s] (intake-node s)))
      (graph/add-node :advise (fn [s] (advise-node s advisor-instance)))
      (graph/add-node :govern (fn [s] (govern-node s store-instance)))
      (graph/add-node :commit (fn [s] (commit-node s store-instance)))
      (graph/add-node :request-approval (fn [s] (request-approval-node s)))
      (graph/add-node :hold (fn [s] (hold-node s)))
      (graph/set-entry-point :intake)
      (graph/add-edge :intake :advise)
      (graph/add-edge :advise :govern)
      (graph/add-conditional-edges :govern route-decision)
      (graph/set-finish-point :commit)
      (graph/set-finish-point :request-approval)
      (graph/set-finish-point :hold)
      (graph/compile-graph)))

(defn run-request!
  "Run an operational request through the compiled actor graph to
   completion. Returns the final state map (`:phase` one of
   :complete/:awaiting-approval/:rejected — see ns docstring for what
   :awaiting-approval/:rejected do and do not mean in this build).
   `store` is accepted for API symmetry with `build-graph` but is not
   itself consulted here — `graph` already closes over the
   advisor/store instances it was built with."
  [compiled-graph initial-request context store]
  (let [state (assoc default-state :request initial-request :context context)]
    (graph/invoke compiled-graph state)))

(defn approve!
  "Approve a request that reached :awaiting-approval. Human sign-off
   for escalation invariants. Computes the post-approval commit state
   directly — see ns docstring: this does not resume a suspended
   graph (none is suspended in this build), it advances the state the
   caller already holds."
  [state approval-context store]
  (assoc state :phase :commit :approval approval-context))
