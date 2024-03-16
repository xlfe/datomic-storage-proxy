(ns net.xlfe.dsp.datastore.interface
  (:require
    [net.xlfe.dsp.jdbc.interface :as i]
    [clj-commons.byte-streams :as bs]
    [net.xlfe.dsp.datastore.core :as core]
    [net.xlfe.dsp.datastore.datastore :as datastore]
    [taoensso.timbre :as timbre :refer [log  trace  debug  info  warn  error  fatal  report]]))

(deftype GCPDatastore
  [datastore-instance]
  i/KVStore

  (get-by-id [_ id]
    (core/get-by-id-with-ancestors datastore-instance id))

  (put! [_ id r m v]
    (core/put! datastore-instance (core/->DatomicKV id m r v)))

  (delete! [_ id]
    (core/delete! datastore-instance id))

  (update-map! [_ id new-rv new-mp old-rv]
    (core/update-map! datastore-instance id new-rv new-mp old-rv))

  (connected? [this]
    (let [r (rand-int 10000000)
          ba (byte-array 10)
          _ (doseq [i (range 10)]
              (aset-byte ba i (- (rand-int 255) 128)))
          _ (i/put! this "test-put" r "none" ba)
          x (i/get-by-id this "test-put")]
      (assert (= r (:r x)))
      (assert (bs/bytes= ba (:v x)))
      true)))

(defn get-default-instance
  [{:keys [host path] :as uri} {:keys [namespace]}]
  (let [database-id (subs path 1)
        project-id host]
    (info "Opening connection to GCP Datastore: " project-id database-id namespace)
    (->GCPDatastore
      (core/->DatastoreInstance (datastore/->default project-id database-id) namespace))))

(defn get-emulator-instance
  [{:keys [host port path] :as uri} {:keys [namespace]}]
  (let [path (subs path 1)]
    (info "Opening connection to GCP Datastore Emulator:" host port path namespace)
    (->GCPDatastore
      (core/->DatastoreInstance (datastore/->emulator host port path) namespace))))
