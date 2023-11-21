(ns inquery.prepared-statement
  (:require [clojure.string :as str]))

(defn tokenize [sql]
  (filter not-empty
          (->
            (str/trim sql)
            (clojure.string/split #"(?<=[\{\}\(\)\[\]\.,;\s+\*/&\|<>=~])|(?=[\{\}\(\)\[\]\.,;\s+\*/&\|<>=~])"))))

(defn seq->?
  ;; convert seqs to ?: i.e. [1 "2" 3] => "?,?,?"
  [xs]
  (as-> xs $
        (count $)
        (repeat $ "?")
        (str/join "," $)))

(defn- ->prepared-statement-vector [{:keys [sql params]}]
  (reduce (fn [acc {:keys [value is-seq?]}]
            (if is-seq?
              (vec (concat acc value))
              (conj acc value)))
          [sql]
          (sort-by :index params)))

(defn- with-params-reduce [input param-map {:keys [seq-set]}]
  (let [input-sql        (or (:sql input) input)
        tokens           (tokenize input-sql)
        str-keys-params  (update-keys param-map str)
        str-keys-seq-set (set (map str seq-set))]
    (reduce (fn [{:keys [sql params]} token]
              (let [has-param? (contains? str-keys-params token)
                    index      (count sql)
                    value      (str-keys-params token)
                    is-seq?    (boolean (str-keys-seq-set token))]
                (if has-param?
                  {:sql    (str sql (if is-seq?
                                      (seq->? value)
                                      "?"))
                   :params (conj params {:index index :value value :is-seq? is-seq?})}
                  {:sql    (str sql token)
                   :params params})))
            {:sql    ""
             :params (or (:params input) [])}
            tokens)))

(defn with-params
  ([input param-map opts]
   (-> (with-params-reduce input param-map opts)
       ->prepared-statement-vector)))






