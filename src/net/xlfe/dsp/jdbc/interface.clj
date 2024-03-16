(ns net.xlfe.dsp.jdbc.interface)

(defprotocol KVStore
  (connected? [this])
  (get-by-id [this id])
  (put! [this id r m v])
  (delete! [this id])
  (update-map! [this id new-r new-m old-r]))
