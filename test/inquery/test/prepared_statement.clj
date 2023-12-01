(ns inquery.test.prepared-statement
  (:require [clojure.test :refer [deftest is testing]]
            [inquery.core :as q]))

(deftest should-sub-params
  (testing "should sub params"
    (let [q   "select * from planets where mass <= :max-mass and name = :name"
          sub (fn [q m] (-> q (q/with-params m {:prepared? true})))]
      (is (= ["select * from planets where mass <= ? and name = ?" 42 "mars"] (sub q {:max-mass 42 :name "mars"})))
      (is (= ["select * from planets where mass <= ? and name = ?" 42 nil] (sub q {:max-mass 42 :name nil})))
      (is (= ["select * from planets where mass <= ? and name = ?" nil ""] (sub q {:max-mass nil :name ""})))
      (is (= ["select * from planets where mass <= ? and name = ?" 42 {:as ""}] (sub q {:max-mass 42 :name {:as ""}})))
      (is (= ["select * from planets where mass <= ? and name = ?" {:as 42} {:as "42"}] (sub q {:max-mass {:as 42} :name {:as "42"}})))
      (is (= ["select * from planets where mass <= ? and name = ?" {:as nil} "''"] (sub q {:max-mass {:as nil} :name "''"})))
      (is (= ["select * from planets where mass <= ? and name = ?" {:as "''"} "''"] (sub q {:max-mass {:as "''"} :name "''"}))))))
(deftest should-sub-starts-with-params
  (testing "should correctly sub params that start with the same prefix"
    (let [q   "select * from planets where moons = :super-position-moons and mass <= :super and name = :super-position"
          sub (fn [q m] (-> q (q/with-params m {:prepared? true})))]
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ?" "up-and-down" 42 "quettabit"]
             (sub q {:super                42
                     :super-position       "quettabit"
                     :super-position-moons "up-and-down"})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ?" "up-and-down" 42 "quettabit"]
             (sub q {:super-position       "quettabit"
                     :super                42
                     :super-position-moons "up-and-down"})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ?" "up-and-down" 42 "quettabit"]
             (sub q {:super-position       "quettabit"
                     :super-position-moons "up-and-down"
                     :super                42})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ?" "up-and-down" 42 "quettabit"]
             (sub q {:super-position-moons "up-and-down"
                     :super-position       "quettabit"
                     :super                42})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ?" "up-and-down" 42 "quettabit"]
             (sub q {:super-position-moons "up-and-down"
                     :super                42
                     :super-position       "quettabit"})))))

  (testing "should correctly sub params that start with the same prefix, even when some of the keys have the same length"
    (let [q   "select * from planets where moons = :super-position-moons and mass <= :super and name = :super-position and orbital_offset = :orbital-offset and orbital_offset_looks = :orbital-offset-looks"
          sub (fn [q m] (-> q (q/with-params m {:prepared? true})))]
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ? and orbital_offset = ? and orbital_offset_looks = ?" "up-and-down" 42 "quettabit" "acute" "wobbly"]
             (sub q {:super-position-moons "up-and-down"
                     :orbital-offset-looks "wobbly"
                     :orbital-offset       "acute"
                     :super-position       "quettabit"
                     :super                42})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ? and orbital_offset = ? and orbital_offset_looks = ?" "up-and-down" 42 "quettabit" "acute" "wobbly"]
             (sub q {:orbital-offset-looks "wobbly"
                     :super-position-moons "up-and-down"
                     :super-position       "quettabit"
                     :orbital-offset       "acute"
                     :super                42})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ? and orbital_offset = ? and orbital_offset_looks = ?" "up-and-down" 42 "quettabit" "acute" "wobbly"]
             (sub q {:super                42
                     :super-position       "quettabit"
                     :super-position-moons "up-and-down"
                     :orbital-offset       "acute"
                     :orbital-offset-looks "wobbly"})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ? and orbital_offset = ? and orbital_offset_looks = ?" "up-and-down" 42 "quettabit" "acute" "wobbly"]
             (sub q {:super-position       "quettabit"
                     :super-position-moons "up-and-down"
                     :super                42
                     :orbital-offset       "acute"
                     :orbital-offset-looks "wobbly"})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ? and orbital_offset = ? and orbital_offset_looks = ?" "up-and-down" 42 "quettabit" "acute" "wobbly"]
             (sub q {:orbital-offset-looks "wobbly"
                     :super-position       "quettabit"
                     :orbital-offset       "acute"
                     :super                42
                     :super-position-moons "up-and-down"})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ? and orbital_offset = ? and orbital_offset_looks = ?" "up-and-down" 42 "quettabit" "acute" "wobbly"]
             (sub q {:super                42
                     :super-position       "quettabit"
                     :orbital-offset       "acute"
                     :super-position-moons "up-and-down"
                     :orbital-offset-looks "wobbly"})))
      (is (= ["select * from planets where moons = ? and mass <= ? and name = ? and orbital_offset = ? and orbital_offset_looks = ?" "up-and-down" 42 "quettabit" "acute" "wobbly"]
             (sub q {:super                42
                     :orbital-offset       "acute"
                     :super-position       "quettabit"
                     :orbital-offset-looks "wobbly"
                     :super-position-moons "up-and-down"})))))

  (testing "should correctly sub seq parameters"
    (let [q   "insert into foo values :job-id, :my-awesome-values where id in (:ids) and other-id=:other-id"
          sub (fn [q m opts] (q/with-params q m opts))]
      (is (= ["insert into foo values ?, ?,?,?,?,?,?,?,?,?,? where id in (?,?,?) and other-id=?" 123 0 1 2 3 4 5 6 7 8 9 111 222 333 456]
             (sub q {:job-id            123
                     :other-id          456
                     :my-awesome-values (range 10)
                     :ids               [111 222 333]}
                  {:prepared? true
                   :seq-set #{:my-awesome-values :ids}})))))

  (testing "should correctly sub seq parameters"
    (let [q   "insert into foo values :my-awesome-values,:job-id where id in (:ids) and other-id = :other-id"
          sub (fn [q m opts] (q/with-params q m opts))]
      (is (= ["insert into foo values ?,?,?,?,?,?,?,?,?,?,? where id in (?,?,?) and other-id = ?" 0 1 2 3 4 5 6 7 8 9 123 111 222 333 456]
             (sub q {:job-id            123
                     :other-id          456
                     :my-awesome-values (range 10)
                     :ids               [111 222 333]}
                  {:prepared? true
                   :seq-set #{:my-awesome-values :ids}}))))))
