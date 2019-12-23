(ns inquery.core
  #?(:clj (:require [clojure.java.io :as io]
                    [clojure.string :as s])
     :cljs (:require [cljs.nodejs :as node]
                     [clojure.string :as s])))

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
