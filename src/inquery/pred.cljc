(ns inquery.pred
  #?(:clj (:require [clojure.string :as s])
     :cljs (:require [clojure.string :as s])))

(defn check-pred [pred]
  (if (fn? pred)
    (.invoke pred)
    (throw (ex-info "predicate should be a function" {:instead-got pred}))))

(defn value? [v]
  (or (number? v)
      (seq v)))

(defn- remove-start-op
  [q op]
  (if (and (value? q)
           (value? op))
    (when (s/starts-with? (s/upper-case q)
                        (s/upper-case op))
      (subs q (count op)))
    q))

(defn remove-start-ops [qpart]
  "removes an op from part of the query that starts with it (i.e. AND, OR, etc.)

   'and dog = :bow' => ' dog = :bow'
   'or dog = :bow'  => ' dog = :bow'
   'xor dog = :bow' => 'xor dog = :bow'  ;; xor is not a SQL op
  "
  (let [ops #{"or" "and"}]                ;; TODO: add more when/iff needed
    (or (some #(remove-start-op qpart %) ops)
        qpart)))

(defn with-prefix [prefix qpart]
  (case (s/lower-case prefix)
    "where" (if (seq qpart)
              (str prefix " " (remove-start-ops qpart))
              qpart)
    (str prefix " " qpart)))
