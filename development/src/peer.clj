(ns peer
  (:require
    [clojure.string]
    [clojure.java.io :as io]
    [datomic.uri :as uri]
    [datomic.garbage]
    [java-time.api :as jt]
    [datomic.api :as d]))

(def test-schema
   [
    [:db/add "datomic.tx" :db/txInstant #inst "2001"]
    {:db/ident :title
     :db/valueType :db.type/string
     ; :db/fulltext true
     :db/cardinality :db.cardinality/one}

    {:db/ident :uid
     :db/valueType :db.type/long
     :db/unique :db.unique/identity
     :db/cardinality :db.cardinality/one}

    {:db/ident :abytes
     :db/valueType :db.type/bytes
     :db/cardinality :db.cardinality/one}

    {:db/ident :lref
     :db/valueType :db.type/long
     :db/index true
     :db/cardinality :db.cardinality/one}

    {:db/ident :year
     :db/valueType :db.type/long
     :db/index true
     :db/cardinality :db.cardinality/one}])

(def english-word-list
  (clojure.string/split-lines
    (slurp (io/resource "wiki-100k.txt"))))

(def english-words (repeatedly #(rand-nth english-word-list)))

(defn rand-bytes
  [l]
  (let [ba (byte-array l)]
    (doseq [i (range l)]
      (aset-byte ba i (- (rand-int 255) 128)))
    ba))

(def small-vals 1000)

(defn run-tests
  [conn]
  (let [start-time  (jt/minus (jt/instant) (jt/days (* 365 10)))]

    @(d/transact conn test-schema)
    @(d/transact
       conn
       (into
         [[:db/add "datomic.tx" :db/txInstant (jt/java-date (jt/plus start-time (jt/seconds 10)))]]
         (mapcat
           (fn [i]
             [
              {:title (clojure.string/join " " (take (rand-int 100) english-words))
               :uid (+ i 4000)
               :abytes (rand-bytes 50)
               :lref (- Long/MAX_VALUE (rand-int 100000))
               :year (rand-int 2999)}])
           (range small-vals))))
    (prn "done!")
    @(d/transact
       conn
       (doall
         (mapcat
           (fn [i]
             [{:uid (+ i 400)
               :title (clojure.string/join " " (take (rand-int 100) english-words))
               :abytes (rand-bytes 1000000)
               :lref (- Long/MAX_VALUE (rand-int 100000))
               :year (rand-int 2999)}])
           (range 2))))
    (prn "done bytes!")))

(defn check-tests
  [conn]
  @(d/transact
     conn
     (doall
       (mapcat
         (fn [i]
           [{:db/excise [:uid (+ i 4000)]}])
         (range small-vals))))

  (prn "checked"))

(defn clean-db
  [conn]
  (prn "REINDEX:" (d/request-index conn))
  (prn "GC-STORAGE:" (d/gc-storage conn (jt/java-date (jt/minus (jt/instant) (jt/days 500)))))
  (let [b (d/basis-t (d/db conn))]
    (prn @(d/sync-excise conn b))
    (prn @(d/sync-index conn b)))
  (prn "cleaned"))

(def connection-map
  {:protocol :sql
   :sql-driver-class "net.xlfe.dsp.jdbc.Driver"
   :db-name "datomic-test-db"
   ; :sql-url "dtlv://datalevin:datalevin@localhost:8898/kvstore?table=datomic-table"
   ; :sql-url "gcp-datastore-emulator://10.0.0.150:8081/test-project?namespace=datomic123"
   :sql-url "gcp-datastore://YOUR-GCP-PROJECT/playground?namespace=datomic-testing"})

(defn gc-deleted-dbs-with-custom-driver
  "I couldn't figure out how to pass the sql-driver-class to the standalone gc-deleted-dbs cli"
  []
  (datomic.garbage/gc-deleted-dbs (uri/parse connection-map)))

(defn -main
  []
  (d/create-database connection-map)
  (run-tests (d/connect connection-map))
  (check-tests (d/connect connection-map))
  (clean-db (d/connect connection-map))
  (d/delete-database connection-map)
  (gc-deleted-dbs-with-custom-driver)
  (d/shutdown true))
