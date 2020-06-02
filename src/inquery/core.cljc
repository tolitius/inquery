(ns inquery.core
  #?(:clj (:require [clojure.java.io :as io]
                    [clojure.string :as s]
                    [inquery.pred :as pred])
     :cljs (:require [cljs.nodejs :as node]
                     [clojure.string :as s]
                     [inquery.pred :as pred])))

#?(:cljs
    (defn read-query [path qname]
      (let [fname (str path "/" qname ".sql")]
        (try
          (.toString
            (.readFileSync (node/require "fs") fname))
          (catch :default e
            (throw (js/Error. (str "can't find query file to load: \"" fname "\": " e))))))))

#?(:clj
    (defn read-query [path qname]
      (let [fname (str path "/" qname ".sql")
            sql (io/resource fname)]
        (if sql
          (slurp sql)
          (throw (RuntimeException. (str "can't find query file to load: \"" fname "\"")))))))

(defn make-query-map
  ([names]
   (make-query-map names {:path "sql"}))
  ([names {:keys [path]}]
   (into {}
         (for [qname names]
           (->> qname
                name
                (read-query path)
                (vector qname))))))

(defn escape-params [params mode]
  (let [esc (case mode
              :ansi #(str \" (s/replace % "\"" "\"\"") \")  ;;
              :mysql #(str \` (s/replace % "`" "``") \`)    ;; TODO: maybe later when returning a sqlvec
              :mssql #(str \[ (s/replace % "]" "]]") \])    ;;
              :don't identity
              #(str "'" (s/replace % "'" "''") "'"))]
    (into {} (for [[k v] params]
               [k (if (string? v)
                    (esc v)
                    (str v))]))))

(defn seq->in-params
  ;; convert seqs to IN params: i.e. [1 "2" 3] => "('1','2','3')"
  [xs]
  (as-> xs $
        (map #(str "'" % "'") $)
        (s/join "," $)
        (str "(" $ ")")))

(defn with-preds
  "* adds predicates to the query
   * if \"where\" needs to be prefixed add {:prefix \"where\"}
   * will remove any SQL ops that a predicate starts with in case it needs to go right after \"where\"
   * if none preds matched with \"where\" prefix the prefix won't be used

  => (q/with-preds \"select foo from bar where this = that\"
                    {#(= 42 42) \"and dog = :bow\"
                    #(= 2 5) \"and cat = :moo\"
                    #(= 28 28) \"or cow = :moo\"})

  => \"select foo from bar
       where this = that
       and dog = :bow
       or cow = :moo\"

  ;; or with \"where\":

  => (q/with-preds \"select foo from bar\"
                    {#(= 42 42) \"and dog = :bow\"
                    #(= 2 5) \"and cat = :moo\"
                    #(= 28 28) \"or cow = :moo\"}
                    {:prefix \"where\"})

  => \"select foo from bar
       where dog = :bow
             or cow = :moo\"
  "
  ([query pred-map]
   (with-preds query pred-map {}))
  ([query pred-map {:keys [prefix]}]
     (->> pred-map
       (filter (comp pred/check-pred first))
       vals
       (interpose " ")
       (apply str)
       (pred/with-prefix prefix)
       (str query " "))))

(defn with-params
  ([query params]
   (with-params query params {}))
  ([query params {:keys [esc]}]
   (if (seq query)
     (let [eparams (escape-params params esc)]
       (reduce-kv (fn [q k v]
                    (s/replace q (str k) v))
                  query eparams))
     (throw (ex-info "can't execute an empty query" {:params params})))))
