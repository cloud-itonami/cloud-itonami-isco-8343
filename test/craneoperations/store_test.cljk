(ns craneoperations.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [craneoperations.store :as store]))

(deftest test-create-store
  (testing "Create a new store"
    (let [s (store/create-store)]
      (is (not (nil? s)))
      (is (satisfies? store/Store s)))))

(deftest test-register-equipment
  (testing "Register and retrieve an equipment record"
    (let [s (store/create-store)
          s' (store/register-equipment! s "crane-001"
                                         {:name "Liebherr LTM 1100"
                                          :rated-capacity 100000
                                          :maintenance-overdue? false})
          retrieved (store/equipment s' "crane-001")]
      (is (= (:name retrieved) "Liebherr LTM 1100"))
      (is (= (:rated-capacity retrieved) 100000))
      (is (false? (:maintenance-overdue? retrieved))))))

(deftest test-unregistered-equipment-returns-nil
  (testing "Unregistered equipment returns nil"
    (let [s (store/create-store)]
      (is (nil? (store/equipment s "nonexistent"))))))

(deftest test-add-record
  (testing "Add and retrieve records from audit ledger"
    (let [s (store/create-store)
          s' (store/add-record! s :lift-plan {:equipment-id "crane-001" :load-planned 5000})
          recs (store/records s')]
      (is (= (count recs) 1))
      (is (= (:type (first recs)) :lift-plan))
      (is (= (:equipment-id (first recs)) "crane-001")))))

(deftest test-ledger-is-append-only
  (testing "Multiple records accumulate in order"
    (let [s (-> (store/create-store)
                (store/add-record! :lift-plan {:equipment-id "crane-001"})
                (store/add-record! :load-reading {:equipment-id "crane-001"}))
          recs (store/records s)]
      (is (= (count recs) 2))
      (is (= (mapv :type recs) [:lift-plan :load-reading])))))

(deftest test-immutability
  (testing "Store operations return new store instances, never mutate in place"
    (let [s (store/create-store)
          s' (store/register-equipment! s "crane-001"
                                         {:name "Liebherr LTM 1100" :rated-capacity 100000})
          eq-in-s (store/equipment s "crane-001")
          eq-in-s' (store/equipment s' "crane-001")]
      (is (nil? eq-in-s))
      (is (not (nil? eq-in-s')))
      (is (= (:name eq-in-s') "Liebherr LTM 1100")))))
