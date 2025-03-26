 (ns inquery.test.core
  (:require [inquery.core :as q]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.test :refer :all])
  (:import java.time.Instant
           java.util.Date
           java.util.UUID))

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
                                                                                  {:esc :don't})))))
    (testing "param is #uuid"
        (let [q  "select * from planets where id = :planet-id"
              sub   (fn [q m] (-> q (q/with-params m)))
              planet-id (random-uuid)]
          (is (= (str "select * from planets where id = '" planet-id "'")
                 (sub q {:planet-id planet-id})))))

    (testing "param is #time/instant"
        (let [q  "select * from planets where id = :imploded-at"
              sub   (fn [q m] (-> q (q/with-params m)))
              imploded-at (Instant/now)]
          (is (= (str "select * from planets where id = '" (.toString imploded-at) "'")
                 (sub q {:imploded-at imploded-at})))))))

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

(deftest should-sub-legacy-batch-upserts
  (testing "should correctly sub params for values in legacy batch upserts"
    (let [q    "insert into planets (\"id\", \"system\", \"planet\") values :planets"
          sub   (fn [q vs] (-> q (q/with-params {:planets {:as (q/seq->update-vals vs)}})))]
      (is (= (str "insert into planets (\"id\", \"system\", \"planet\") "
                  "values "
                  "('42','solar','earth'),"
                  "('34',null,'saturn'),"
                  "('28','','pluto')")
             (sub q [[42 "solar" "earth"]
                     [34 nil "saturn"]
                     [28 "" "pluto"]]))))))

(deftest should-sub-batch-upserts
  (testing "should correctly sub params for values in batch upserts"
    (let [q    "insert into planets (\"id\", \"system\", \"planet\") values :planets"
          sub   (fn [q vs] (-> q (q/with-params {:planets {:as (q/seq->batch-params vs)}})))]
      (is (= (str "insert into planets (\"id\", \"system\", \"planet\") "
                  "values "
                  "(42,'solar','earth'),"
                  "('34',null,'saturn'),"
                  "(28,'','pluto')")
             (sub q [[42 "solar" "earth"]
                     ["34" nil "saturn"]
                     [28 "" "pluto"]]))))))

