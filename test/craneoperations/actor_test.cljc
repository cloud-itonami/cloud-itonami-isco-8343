(ns craneoperations.actor-test
  "End-to-end tests that `craneoperations.actor/build-graph` actually
   compiles AND runs against the real pinned `langgraph.graph` API
   (not just requires without error) — the reference pattern
   (`officer-admin.actor`) was never exercised this way, and turned
   out to call a `graph/state-graph-builder` fn that does not exist in
   the pinned dependency. Exercises ONLY public functions."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as graph]
            [langchain.runnable :as runnable]
            [craneoperations.actor :as actor]
            [craneoperations.advisor :as advisor]
            [craneoperations.store :as store]))

(defn- registered-store []
  (-> (store/create-store)
      (store/register-equipment! "crane-001"
                                  {:name "Liebherr LTM 1100"
                                   :rated-capacity 100000
                                   :maintenance-overdue? false})))

(deftest test-build-graph-compiles
  (testing "build-graph returns a compiled, invokable graph"
    (let [g (actor/build-graph (advisor/mock-advisor) (registered-store))]
      (is (some? g))
      (is (satisfies? runnable/IRunnable g)))))

(deftest test-clean-proposal-reaches-complete
  (testing "A within-capacity lift plan runs end-to-end to :complete"
    (let [st (registered-store)
          g (actor/build-graph (advisor/mock-advisor) st)
          request {:type :log-lift-plan :equipment-id "crane-001"
                    :load-planned 50000 :radius 10 :boom-angle 60}
          final (actor/run-request! g request {} st)]
      (is (= :complete (:phase final)))
      (is (= 1 (count (:records final))))
      (is (= :log-lift-plan (-> final :records first :op))))))

(deftest test-hard-violation-reaches-rejected
  (testing "An overload lift plan (load exceeds registered rated-capacity) is HARD-held to :rejected"
    (let [st (registered-store)
          g (actor/build-graph (advisor/mock-advisor) st)
          request {:type :log-lift-plan :equipment-id "crane-001"
                    :load-planned 150000 :radius 10 :boom-angle 60}
          final (actor/run-request! g request {} st)]
      (is (= :rejected (:phase final)))
      (is (true? (:hard? (:decision final))))
      (is (some? (:error final)))
      (is (= 0 (count (:records final)))))))

(deftest test-safety-concern-reaches-awaiting-approval
  (testing "A safety-concern flag always escalates to :awaiting-approval"
    (let [st (registered-store)
          g (actor/build-graph (advisor/mock-advisor) st)
          request {:type :flag-safety-concern :equipment-id "crane-001"
                    :concern-type :structural :description "visible boom deflection"}
          final (actor/run-request! g request {} st)]
      (is (= :awaiting-approval (:phase final)))
      (is (true? (:escalate? (:decision final))))
      (is (= 0 (count (:records final)))))))

(deftest test-unregistered-equipment-reaches-rejected
  (testing "A request against unregistered equipment is HARD-held (spec-basis / provenance)"
    (let [st (store/create-store)
          g (actor/build-graph (advisor/mock-advisor) st)
          request {:type :log-lift-plan :equipment-id "ghost-crane"
                    :load-planned 5000 :radius 10 :boom-angle 60}
          final (actor/run-request! g request {} st)]
      (is (= :rejected (:phase final))))))

(deftest test-approve!-advances-awaiting-approval-to-commit
  (testing "approve! moves an :awaiting-approval state to :commit with the approval context recorded"
    (let [st (registered-store)
          g (actor/build-graph (advisor/mock-advisor) st)
          request {:type :flag-safety-concern :equipment-id "crane-001"
                    :concern-type :structural :description "visible boom deflection"}
          held (actor/run-request! g request {} st)
          approved (actor/approve! held {:approver "site-supervisor"} st)]
      (is (= :awaiting-approval (:phase held)))
      (is (= :commit (:phase approved)))
      (is (= {:approver "site-supervisor"} (:approval approved))))))

(deftest test-clean-proposal-persists-to-store-ledger
  (testing "commit-node actually appends to the store's own audit ledger, not just the ephemeral state counter"
    (let [st (registered-store)
          g (actor/build-graph (advisor/mock-advisor) st)
          request {:type :log-lift-plan :equipment-id "crane-001"
                    :load-planned 50000 :radius 10 :boom-angle 60}
          final (actor/run-request! g request {} st)]
      (is (= 1 (count (store/records (:store final)))))
      (is (= :log-lift-plan (:type (first (store/records (:store final)))))))))

(deftest test-approve!-persists-to-store-ledger
  (testing "approve! also appends to the store's own audit ledger upon human sign-off"
    (let [st (registered-store)
          g (actor/build-graph (advisor/mock-advisor) st)
          request {:type :flag-safety-concern :equipment-id "crane-001"
                    :concern-type :structural :description "visible boom deflection"}
          held (actor/run-request! g request {} st)
          approved (actor/approve! held {:approver "site-supervisor"} st)]
      (is (= 1 (count (store/records (:store approved)))))
      (is (true? (:approved (first (store/records (:store approved)))))))))
