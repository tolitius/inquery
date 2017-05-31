# inquery

vanilla SQL with params

* no DSL
* no comments parsing
* no namespace creations
* no defs / defqueries
* no dependencies
* no edn SQL

just "read SQL with :params"

[![Clojars Project](https://clojars.org/tolitius/inquery/latest-version.svg)](http://clojars.org/tolitius/inquery)

## why

SQL is a great language, it is very expressive and exremely well optimized and supported by "SQL" databases.
I don't belive it needs any kind of wrappers and it should live in its pure SQL form.

`inquery` does two things:

* reads SQL files
* substitute params at runtime

Clojure APIs cover all the rest

## using inquery

There is nothing really to do other than bring the queries into a map with a `make-query-map` function.

`inquery` is about SQL: it _does not_ require or force a particular JDBC library or a database.

This example will use [funcool/clojure.jdbc](http://funcool.github.io/clojure.jdbc/latest/) to speak to
a sample [H2](http://www.h2database.com/html/main.html) database since both of them are great.

```clojure
$ boot dev

boot.user=> (require '[inquery.core :as q]
                     '[jdbc.core :as jdbc])
```

`dbspec` along with a `set of queries` would usually come from config.edn / consul / etc :

```clojure
boot.user=> (def dbspec
              {:subprotocol "h2"
               :subname "file:/tmp/solar"})

boot.user=> (def queries
              (q/make-query-map #{:create-planets
                                  :find-planets
                                  :find-planets-by-mass}))
```

`inquiry` by default will look into `sql/*` path for queries. in this case "dev-resources" is in a classpath:

```vim
▾ dev-resources/sql/
      create-planets.sql
      find-planets-by-mass.sql
      find-planets.sql
```

Ready to roll, let's create some planets:

```clojure
boot.user=> (with-open [conn (jdbc/connection dbspec)]
              (jdbc/execute conn (:create-planets queries)))
```

checking out the solar system:

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

## scratchpad

development [scratchpad](dev/scratchpad.clj) with sample shorcuts:

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

## License

Copyright © 2017 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
