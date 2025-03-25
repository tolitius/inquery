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

(defn treat-as? [k v]
  (if (map? v)
    (if (contains? v :as)  ;; TODO: later if more opts check validate all: (#{:as :foo :bar} op)
      v
      (throw (ex-info "invalid query substitution option. supported options are: #{:as}"
                      {:key k :value v})))))

(defn esc
  ;; TODO: add other data types if/when needed
  [v]
  (cond (string? v) (s/replace v "'" "''")
        :else       v))

(defn escape-params [params mode]
  (let [esc# (case mode
               :ansi #(str \" (s/replace % "\"" "\"\"") \")  ;;
               :mysql #(str \` (s/replace % "`" "``") \`)    ;; TODO: maybe later when returning a sqlvec
               :mssql #(str \[ (s/replace % "]" "]]") \])    ;;
               :don't identity
               #(str "'" (esc %) "'"))]
    (into {} (for [[k v] params]
               [k (cond
                    (treat-as? k v) (-> v :as str) ;; "no escape"
                    (= v "") "''"
                    (or (string? v)
                        (uuid? v)
                        (inst? v)) (esc# v)
                    (nil? v) "null"
                    :else (str v))]))))

(defn fnull
  "nil to null
   otherwise SQL string the param

  => (fnull 42)
     \"'42'\"

  => (fnull nil)
     \"null\"

  => (fnull \"\")
     \"''\"
  "
  [param]
  (if (nil? param)
    "null"
    (str "'" param "'")))

(defn seq->in-params
  ;; convert seqs to IN params: i.e. [1 "2" 3] => "('1','2','3')"
  [xs]
  (as-> xs $
        (mapv fnull $)
        (s/join "," $)
        (str "(" $ ")")))

(defn seq->update-vals
  "convert seq of seqs to updatable values (to use in SQL batch updates):

   => ;; xs
      [[#uuid 'c7a344f2-0243-4f92-8a96-bfc7ee482a9c'
        #uuid 'b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa']
       [#uuid '3236ebed-8248-4b07-a37e-c64c0a062247'
        #uuid 'b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa']]

   => (sseq->values xs)

      ('c7a344f2-0243-4f92-8a96-bfc7ee482a9c','b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa'),
      ('3236ebed-8248-4b07-a37e-c64c0a062247','b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa')

  to be able to plug them into something like:

      update test as t set
        column_a = c.column_a
      from (values
          ('123', 1),                << here
          ('345', 2)                 << is a batch of values
      ) as c(column_b, column_a)
      where c.column_b = t.column_b;
  "
  [xs]
  (->> xs
       (map seq->in-params)
       (interpose ",")
       (apply str)))

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

(defn- compare-key-length [k1 k2]
  (let [length #(-> % str count)]
    (compare [(length k2) k2]
             [(length k1) k1])))

(defn with-params
  ([query params]
   (with-params query params {}))
  ([query params {:keys [esc]}]
   (if (seq query)
     (let [eparams (->> (escape-params params esc)
                        (into (sorted-map-by compare-key-length)))]
       (reduce-kv (fn [q k v]
                    (s/replace q (str k) v))
                  query eparams))
     (throw (ex-info "can't execute an empty query" {:params params})))))
