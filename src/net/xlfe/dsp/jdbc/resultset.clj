(ns net.xlfe.dsp.jdbc.resultset
  (:require
    [taoensso.timbre :as timbre :refer [info  error]])
  (:import
    [java.sql ResultSet]))

(defn no-results
  []
  (reify ResultSet
    (close [_])
    (next [_] false)))

(defn single-empty-row
  []
  (let [more?* (atom true)]
    (reify ResultSet
      (close [_])
      (next [_]
        (boolean
          (when @more?*
            (reset! more?* false)
            true))))))

(defn single-row
  [{:keys [id m r v]}]
  (let [more?* (atom true)]
    (reify ResultSet
      (close [_])
      (next [_]
        (boolean
          (when @more?*
            (reset! more?* false)
            true)))
      (^String getString [_ ^String n]
        (case n
          "id" id
          "map" m))
      (^long getLong [_ ^String n]
        (assert (= n "rev"))
        (or r 0))
      (^bytes getBytes [_ ^String n]
        (assert (= n "val"))
        v))))
