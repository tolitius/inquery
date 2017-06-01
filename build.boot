(def +version+ "0.1.0")

(set-env!
  :source-paths #{"src"}
  :dependencies '[;; this lib brings no deps

                  ;; boot clj
                  [boot/core                "2.7.1"           :scope "provided"]
                  [adzerk/bootlaces         "0.1.13"          :scope "test"]
                  [adzerk/boot-test         "1.0.6"           :scope "test"]
                  [tolitius/boot-check      "0.1.4"           :scope "test"]
                  
                  ;; scratchpad
                  [funcool/clojure.jdbc     "0.9.0"           :scope "test"]
                  [com.h2database/h2        "1.4.195"         :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[tolitius.boot-check :as check]
         '[adzerk.boot-test :as bt])

(bootlaces! +version+)

(defn uber-env []
  (set-env! :source-paths #(conj % "dev"))
  (set-env! :resource-paths #(conj % "dev-resources")))

(deftask dev []
  (uber-env)
  ;; (require '[scratchpad :as sp :refer [dbspec queries]])
  (repl))

(deftask test []
  (uber-env)
  (bt/test))

(deftask check-sources []
  (comp
    (check/with-bikeshed)
    (check/with-eastwood)
    (check/with-yagni)
    (check/with-kibit)))

(task-options!
  push {:ensure-branch nil}
  pom {:project     'tolitius/inquery
       :version     +version+
       :description "see qua l"
       :url         "https://github.com/tolitius/inquery"
       :scm         {:url "https://github.com/tolitius/inquery"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})
