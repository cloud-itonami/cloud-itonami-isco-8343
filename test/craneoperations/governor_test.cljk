(ns craneoperations.governor-test
  "Governor tests exercise ONLY public functions (`gov/check`,
   `store/create-store`, `store/register-equipment!`) — never a private
   var directly. (cloud-itonami-isic-0710 broke for a full day earlier
   in this fleet's history from exactly that mistake; do not repeat it.)"
  (:require [clojure.test :refer [deftest is testing]]
            [craneoperations.governor :as gov]
            [craneoperations.store :as store]))

(defn- registered-store []
  (-> (store/create-store)
      (store/register-equipment! "crane-001"
                                  {:name "Liebherr LTM 1100"
                                   :rated-capacity 100000
                                   :maintenance-overdue? false})))

;; ------------------------- HARD violations -------------------------

(deftest test-unregistered-equipment-is-hard-violation
  (testing "Unregistered equipment reference is a hard violation (spec-basis / provenance)"
    (let [test-store (store/create-store)
          request {:equipment-id "unknown-crane"}
          context {}
          proposal {:op :log-lift-plan :effect :propose :confidence 0.9
                     :load-planned 5000 :radius 10 :boom-angle 60}
          verdict (gov/check request context proposal test-store)]
      (is (true? (:hard? verdict)))
      (is (false? (:ok? verdict)))
      (is (= :no-equipment-provenance (:rule (first (:violations verdict))))))))

(deftest test-wrong-effect-is-hard-violation
  (testing "Non-:propose effect is a hard violation (no real actuation)"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :log-lift-plan :effect :execute :confidence 0.9
                     :load-planned 5000 :radius 10 :boom-angle 60}
          verdict (gov/check request context proposal test-store)]
      (is (true? (:hard? verdict)))
      (is (seq (:violations verdict))))))

(deftest test-forbidden-actuation-ops-are-hard-blocked
  (testing "Real crane/hoist actuation ops and rated-capacity override are permanently forbidden"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}]
      (doseq [forbidden-op [:actuate-crane :engage-hoist :move-load
                             :operate-boom :override-rated-capacity :unknown]]
        (let [proposal {:op forbidden-op :effect :propose :confidence 0.9}
              verdict (gov/check request context proposal test-store)]
          (is (true? (:hard? verdict)) (str forbidden-op " should be hard-blocked"))
          (is (false? (:ok? verdict)) (str forbidden-op " should not be ok")))))))

(deftest test-load-planned-exceeds-rated-capacity-is-hard-violation
  (testing "A planned lift load above the equipment's own registered rated-capacity is a hard violation"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :log-lift-plan :effect :propose :confidence 0.9
                     :load-planned 150000 :radius 10 :boom-angle 60}
          verdict (gov/check request context proposal test-store)]
      (is (true? (:hard? verdict)))
      (is (= :load-exceeds-rated-capacity (:rule (first (:violations verdict))))))))

(deftest test-load-actual-exceeds-rated-capacity-is-hard-violation
  (testing "A recorded actual load reading above rated capacity is a hard violation"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :record-load-reading :effect :propose :confidence 0.9
                     :load-actual 150000}
          verdict (gov/check request context proposal test-store)]
      (is (true? (:hard? verdict)))
      (is (= :load-exceeds-rated-capacity (:rule (first (:violations verdict))))))))

(deftest test-negative-load-reading-is-hard-violation
  (testing "A physically-impossible negative load reading is a hard violation"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :record-load-reading :effect :propose :confidence 0.9
                     :load-actual -10}
          verdict (gov/check request context proposal test-store)]
      (is (true? (:hard? verdict))))))

(deftest test-load-exactly-at-rated-capacity-is-not-a-violation
  (testing "A load exactly at the rated-capacity ceiling is within range (boundary check)"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :record-load-reading :effect :propose :confidence 0.9
                     :load-actual 100000}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict))))))

;; ------------------------- passing proposals -------------------------

(deftest test-lift-plan-within-capacity-passes
  (testing "A lift plan within rated capacity is not a hard violation and is ok"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :log-lift-plan :effect :propose :confidence 0.9
                     :load-planned 50000 :radius 10 :boom-angle 60}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict)))
      (is (true? (:ok? verdict))))))

(deftest test-load-reading-within-capacity-passes
  (testing "A load reading within rated capacity is not a hard violation and does not escalate"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :record-load-reading :effect :propose :confidence 0.9
                     :load-actual 40000}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict)))
      (is (true? (:ok? verdict))))))

;; ------------------------- escalations -------------------------

(deftest test-safety-concern-always-escalates
  (testing "Safety concern proposals always escalate, regardless of confidence"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :flag-safety-concern :effect :propose :confidence 0.99
                     :concern-type :structural :description "visible boom deflection"}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict)))
      (is (false? (:ok? verdict))))))

(deftest test-low-confidence-escalation
  (testing "Low confidence (<0.6) triggers escalation"
    (let [test-store (registered-store)
          request {:equipment-id "crane-001"}
          context {}
          proposal {:op :schedule-maintenance-inspection :effect :propose :confidence 0.4
                     :inspection-type :annual}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest test-maintenance-overdue-equipment-escalates-lift-plan
  (testing "A lift-plan proposal against equipment flagged maintenance-overdue escalates"
    (let [test-store (-> (store/create-store)
                          (store/register-equipment! "crane-002"
                                                      {:name "Grove GMK5250L"
                                                       :rated-capacity 250000
                                                       :maintenance-overdue? true}))
          request {:equipment-id "crane-002"}
          context {}
          proposal {:op :log-lift-plan :effect :propose :confidence 0.9
                     :load-planned 50000 :radius 10 :boom-angle 60}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest test-maintenance-overdue-equipment-escalates-load-reading
  (testing "A load-reading proposal against equipment flagged maintenance-overdue escalates"
    (let [test-store (-> (store/create-store)
                          (store/register-equipment! "crane-002"
                                                      {:name "Grove GMK5250L"
                                                       :rated-capacity 250000
                                                       :maintenance-overdue? true}))
          request {:equipment-id "crane-002"}
          context {}
          proposal {:op :record-load-reading :effect :propose :confidence 0.9
                     :load-actual 50000}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest test-maintenance-scheduling-on-overdue-equipment-does-not-escalate
  (testing "Scheduling maintenance itself for overdue equipment is the desired remedy, not escalated"
    (let [test-store (-> (store/create-store)
                          (store/register-equipment! "crane-002"
                                                      {:name "Grove GMK5250L"
                                                       :rated-capacity 250000
                                                       :maintenance-overdue? true}))
          request {:equipment-id "crane-002"}
          context {}
          proposal {:op :schedule-maintenance-inspection :effect :propose :confidence 0.9
                     :inspection-type :annual}
          verdict (gov/check request context proposal test-store)]
      (is (false? (:hard? verdict)))
      (is (false? (:escalate? verdict)))
      (is (true? (:ok? verdict))))))
