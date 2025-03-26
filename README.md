# inquery

vanilla SQL with params for Clojure/Script

* no DSL
* no comments parsing
* no namespace creations
* no defs / defqueries
* no dependencies
* no edn SQL

just "read SQL with `:params`"

[![Clojars Project](https://clojars.org/tolitius/inquery/latest-version.svg)](http://clojars.org/tolitius/inquery)

- [why](#why)
- [using inquery](#using-inquery)
  - [escaping](#escaping)
  - [dynamic queries](#dynamic-queries)
- [type safety](#type-safety)
- [batch upserts](#batch-upserts)
- [ClojureScript](#clojurescript)
- [scratchpad](#scratchpad)
- [license](#license)

## why

SQL is a great language, it is very expressive and exremely well optimized and supported by "SQL" databases.
it needs no wrappers. it should live in its pure SQL form.

`inquery` does two things:

* reads SQL files
* substitutes params at runtime

Clojure APIs cover all the rest

## using inquery

`inquery` is about SQL: it _does not_ require or force a particular JDBC library or a database.

But to demo an actual database conversation, this example will use "[funcool/clojure.jdbc](http://funcool.github.io/clojure.jdbc/latest/)" to speak to
a sample [H2](http://www.h2database.com/html/main.html) database since both of them are great.

There is nothing really to do other than to bring the queries into a map with a `make-query-map` function:

```clojure
$ make repl

=> (require '[inquery.core :as q]
            '[jdbc.core :as jdbc])
```

`dbspec` along with a `set of queries` would usually come from `config.edn` / consul / etc :

```clojure
=> (def dbspec {:subprotocol "h2"
                :subname "file:/tmp/solar"})

=> (def queries (q/make-query-map #{:create-planets
                                    :find-planets
                                    :find-planets-by-mass
                                    :find-planets-by-name}))
```

`inquiry` by default will look under `sql/*` path for queries. In this case "[dev-resources](dev-resources)" is in a classpath:

```
▾ dev-resources/sql/
      create-planets.sql
      find-planets-by-mass.sql
      find-planets-by-name.sql
      find-planets.sql
```

Ready to roll, let's create some planets:

```clojure
=> (with-open [conn (jdbc/connection dbspec)]
     (jdbc/execute conn (:create-planets queries)))
```

check out the solar system:

```clojure
=> (with-open [conn (jdbc/connection dbspec)]
     (jdbc/fetch conn (:find-planets queries)))

[{:id 1, :name "Mercury", :mass 330.2M}
 {:id 2, :name "Venus", :mass 4868.5M}
 {:id 3, :name "Earth", :mass 5973.6M}
 {:id 4, :name "Mars", :mass 641.85M}
 {:id 5, :name "Jupiter", :mass 1898600M}
 {:id 6, :name "Saturn", :mass 568460M}
 {:id 7, :name "Uranus", :mass 86832M}
 {:id 8, :name "Neptune", :mass 102430M}
 {:id 9, :name "Pluto", :mass 13.105M}]
```

find all the planets with mass less or equal to the mass of Earth:

```clojure
=> (with-open [conn (jdbc/connection dbspec)]
     (jdbc/fetch conn (-> (:find-planets-by-mass queries)
                          (q/with-params {:max-mass 5973.6}))))

[{:id 1, :name "Mercury", :mass 330.2M}
 {:id 2, :name "Venus", :mass 4868.5M}
 {:id 3, :name "Earth", :mass 5973.6M}
 {:id 4, :name "Mars", :mass 641.85M}
 {:id 9, :name "Pluto", :mass 13.105M}]
```

which planet is the most `art`sy:

```clojure
=> (with-open [conn (jdbc/connection dbspec)]
     (jdbc/fetch conn (-> (:find-planets-by-name queries)
                      (q/with-params {:name "%art%"}))))

[{:id 3, :name "Earth", :mass 5973.6M}]
```

### escaping

by default inquery will "SQL escape" all the parameters that need to be substituted in a query.

in case you need to _not_ escape the params inquery has options to not escape the whole query with `{:esc :don't}`:

```clojure
=> (with-open [conn (jdbc/connection dbspec)]
     (jdbc/fetch conn (-> (:find-planets-by-name queries)
                      (q/with-params {:name "%art%"}
                                     {:esc :don't}))))

```

or per individual parameter with `{:as val}`:

```clojure
=> (with-open [conn (jdbc/connection dbspec)]
     (jdbc/fetch conn (-> (:find-planets-by-name queries)
                      (q/with-params {:name {:as ""}
                                      :mass 42}))))
```

#### things to note about escaping

`nil`s are converted to "null":

```clojure
=> (-> "name = :name" (q/with-params {:name nil}))
"name = null"
```

`{:as nil}` or `{:as ""}` are "as is", so it will be replaced with an empty string:

```clojure
=> (-> "name = :name" (q/with-params {:name {:as nil}}))
"name = "

=> (-> "name = :name" (q/with-params {:name {:as ""}}))
"name = "
```

`""` will become a "SQL empty string":

```clojure
=> (-> "name = :name" (q/with-params {:name ""}))
"name = ''"
```

see [tests](test/inquery/test/core.clj) for more examples.

### dynamic queries

inquery can help out with some runtime decision making to build SQL predicates.

`with-preds` function takes a map of `{pred-fn sql-predicate}`.<br/>
for each "true" predicate function its `sql-predicate` will be added to the query:

```clojure
=> (q/with-preds "select planet from solar_system where this = that"
                 {#(= 42 42) "and type = :type"})

"select planet from solar_system where this = that and type = :type"
```

```clojure
=> (q/with-preds "select planet from solar_system where this = that"
                 {#(= 42 42) "and type = :type"
                  #(= 28 34) "and size < :max-size"})

"select planet from solar_system where this = that and type = :type"
```

if both predicates are true, both will be added:

```clojure
=> (q/with-preds "select planet from solar_system where this = that"
                  {#(= 42 42) "and type = :type"
                   #(= 28 28) "and size < :max-size"})

"select planet from solar_system where this = that and type = :type and size < :max-size"
```

some queries don't come with `where` clause, for these cases `with-preds` takes a prefix:

```clojure
=> (q/with-preds "select planet from solar_system"
                  {#(= 42 42) "and type = :type"
                   #(= 28 34) "and size < :max-size"}
                  {:prefix "where"})

"select planet from solar_system where type = :type"
```

developer will know the (first part of the) query, so this decision is not "hardcoded".

```clojure
=> (q/with-preds "select planet from solar_system"
                  {#(= 42 42) "and type = :type"
                   #(= 34 34) "and size < :max-size"}
                  {:prefix "where"})

"select planet from solar_system where type = :type and size < :max-size"
```

in case none of the predicates are true, `"where"` prefix won't be used:

```clojure
=> (q/with-preds "select planet from solar_system"
                  {#(= 42 -42) "and type = :type"
                   #(= 34 28) "and size < :max-size"}
                   {:prefix "where"})

"select planet from solar_system"
```

## type safety

### sql parameters

inquery uses a type protocol `SqlParam` to safely convert clojure/script values to sql strings:

```clojure
(defprotocol SqlParam
  "safety first"
  (to-sql-string [this] "trusted type will be SQL'ized"))
```

it:

* prevents sql injection
* properly handles various data types
* is extensible for custom types

common types are handled out of the box:

```clojure
(q/to-sql-string nil)                                        ;; => "null"
(q/to-sql-string "earth")                                    ;; => "'earth'"
(q/to-sql-string "pluto's moon")                             ;; => "'pluto''s moon'"  ;; note proper escaping
(q/to-sql-string 42)                                         ;; => "42"
(q/to-sql-string true)                                       ;; => "true"
(q/to-sql-string :jupiter)                                   ;; => "'jupiter'"
(q/to-sql-string [1 2 nil "mars"])                           ;; => "(1,2,null,'mars')"
(q/to-sql-string #uuid "f81d4fae-7dec-11d0-a765-00a0c91e6bf6") ;; => "'f81d4fae-7dec-11d0-a765-00a0c91e6bf6'"
(q/to-sql-string #inst "2023-01-15T12:34:56Z")               ;; => "'2023-01-15T12:34:56Z'"
(q/to-sql-string (java.util.Date.))                          ;; => "'Wed Mar 26 09:42:17 EDT 2025'"
```

### custom types

you can extend `SqlParam` protocol to handle custom types:

```clojure
(defrecord Planet [name mass])

(extend-protocol inquery.core/SqlParam
  Planet
  (to-sql-string [planet]
    (str "'" (:name planet) " (" (:mass planet) " x 10^24 kg)'")))

(q/to-sql-string (->Planet "neptune" 102)) ;; => "'neptune (102 x 10^24 kg)'"
```

### its built in

no need to call "`to-sql-string`" of course, inquery does it internally:

```clojure
;; find planets discovered during specific time range with certain composition types
(let [query "SELECT * FROM planets
             WHERE discovery_date BETWEEN :start_date AND :end_date
             AND name NOT IN :excluded_planets
             AND composition_type IN :allowed_types
             AND is_habitable = :habitable
             AND discoverer_id = :discoverer"
      params {:start_date (Instant/parse "2020-01-01T00:00:00Z")
              :end_date (java.util.Date.)
              :excluded_planets ["mercury" "venus" "earth"]
              :allowed_types [:rocky :gas-giant :ice-giant]
              :habitable true
              :discoverer (UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")}]

  (q/with-params query params))
```
```clojure
;; => "SELECT * FROM planets
;;     WHERE discovery_date BETWEEN '2020-01-01T00:00:00Z' AND 'Wed Mar 26 09:48:32 EDT 2025'
;;     AND name NOT IN ('mercury','venus','earth')
;;     AND composition_type IN ('rocky','gas-giant','ice-giant')
;;     AND is_habitable = true
;;     AND discoverer_id = 'f81d4fae-7dec-11d0-a765-00a0c91e6bf6'"
```

## batch upserts

inquery provides functions to safely convert collections for batch operations:

* `seq->batch-params` - converts a sequence of sequences to a string suitable for batch inserts/updates
* `seq->update-vals` - legacy version that quotes all values (even numbers)

```clojure
;; using seq->batch-params for modern batch operations
;; (perfect for cataloging newly discovered exoplanets)
(q/seq->batch-params [[42 "earth" 5973.6]
                      ["34" nil "saturn"]])
;; => "(42,'earth',5973.6),('34',null,'saturn')"

;; safe handling of UUIDs, timestamps, and other complex types
;; (for when you need to record celestial events)
(let [uuid1 (UUID/fromString "f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
      timestamp (Instant/parse "2023-01-15T12:34:56Z")]
  (q/seq->batch-params [[uuid1 "planet" "earth" timestamp]]))
;; => "('f81d4fae-7dec-11d0-a765-00a0c91e6bf6','planet','earth','2023-01-15T12:34:56Z')"
```

and the real SQL example:

```clojure
;; batch insert new celestial bodies with mixed data types
(let [query "INSERT INTO celestial_bodies
              (id, name, type, mass, discovery_date, is_confirmed)
             VALUES :bodies"

      ;; collection of [id, name, type, mass, date, confirmed?]
      bodies [[#uuid "c7a344f2-0243-4f92-8a96-bfc7ee482a9c"
               "kepler-186f"
               :exoplanet
               4.7
               #inst "2014-04-17T00:00:00Z"
               true]

              [#uuid "3236ebed-8248-4b07-a37e-c64c0a062247"
               "toi-700d"
               :exoplanet
               1.72
               #inst "2020-01-07T00:00:00Z"
               true]

              [#uuid "b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa"
               "proxima centauri b"
               :exoplanet
               1.27
               #inst "2016-08-24T00:00:00Z"
               nil]]]

  (q/with-params query {:bodies {:as (q/seq->batch-params bodies)}}))
```
```clojure
;; => "INSERT INTO celestial_bodies
;;       (id, name, type, mass, discovery_date, is_confirmed)
;;     VALUES
;;       ('c7a344f2-0243-4f92-8a96-bfc7ee482a9c','kepler-186f','exoplanet',4.7,'2014-04-17T00:00:00Z',true),
;;       ('3236ebed-8248-4b07-a37e-c64c0a062247','toi-700d','exoplanet',1.72,'2020-01-07T00:00:00Z',true),
;;       ('b29bc806-7db1-4e0c-93f7-fe5ee38ad1fa','proxima centauri b','exoplanet',1.27,'2016-08-24T00:00:00Z',null)"
```

## ClojureScript

```clojure
$ lumo -i src/inquery/core.cljc --repl
Lumo 1.2.0
ClojureScript 1.9.482
 Docs: (doc function-name-here)
 Exit: Control+D or :cljs/quit or exit

cljs.user=> (ns inquery.core)
```

depending on how a resource path is setup, an optional parameter `{:path "..."}`
could help to specify the path to queries:

```clojure
inquery.core=> (def queries
                 (make-query-map #{:create-planets
                                   :find-planets
                                   :find-planets-by-mass}
                                 {:path "dev-resources/sql"}))
#'inquery.core/queries
```

```clojure
inquery.core=> (print queries)

{:create-planets -- create planets
drop table if exists planets;
create table planets (id bigint auto_increment, name varchar, mass decimal);

insert into planets (name, mass) values ('Mercury', 330.2),
                                        ('Venus', 4868.5),
                                        ('Earth', 5973.6),
                                        ('Mars', 641.85),
                                        ('Jupiter', 1898600),
                                        ('Saturn', 568460),
                                        ('Uranus', 86832),
                                        ('Neptune', 102430),
                                        ('Pluto', 13.105);
, :find-planets -- find all planets
select * from planets;
, :find-planets-by-mass -- find planets under a certain mass
select * from planets where mass <= :max-mass
}
```

```clojure
inquery.core=> (-> queries
                   :find-planets-by-mass
                   (with-params {:max-mass 5973.6}))

-- find planets under a certain mass
select * from planets where mass <= 5973.6
```

## scratchpad

development [scratchpad](dev/scratchpad.clj) with sample shortcuts:

```clojure
$ make repl

=> (require '[scratchpad :as sp :refer [dbspec queries]])

=> (sp/execute dbspec (:create-planets queries))

=> (sp/fetch dbspec (:find-planets queries))

[{:id 1, :name "Mercury", :mass 330.2M}
 {:id 2, :name "Venus", :mass 4868.5M}
 {:id 3, :name "Earth", :mass 5973.6M}
 {:id 4, :name "Mars", :mass 641.85M}
 {:id 5, :name "Jupiter", :mass 1898600M}
 {:id 6, :name "Saturn", :mass 568460M}
 {:id 7, :name "Uranus", :mass 86832M}
 {:id 8, :name "Neptune", :mass 102430M}
 {:id 9, :name "Pluto", :mass 13.105M}]

=> (sp/fetch dbspec (:find-planets-by-mass queries) {:max-mass 5973.6})

[{:id 1, :name "Mercury", :mass 330.2M}
 {:id 2, :name "Venus", :mass 4868.5M}
 {:id 3, :name "Earth", :mass 5973.6M}
 {:id 4, :name "Mars", :mass 641.85M}
 {:id 9, :name "Pluto", :mass 13.105M}]
```

## license

Copyright © 2025 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
