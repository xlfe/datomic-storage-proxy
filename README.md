# Datomic storage proxy (dsp)

This repository answers a question you probably never wanted to know :-

* Could Datomic's storage layer use other KV stores aside from the officially supported ones?
* How would you hack together a terrible JDBC driver?

# Compile the JDBC Datomic Storage Proxy Driver

This driver needs to be available on the classpath for both the Datomic transactor and the Datomic peer

```bash
clj -M:build-driver
```
Copy the resulting `target/net.xlfe.dsp.jdbc.driver-<VERSION>.jar` to the lib path for the
Datomic transactor as well as the project where you're using the associated Datomic Peer

# Supported KV Stores

## GCP Datastore

### Datastore

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service/account/key.json
```

```clojure
(ns peer
  (:require [datomic.api :as d]))

(def gcp-datastore-connection-map
  {:protocol :sql
   :sql-driver-class "net.xlfe.dsp.jdbc.Driver"
   :db-name "datomic-database-name"
   :sql-url "gcp-datastore://project-id/datastore-id?namespace=datastore-namespace"})

(defn -main
  []
  (d/create-database gcp-datastore-connection-map)
  (let [conn (d/connect gcp-datastore-connection-map)]
    (run-tests conn)
```

### Datastore emulator

```bash
gcloud beta emulators datastore start --use-firestore-in-datastore-mode
```

```clojure
(ns peer
  (:require [datomic.api :as d]))

(def gcp-datastore-connection-map
  {:protocol :sql
   :sql-driver-class "net.xlfe.dsp.jdbc.Driver"
   :db-name "datomic-test-db"
   :sql-url "gcp-datastore-emulator://localhost:8081/test-project?namespace=datomic123"})

(defn -main
  []
  (d/create-database gcp-datastore-connection-map)
  (let [conn (d/connect gcp-datastore-connection-map)]
    (run-tests conn)
```

## Datalevin server

Datalevin has a [standalone commandline client](https://github.com/juji-io/datalevin/blob/master/doc/install.md#command-line-tool) that runs a server which we can connect to over a network

```clojure
(ns peer
  (:require [datomic.api :as d]))

(def datalevin-connection-map
  {:protocol :sql
   :sql-driver-class "net.xlfe.dsp.jdbc.Driver"
   :db-name "datomic-test-db"
   :sql-url "dtlv://datalevin:datalevin@localhost:8898/my-kv-store?table=datomic-table"})

(defn -main
  []
  (d/create-database datalevin-connection-map)
  (let [conn (d/connect datalevin-connection-map)]
    (run-tests conn)
```

Note the sql url is actually the Datalevin's server url with the addition of a query param `?table=datomic-table`
to indicate the table
