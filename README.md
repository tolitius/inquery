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
$ boot dev

boot.user=> (require '[inquery.core :as q]
                     '[jdbc.core :as jdbc])
```

`dbspec` along with a `set of queries` would usually come from `config.edn` / consul / etc :

```clojure
boot.user=> (def dbspec
              {:subprotocol "h2"
               :subname "file:/tmp/solar"})

boot.user=> (def queries
              (q/make-query-map #{:create-planets
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
boot.user=> (with-open [conn (jdbc/connection dbspec)]
              (jdbc/execute conn (:create-planets queries)))
```

check out the solar system:

```clojure
boot.user=> (with-open [conn (jdbc/connection dbspec)]
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
boot.user=> (with-open [conn (jdbc/connection dbspec)]
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
boot.user=> (with-open [conn (jdbc/connection dbspec)]
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

or per individual parameter:

```clojure
=> (with-open [conn (jdbc/connection dbspec)]
     (jdbc/fetch conn (-> (:find-planets-by-name queries)
                      (q/with-params {:name {:as ""}}))))
```

#### things to note about escaping

`nil`s are converted to "null":

```clojure
=> (-> "name = :name" (q/with-params {:name nil}))
"name = null"
```

`{:as nil}` or `{:as ""}` is "as is", so it will be replaced with an empty string:

```clojure
=> (-> "name = :name" (q/with-params {:name {:as nil}}))
"name = "

=> (-> "name = :name" (q/with-params {:name ""}))
"name = ''"
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
$ boot dev

boot.user=> (require '[scratchpad :as sp :refer [dbspec queries]])

boot.user=> (sp/execute dbspec (:create-planets queries))

boot.user=> (sp/fetch dbspec (:find-planets queries))
[{:id 1, :name "Mercury", :mass 330.2M}
 {:id 2, :name "Venus", :mass 4868.5M}
 {:id 3, :name "Earth", :mass 5973.6M}
 {:id 4, :name "Mars", :mass 641.85M}
 {:id 5, :name "Jupiter", :mass 1898600M}
 {:id 6, :name "Saturn", :mass 568460M}
 {:id 7, :name "Uranus", :mass 86832M}
 {:id 8, :name "Neptune", :mass 102430M}
 {:id 9, :name "Pluto", :mass 13.105M}]

boot.user=> (sp/fetch dbspec (:find-planets-by-mass queries) {:max-mass 5973.6})
[{:id 1, :name "Mercury", :mass 330.2M}
 {:id 2, :name "Venus", :mass 4868.5M}
 {:id 3, :name "Earth", :mass 5973.6M}
 {:id 4, :name "Mars", :mass 641.85M}
 {:id 9, :name "Pluto", :mass 13.105M}]
```

## license

Copyright © 2020 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
