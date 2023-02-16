 (ns inquery.test.core
  (:require [inquery.core :as q]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.test :refer :all]))

(deftest should-sub-params
  (testing "should sub params"
    (let [q    "select * from planets where mass <= :max-mass and name = :name"
          sub   (fn [q m] (-> q (q/with-params m)))]
      (is (= "select * from planets where mass <= 42 and name = 'mars'"   (sub q {:max-mass 42 :name "mars"})))
      (is (= "select * from planets where mass <= 42 and name = null"     (sub q {:max-mass 42 :name nil})))
      (is (= "select * from planets where mass <= null and name = ''"     (sub q {:max-mass nil :name ""})))
      (is (= "select * from planets where mass <= 42 and name = "         (sub q {:max-mass 42 :name {:as ""}})))
      (is (= "select * from planets where mass <= 42 and name = 42"       (sub q {:max-mass {:as 42} :name {:as "42"}})))
      (is (= "select * from planets where mass <=  and name = ''''''"     (sub q {:max-mass {:as nil} :name "''"})))
      (is (= "select * from planets where mass <= '' and name = ''''''"   (sub q {:max-mass {:as "''"} :name "''"})))
      (is (= "select * from planets where mass <=  and name = ''"         (-> q (q/with-params {:max-mass {:as nil} :name "''"}
                                                                                               {:esc :don't})))))))
(deftest should-sub-starts-with-params
  (testing "should correctly sub params that start with the same prefix"
    (let [q    "select * from planets where moons = :super-position-moons and mass <= :super and name = :super-position"
          sub   (fn [q m] (-> q (q/with-params m)))]
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit'"
             (sub q {:super 42
                     :super-position "quettabit"
                     :super-position-moons "up-and-down"})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit'"
             (sub q {:super-position "quettabit"
                     :super 42
                     :super-position-moons "up-and-down"})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit'"
             (sub q {:super-position "quettabit"
                     :super-position-moons "up-and-down"
                     :super 42})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit'"
             (sub q {:super-position-moons "up-and-down"
                     :super-position "quettabit"
                     :super 42})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit'"
             (sub q {:super-position-moons "up-and-down"
                     :super 42
                     :super-position "quettabit"})))))

  (testing "should correctly sub params that start with the same prefix, even when some of the keys have the same length"
    (let [q   "select * from planets where moons = :super-position-moons and mass <= :super and name = :super-position and orbital_offset = :orbital-offset and orbital_offset_looks = :orbital-offset-looks"
          sub (fn [q m] (-> q (q/with-params m)))]
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit' and orbital_offset = 'acute' and orbital_offset_looks = 'wobbly'"
             (sub q {:super-position-moons "up-and-down"
                     :orbital-offset-looks "wobbly"
                     :orbital-offset "acute"
                     :super-position "quettabit"
                     :super 42})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit' and orbital_offset = 'acute' and orbital_offset_looks = 'wobbly'"
             (sub q {:orbital-offset-looks "wobbly"
                     :super-position-moons "up-and-down"
                     :super-position "quettabit"
                     :orbital-offset "acute"
                     :super 42})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit' and orbital_offset = 'acute' and orbital_offset_looks = 'wobbly'"
             (sub q {:super 42
                     :super-position "quettabit"
                     :super-position-moons "up-and-down"
                     :orbital-offset "acute"
                     :orbital-offset-looks "wobbly"})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit' and orbital_offset = 'acute' and orbital_offset_looks = 'wobbly'"
             (sub q {:super-position "quettabit"
                     :super-position-moons "up-and-down"
                     :super 42
                     :orbital-offset "acute"
                     :orbital-offset-looks "wobbly"})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit' and orbital_offset = 'acute' and orbital_offset_looks = 'wobbly'"
             (sub q {:orbital-offset-looks "wobbly"
                     :super-position "quettabit"
                     :orbital-offset "acute"
                     :super 42
                     :super-position-moons "up-and-down"})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit' and orbital_offset = 'acute' and orbital_offset_looks = 'wobbly'"
             (sub q {:super 42
                     :super-position "quettabit"
                     :orbital-offset "acute"
                     :super-position-moons "up-and-down"
                     :orbital-offset-looks "wobbly"})))
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit' and orbital_offset = 'acute' and orbital_offset_looks = 'wobbly'"
             (sub q {:super 42
                     :orbital-offset "acute"
                     :super-position "quettabit"
                     :orbital-offset-looks "wobbly"
                     :super-position-moons "up-and-down"}))))))
