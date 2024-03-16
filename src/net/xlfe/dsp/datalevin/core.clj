(ns net.xlfe.dsp.datalevin.core
  (:require
    [taoensso.timbre :as timbre :refer [log  trace  debug  info  warn  error  fatal  report]]
    [clojure.string]
    [datalevin.core :as d]))

(defrecord DatalevinInstance
  [db table])

(defn get-connection
  [uri {:keys [table]}]
  (assert (not (clojure.string/blank? table)))
  (let [db (d/open-kv uri {})]
    (d/open-dbi db table)
    (->DatalevinInstance db table)))

(defn get-by-id
  [{:keys [db table]} id]
  (when-let [[r m v] (d/get-value db table id)]
    {:id id :r r :m m :v v}))

(defn put!
  [{:keys [db table]} id m r v]
  (d/transact-kv db [[:put table id [r m v]]])
  true)

(defn update-map!
  [{:keys [db table]} id new-r new-m old-r]
  (let [[r _ v] (d/get-value db table id)]
    (when (= old-r r)
      (d/transact-kv db [[:put table id [new-r new-m v]]])
      true)))
