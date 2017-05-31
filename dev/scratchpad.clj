(ns scratchpad
  (:require [inquery.core :as q]
            [jdbc.core :as jdbc]))

(def dbspec
  {:subprotocol "h2"                           ;; dbspec would usually come from config.end / consul / etc..
   :subname "file:/tmp/solar"})

(def queries
  (q/make-query-map #{:create-planets          ;; set of queries would usually come from config.edn / consul / etc..
                      :find-planets
                      :find-planets-by-mass}))

;; sample functions using "funcool/clojure.jdbc"

(defn with-db [db-spec f]
   (with-open [conn (jdbc/connection db-spec)]
     (f conn)))

(defn execute [db-spec query]
  (with-db db-spec
    (fn [conn] (jdbc/execute conn query))))

(defn fetch
  ([db-spec query]
   (fetch db-spec query {}))
  ([db-spec query params]
   (with-db db-spec
     (fn [conn] (jdbc/fetch conn
                            (q/with-params query params))))))
