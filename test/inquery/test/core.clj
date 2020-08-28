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
