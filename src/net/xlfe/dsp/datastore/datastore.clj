(ns net.xlfe.dsp.datastore.datastore
  (:require
    [taoensso.timbre :as timbre :refer [debug error info]])
  (:import
    [com.google.cloud NoCredentials ServiceOptions]
    [com.google.cloud.datastore DatastoreOptions]
    [com.google.cloud.datastore.testing LocalDatastoreHelper]))

; See https://github.com/googleapis/java-datastore

(defn start-local-datastore!
  [port]
  (let [local (LocalDatastoreHelper/create 1.0 (int port))]
    (.start local)
    (debug "started local datastore on port" port)))

(defn with-datastore-emulator
  [f]
  (let [datastore-helper ^LocalDatastoreHelper (LocalDatastoreHelper/create 1.0 (int 8282))
        options ^DatastoreOptions (.getOptions datastore-helper)
        ds (.getService options)]
    (try
      (.start datastore-helper)
      (f ds)
      (finally
        (.stop ds)))))

(defn ->emulator
  [host port project]
  (->
    (doto
      (DatastoreOptions/newBuilder)
      (.setProjectId project) ;(DatastoreOptions/getDefaultProjectId))
      (.setHost (str "http://" host ":" port))
      (.setCredentials (NoCredentials/getInstance))
      (.setRetrySettings (ServiceOptions/getNoRetrySettings)))
    (.build)
    (.getService)))

(defn ->default
  [project-id database-id]
  (.getService (DatastoreOptions/getDefaultInstance))
  (->
      (doto
        (DatastoreOptions/newBuilder)
        (.setProjectId project-id)
        (.setDatabaseId database-id))
      (.build)
      (.getService)))
