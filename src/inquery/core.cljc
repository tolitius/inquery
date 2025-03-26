(ns inquery.core
  #?(:clj (:require [clojure.java.io :as io]
                    [clojure.string :as s]
                    [inquery.pred :as pred])
     :cljs (:require [cljs.nodejs :as node]
                     [clojure.string :as s]
                     [inquery.pred :as pred])))

(defprotocol SqlParam
  "safety first"
  (to-sql-string [this] "trusted type will be SQL'ized"))

(extend-protocol SqlParam
  nil
  (to-sql-string [_] "null")

  String
  (to-sql-string [s] (str "'" (s/replace s "'" "''") "'"))

  Number
  (to-sql-string [n] (str n))

  Boolean
  (to-sql-string [b] (str b))

  #?(:clj java.util.UUID :cljs cljs.core.UUID)
  (to-sql-string [u] (str "'" u "'"))

  #?(:clj java.time.Instant)
  #?(:clj (to-sql-string [i] (str "'" (.toString i) "'")))

  #?(:clj java.util.Date :cljs js/Date)
  (to-sql-string [d] (str "'" d "'"))

  clojure.lang.Keyword
  (to-sql-string [k] (str "'" (name k) "'"))

  #?(:clj clojure.lang.IPersistentCollection :cljs cljs.core.ICollection)
  (to-sql-string [coll]
    (if (seq coll)
      (str "("
           (->> coll
                (map (fn [v]
                       (if (nil? v)
                         "null"
                         (if (= v "")
                           "''"
                           (to-sql-string v)))))
                (s/join ","))
           ")")
      "(null)"))

  Object
  (to-sql-string [o]
    (throw (ex-info "not sure about safety of this type. if needed, implement the SqlParam protocol"
                    {:value o, :type (type o)}))))

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

(defn escape-params [params mode]
  (let [esc# (case mode
               :ansi #(str \" (s/replace % "\"" "\"\"") \")  ;;
               :mysql #(str \` (s/replace % "`" "``") \`)    ;; TODO: maybe later when returning a sqlvec
               :mssql #(str \[ (s/replace % "]" "]]") \])    ;;
               :don't identity
               identity)]
    (into {} (for [[k v] params]
               [k (cond
                    (treat-as? k v) (-> v :as str) ;; "no escape"
                    (= mode :don't) (str v)        ;; in :don't naughty mode, bypass the protocol
                    :else (if (= v "")             ;; empty string needs special treatment
                            "''"
                            (esc# (to-sql-string v))))]))))

(defn seq->batch-params
  "convert seq of seqs to updatable values (to use in SQL batch updates):

   => ;; xs
      [[#uuid 'c7a344f2-0243-4f92-8a96-bfc7ee482a9c'
        #uuid 'b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa']
       [#uuid '3236ebed-8248-4b07-a37e-c64c0a062247'
        #uuid 'b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa']]

   => (seq->batch-params xs)

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
       (map to-sql-string)
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











;; legacy corner

(defn- legacy-val->sql
  "legacy batch updates need special handling of numbers"
  [v]
  (cond
    (nil? v) "null"
    (= v "") "''"
    (number? v) (str "'" v "'")
    :else (to-sql-string v)))

(defn seq->in-params
  ;; convert seqs to IN params: i.e. [1 "2" 3] => "('1','2','3')"
  ;; no longer needed as it is handled by the SqlParam protocol
  ;; kept here because legacy relies on all values to be quoted: ('1','2','3'), even though some of them are numbers: [1 "2" 3]
  [xs]
  (as-> xs $
        (mapv legacy-val->sql $)
        (s/join "," $)
        (str "(" $ ")")))

(defn seq->update-vals
  ;; replace with seq->batch-params
  ;; kept here because legacy relies on all values to be quoted: ('1','2','3'), even though some of them are numbers: [1 "2" 3]
  [xs]
  "convert seq of seqs to updatable values (to use in SQL batch updates):

   => ;; xs
      [[#uuid 'c7a344f2-0243-4f92-8a96-bfc7ee482a9c'
        #uuid 'b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa']
       [#uuid '3236ebed-8248-4b07-a37e-c64c0a062247'
        #uuid 'b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa']]

   => (seq->update-vals xs)

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
