# 0.1.20

* add support for #inst and #uuid literals (thanks to [@arichiardi](https://github.com/arichiardi))

# 0.1.19

* add `esc` function

# 0.1.17

* have "`seq->update-vals`" return 'null's for nils

```clojure
(deftest should-sub-batch-upserts
  (testing "should correctly sub params for values in batch upserts"
    (let [q    "insert into planets (\"id\", \"system\", \"planet\") values :planets"
          sub   (fn [q vs] (-> q (q/with-params {:planets {:as (q/seq->update-vals vs)}})))]
      (is (= (str "insert into planets (\"id\", \"system\", \"planet\") "
                  "values "
                  "('42','solar','earth'),"
                  "('34',null,'saturn'),"       ;; <<<<<< before the null here would be ''
                  "('28','','pluto')")
             (sub q [[42 "solar" "earth"]
                     [34 nil "saturn"]
                     [28 "" "pluto"]]))))))
```

# 0.1.16

* fix: same prefix params

```clojure
(deftest should-sub-starts-with-params
  (testing "should correctly sub params that start with the same prefix"
    (let [q    "select * from planets where moons = :super-position-moons and mass <= :super and name = :super-position"
          sub   (fn [q m] (-> q (q/with-params m)))]
      (is (= "select * from planets where moons = 'up-and-down' and mass <= 42 and name = 'quettabit'"
             (sub q {:super 42
                     :super-position "quettabit"
                     :super-position-moons "up-and-down"}))))))
```