(deftest should-handle-batch-upserts-with-complex-types
  (testing "should correctly handle batch upserts with complex types"
    (let [uuid1 (UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
          uuid2 (UUID/fromString "bdd21ce1-24d5-4180-9307-7f560bb0e9e3")
          timestamp (Instant/parse "2023-01-15T12:34:56Z")
          q "INSERT INTO celestial_bodies (id, type, name, discovered_at) VALUES :bodies"]

      (is (= (str "INSERT INTO celestial_bodies (id, type, name, discovered_at) VALUES "
                  "('f81d4fae-7dec-11d0-a765-00a0c91e6bf6','planet','earth','2023-01-15T12:34:56Z'),"
                  "('bdd21ce1-24d5-4180-9307-7f560bb0e9e3','planet','mars',null)")
             (q/with-params q {:bodies {:as (q/seq->batch-params [[uuid1 "planet" "earth" timestamp]
                                                                  [uuid2 "planet" "mars" nil]])}}))))))
(deftest should-format-legacy-batch-values-correctly
  (testing "should format legacy batch values correctly with seq->update-vals"
    (are [input expected] (= expected (q/seq->update-vals input))
      [[1 "earth" 5973.6]]                                        "('1','earth','5973.6')"

      [[1 "earth" 5973.6] [2 "mars" 641.85]]                      "('1','earth','5973.6'),('2','mars','641.85')"

      [[1 nil "earth"] [2 "" "mars"] [3 "venus" 4868.5]]          "('1',null,'earth'),('2','','mars'),('3','venus','4868.5')"

      [[(UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6") "neptune"]]
                                                                  "('f81d4fae-7dec-11d0-a765-00a0c91e6bf6','neptune')")))

(deftest should-format-batch-values-correctly
  (testing "should format batch values correctly with seq->update-vals"
    (are [input expected] (= expected (q/seq->batch-params input))
      [[1 "earth" 5973.6]]                                        "(1,'earth',5973.6)"

      [[1 "earth" 5973.6] [2 "mars" 641.85]]                      "(1,'earth',5973.6),(2,'mars',641.85)"

      [[1 nil "earth"] [2 "" "mars"] [3 "venus" 4868.5]]          "(1,null,'earth'),(2,'','mars'),(3,'venus',4868.5)"

      [[(UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6") "neptune"]]
                                                                  "('f81d4fae-7dec-11d0-a765-00a0c91e6bf6','neptune')")))
(deftest should-properly-escape-basic-types
  (testing "should properly escape basic types"
    (are [input expected] (= expected (q/to-sql-string input))
      nil                                                         "null"
      42                                                          "42"
      42.5                                                        "42.5"
      "earth"                                                     "'earth'"
      "mars'"                                                     "'mars'''"
      ""                                                          "''"
      true                                                        "true"
      false                                                       "false"
      :saturn                                                     "'saturn'"
      (UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")   "'f81d4fae-7dec-11d0-a765-00a0c91e6bf6'"
      (Instant/parse "2023-01-15T12:34:56Z")                     "'2023-01-15T12:34:56Z'")))

(deftest should-handle-date-types
  (testing "should properly handle date types"
    (let [date (doto (Date.) (.setTime 1673789696000))]
      (is (re-find #"^'.*'$" (q/to-sql-string date))))))

(deftest should-format-collections-for-in-clauses
  (testing "should properly format collections for IN clauses"
    (are [input expected] (= expected (q/to-sql-string input))
      []                                                          "(null)"
      [1 2 3]                                                     "(1,2,3)"
      ["earth" "mars"]                                            "('earth','mars')"
      ["earth" nil "mars"]                                        "('earth',null,'mars')"
      ["earth" "" "mars"]                                         "('earth','','mars')"
      [1 "mars" nil]                                              "(1,'mars',null)"
      [:earth :mars]                                              "('earth','mars')")))

(deftype SqlInjection []
  Object
  (toString [_] "'; DROP TABLE planets; --"))

(deftest should-reject-malicious-objects
  (testing "should reject malicious objects"

    (is (thrown? Exception (q/to-sql-string (SqlInjection.))))
    (is (thrown? Exception (q/with-params "SELECT * FROM planets WHERE name = :name"
                                          {:name (SqlInjection.)})))))

(deftest should-substitute-various-parameter-types
  (testing "should properly substitute various parameter types in sql queries"
    (let [query "SELECT * FROM planets WHERE mass <= :mass AND name = :name AND active = :active AND id = :id AND created_at <= :time"]
      (are [params expected] (= expected (q/with-params query params))
        {:mass 5973.6
         :name "earth"
         :active true
         :id (UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
         :time (Instant/parse "2023-01-15T12:34:56Z")}
        "SELECT * FROM planets WHERE mass <= 5973.6 AND name = 'earth' AND active = true AND id = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6' AND created_at <= '2023-01-15T12:34:56Z'"

        {:mass nil
         :name ""
         :active false
         :id nil
         :time nil}
        "SELECT * FROM planets WHERE mass <= null AND name = '' AND active = false AND id = null AND created_at <= null"))))

(deftest should-substitute-parameters-with-shared-prefixes-in-correct-order
  (testing "should substitute parameters with shared prefixes in correct order"
    (let [query "INSERT INTO galaxies VALUES (:galaxy-id, :galaxy, :galaxy-type, :galaxy-id-alt)"]
      (is (= "INSERT INTO galaxies VALUES ('milky-way-42', 'milky way', 'spiral', 'alt-42')"
             (q/with-params query {:galaxy "milky way"
                                   :galaxy-id "milky-way-42"
                                   :galaxy-type "spiral"
                                   :galaxy-id-alt "alt-42"}))))))

(deftest should-handle-as-option-without-escaping
  (testing "should insert parameters with :as option without escaping"
    (let [query "SELECT * FROM planets WHERE mass IN :masses AND name LIKE :pattern"]
      (is (= "SELECT * FROM planets WHERE mass IN (1, 2, 3) AND name LIKE '%ar%'"
             (q/with-params query {:masses {:as "(1, 2, 3)"}
                                   :pattern {:as "'%ar%'"}})))

      (is (= "SELECT * FROM planets WHERE mass IN NULL AND name LIKE NULL"
             (q/with-params query {:masses {:as "NULL"}
                                   :pattern {:as "NULL"}}))))))

(deftest should-prevent-sql-injection-attempts
  (testing "should properly escape sql injection attempts"
    (let [query "SELECT * FROM planets WHERE name = :name"]
      (is (= "SELECT * FROM planets WHERE name = 'earth'' OR ''1''=''1'"
             (q/with-params query {:name "earth' OR '1'='1"})))

      (is (= "SELECT * FROM planets WHERE name = 'mars''; DROP TABLE planets; --'"
             (q/with-params query {:name "mars'; DROP TABLE planets; --"}))))))

(deftest should-throw-exception-for-empty-query
  (testing "should throw an exception for empty query"
    (is (thrown? Exception (q/with-params "" {:name "mars"})))))

(deftest should-substitute-various-parameter-types
  (testing "should properly substitute various parameter types in sql queries"
    (let [query "SELECT * FROM planets WHERE mass <= :mass AND name = :name AND active = :active AND id = :id AND created_at <= :time"]
      (are [params expected] (= expected (q/with-params query params))
        {:mass 5973.6
         :name "earth"
         :active true
         :id (UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
         :time (Instant/parse "2023-01-15T12:34:56Z")}
        "SELECT * FROM planets WHERE mass <= 5973.6 AND name = 'earth' AND active = true AND id = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6' AND created_at <= '2023-01-15T12:34:56Z'"

        {:mass nil
         :name ""
         :active false
         :id nil
         :time nil}
        "SELECT * FROM planets WHERE mass <= null AND name = '' AND active = false AND id = null AND created_at <= null"))))

(deftest should-build-queries-with-collection-parameters
  (testing "should properly build queries with collection parameters"
    (let [query "SELECT * FROM planets WHERE id IN :ids AND name IN :names"]
      (are [params expected] (= expected (q/with-params query params))
        {:ids [1 2 3]
         :names ["earth" "mars" "venus"]}
        "SELECT * FROM planets WHERE id IN (1,2,3) AND name IN ('earth','mars','venus')"

        {:ids []
         :names []}
        "SELECT * FROM planets WHERE id IN (null) AND name IN (null)"

        {:ids [1 2 nil 3]
         :names ["earth" nil "mars"]}
        "SELECT * FROM planets WHERE id IN (1,2,null,3) AND name IN ('earth',null,'mars')"))))

(deftest should-build-queries-with-mixed-collection-types
  (testing "should properly build queries with mixed collection types"
    (let [query "SELECT * FROM celestial_objects WHERE type IN :types AND id IN :ids AND discovery_date IN :dates"]
      (is (= (str "SELECT * FROM celestial_objects WHERE type IN ('planet','moon','asteroid') AND "
                 "id IN ('f81d4fae-7dec-11d0-a765-00a0c91e6bf6','bdd21ce1-24d5-4180-9307-7f560bb0e9e3') AND "
                 "discovery_date IN ('2023-01-15T12:34:56Z','2023-02-20T15:30:00Z')")
             (q/with-params query
                           {:types ["planet" "moon" "asteroid"]
                            :ids [(UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
                                  (UUID/fromString "bdd21ce1-24d5-4180-9307-7f560bb0e9e3")]
                            :dates [(Instant/parse "2023-01-15T12:34:56Z")
                                    (Instant/parse "2023-02-20T15:30:00Z")]}))))))

(deftest should-build-complex-queries-with-mixed-parameters
  (testing "should build complex queries with mixed scalar and collection parameters"
    (let [query (str "SELECT * FROM observations "
                     "WHERE planet_id = :planet_id "
                     "AND observer_id IN :observer_ids "
                     "AND observation_type IN :types "
                     "AND observation_date BETWEEN :start_date AND :end_date "
                     "AND confidence > :min_confidence")]
      (is (= (str "SELECT * FROM observations "
                  "WHERE planet_id = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6' "
                  "AND observer_id IN (1,2,3) "
                  "AND observation_type IN ('visual','spectral','radio') "
                  "AND observation_date BETWEEN '2023-01-15T12:34:56Z' AND '2023-02-20T15:30:00Z' "
                  "AND confidence > 0.75")
             (q/with-params query
                           {:planet_id (UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
                            :observer_ids [1 2 3]
                            :types ["visual" "spectral" "radio"]
                            :start_date (Instant/parse "2023-01-15T12:34:56Z")
                            :end_date (Instant/parse "2023-02-20T15:30:00Z")
                            :min_confidence 0.75}))))))
