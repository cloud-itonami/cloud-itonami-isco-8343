(ns craneoperations.store
  "Crane Operations Store — the append-only audit ledger and persistent
  state for the ISCO-08 8343 Independent Crane & Hoist Operations
  Practice actor. Implements the Store protocol for crane/hoist
  equipment registration and operational record-keeping.

  Each equipment record's own disclosed `:rated-capacity` (kg, taken
  from the unit's real load chart) is a permanent ground-truth field
  `craneoperations.governor` independently re-verifies proposed lift
  loads against — this actor never invents a crane/hoist unit or its
  rated capacity, and never takes a rated-capacity value from a
  proposal's own self-report.")

(defprotocol Store
  "Store protocol for crane operations actor state and audit ledger."
  (equipment [store equipment-id]
    "Retrieve a crane/hoist equipment record by ID. Returns nil if not found.")
  (register-equipment! [store equipment-id equipment-data]
    "Register a crane/hoist equipment unit (adds to store, returns updated
     store). `equipment-data` carries the unit's own disclosed
     :rated-capacity (kg, from its load chart) and :maintenance-overdue? —
     real, disclosed fields this actor never fabricates.")
  (add-record! [store record-type record-data]
    "Append an immutable operational record to the audit ledger.")
  (records [store]
    "Return all records in the audit ledger (immutable)."))

(defrecord MemStore [equipment-units ledger]
  Store
  (equipment [this equipment-id]
    (get equipment-units equipment-id))
  (register-equipment! [this equipment-id equipment-data]
    (MemStore. (assoc equipment-units equipment-id equipment-data) ledger))
  (add-record! [this record-type record-data]
    (let [record (assoc record-data :type record-type :timestamp #?(:clj (System/currentTimeMillis) :cljs (.getTime (js/Date.))))]
      (MemStore. equipment-units (conj ledger record))))
  (records [this]
    ledger))

(defn create-store
  "Create a new in-memory store for crane operations records."
  []
  (MemStore. {} []))
