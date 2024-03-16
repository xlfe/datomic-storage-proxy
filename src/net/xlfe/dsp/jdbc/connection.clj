(ns net.xlfe.dsp.jdbc.connection
  (:require
    [net.xlfe.dsp.jdbc.statement :as s]
    [net.xlfe.dsp.jdbc.interface :as i]
    [clojure.string]
    [lambdaisland.uri :refer [uri query-map]]
    [net.xlfe.dsp.datastore.interface :as datastore]
    [net.xlfe.dsp.datalevin.interface :as datalevin]
    [taoensso.timbre :as timbre :refer [info error]])
  (:import
    [java.sql Connection Statement PreparedStatement]))

(defn get-connection
  [uri-string]
  (cond
    (clojure.string/starts-with? uri-string "dtlv")
    (datalevin/get-instance uri-string (query-map uri-string))

    (clojure.string/starts-with? uri-string "gcp-datastore-emulator")
    (datastore/get-emulator-instance (uri uri-string) (query-map uri-string))

    (clojure.string/starts-with? uri-string "gcp-datastore")
    (datastore/get-default-instance (uri uri-string) (query-map uri-string))

    :else nil))

;https://docs.oracle.com/javase/8/docs/api/java/sql/Connection.html
(defn connection
  [uri ifo]
  (when-let [storage (get-connection uri)]
    (reify Connection
      (clearWarnings [_])
      (getAutoCommit [_] true)
      (^Statement createStatement [_]
        (reify Statement
          (close [_])
          (^boolean execute [_ ^String sql]
            ; connectivity check
            (assert (= sql "select 1"))
            (i/connected? storage))))

      (^PreparedStatement prepareStatement [_ ^java.lang.String sql]
        (s/statement storage sql)))))
