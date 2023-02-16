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
