(ns net.xlfe.dsp.datalevin.interface
  (:require
    [net.xlfe.dsp.jdbc.interface :as i]
    [net.xlfe.dsp.datalevin.core :as core]
    [clj-commons.byte-streams :as bs]
    [taoensso.timbre :as timbre :refer [log  trace  debug  info  warn  error  fatal  report]]))

(deftype DatalevinStore
  [datastore-instance]
  i/KVStore

  (get-by-id [_ id]
    (core/get-by-id datastore-instance id))

  (put! [_ id r m v]
    (core/put! datastore-instance id m r v))

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

(defn get-instance
  [uri query-map]
  (assert (string? uri))
  (info "Opening Datalevin for URI:" uri query-map)
  (->DatalevinStore (core/get-connection uri query-map)))
