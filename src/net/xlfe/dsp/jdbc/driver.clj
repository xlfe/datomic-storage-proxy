(ns net.xlfe.dsp.jdbc.driver
  (:require
    [net.xlfe.dsp.jdbc.connection :as c])
  (:gen-class
    :name net.xlfe.dsp.jdbc.Driver
    :state state
    :init init
    :implements [java.sql.Driver])
  (:import
    [java.sql DriverManager]))

;https://docs.oracle.com/javase/8/docs/api/java/sql/Driver.html

(defn make-driver
  []
  (proxy
    [java.sql.Driver]
    []
    (connect [url info]
      (c/connection url info))
    (acceptsURL [url]
      true)
    (getPropertyInfo [url info]
      (make-array java.sql.DriverPropertyInfo 0))
    (getMajorVersion [] 1)
    (getMinorVersion [] 0)
    (jdbcCompliant []
      true)
    (getParentLogger []
      (java.util.logging.Logger/getLogger "net.xlfe.dsp.jdbc.driver"))))

(def -init
  (let [driver (atom nil)]
    (fn []
      (if-let [r @driver]
        [[] r]
        (let [r (make-driver)]
          (DriverManager/registerDriver r)
          (reset! driver r)
          [[] r])))))

(defn -connect
  [this url info]
  (.connect (.state this) url info))
