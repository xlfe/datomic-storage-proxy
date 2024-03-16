(ns net.xlfe.dsp.jdbc.statement
  (:require
    [net.xlfe.dsp.jdbc.interface :as i]
    [net.xlfe.dsp.jdbc.resultset :as rs]
    [taoensso.timbre :as timbre :refer [log  trace  debug  info  warn  error  fatal  report]])
  (:import
    [java.sql PreparedStatement]))

; https://docs.oracle.com/javase/8/docs/api/java/sql/PreparedStatement.html

(defmulti statement- (fn [_ stmt] stmt))

(defmethod statement- "select 1"
  [driver _]
  (reify
    PreparedStatement
    (close [_])
    (executeQuery [_]
      (if (i/connected? driver)
        (rs/single-empty-row)
        (rs/no-results)))))

(defn update-val
  [ufn]
  (let [values (volatile! [])]
    (reify
      PreparedStatement
      (close [_])
      (^void setObject [_ ^int i x]
        (vswap! values assoc (dec i) x))
      (executeUpdate [_]
        (ufn @values)))))

(defmethod statement- "select id, rev, map, val from datomic_kvs where id = ?" [driver _]
  (let [key (volatile! nil)]
    (reify
      PreparedStatement
      (close [_])
      (^void setObject [_ ^int i x]
        (vreset! key x))
      (executeQuery [_]
        (if-let [d (i/get-by-id driver @key)]
          (rs/single-row d)
          (rs/no-results))))))

(defmethod statement-
  "insert into datomic_kvs (id, rev, map) values (?, ?, ?)" [driver _]
  (update-val
    (fn [[id r m]]
      (when (i/put! driver id r m nil)
        1))))

(defmethod statement-
  "insert into datomic_kvs (id, map, val) values (?, ?, ?)" [driver _]
  (update-val
    (fn [[id m v]]
      (when (i/put! driver id nil m v)
        1))))

(defmethod statement-
  "update datomic_kvs set rev=?, map=? where id=? and rev=?" [driver _]
  (update-val
    (fn [[new-r new-m id old-r]]
      (when (i/update-map! driver id new-r new-m old-r)
        1))))

(defmethod statement-
  "delete from datomic_kvs where id = ?" [driver _]
  (update-val
    (fn [[id]]
      (when (i/delete! driver id) 1))))

; "insert into datomic_kvs (id, rev, map, val) values (?, ?, ?, ?)"
; "insert into datomic_kvs (
; "update datomic_kvs set rev=?, map=?, val=? where id=? and rev=?"
; "update datomic_kvs set ????? where id=? and rev=?"

(defmethod statement- :default [_ stmt]
  (error "Bad PreparedStatement:" stmt)
  (throw (IllegalArgumentException. (str "I don't know the " stmt))))

(defn statement
  [driver stmt]
  (statement- driver stmt))
