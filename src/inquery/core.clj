(ns inquery.core
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

(defn read-query [path qname]
  (let [fname (str path "/" qname ".sql")
        sql (io/resource fname)]
    (if sql
      (slurp sql)
      (throw (RuntimeException. (str "can't find query file to load: \"" fname "\""))))))

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

(defn with-params [query params]
  (reduce-kv (fn [q k v]
               (s/replace q (str k) (str v)))
             query params))
