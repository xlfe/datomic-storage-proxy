(ns net.xlfe.dsp.datastore.core
  (:require
    [taoensso.timbre :as timbre :refer [debug error info]]
    [clj-commons.byte-streams :as bs])
  (:import
    (java.nio ByteBuffer)
    [com.google.cloud.datastore Datastore KeyFactory Entity Key Entity$Builder PathElement
     Query EntityQuery EntityQuery$Builder KeyQuery KeyQuery$Builder StructuredQuery$OrderBy StructuredQuery$PropertyFilter
     Blob BlobValue StringValue LongValue NullValue]))

(set! *warn-on-reflection* true)

(def ^String entity-kind "dkvs")
; Datastore doesn't allow storing Entities larger than 1 MiB - 89 bytes
(def MAX_ENTITY_SIZE 1048487)

(defrecord DatomicKV [^String id ^String m r v])

(defrecord DatastoreInstance
  [^Datastore datastore ^String namespace])

(defn id->key ^Key
  [{:keys [^Datastore datastore namespace]} ^String id]
  (->
      (.newKeyFactory datastore)
      ^KeyFactory (.setKind entity-kind)
      ^KeyFactory (.setNamespace namespace)
      (.newKey id)))

(defn child-key
  [{:keys [^Datastore datastore namespace]} ^String parent-id ^Long idx]
  ^Key
  (->
    (.newKeyFactory datastore)
    ^KeyFactory (.setKind entity-kind)
    ^KeyFactory (.addAncestor (PathElement/of entity-kind parent-id))
    ^KeyFactory (.setNamespace namespace)
    (.newKey idx)))

(defn ->blob ^BlobValue
  [b]
  (->
    (BlobValue/newBuilder (Blob/copyFrom ^bytes b))
    (.setExcludeFromIndexes true)
    (.build)))

(defn ->string ^StringValue
  [s]
  (->
    (StringValue/newBuilder s)
    (.setExcludeFromIndexes true)
    (.build)))

(defn ->long ^LongValue
  [l]
  (->
    (LongValue/newBuilder ^long l)
    (.setExcludeFromIndexes true)
    (.build)))

(defn ->nil ^NullValue
  []
  (->
    (NullValue/newBuilder)
    (.setExcludeFromIndexes true)
    (.build)))

(defn pack-entity
  ([dsi ^DatomicKV d]
   (pack-entity dsi d nil))
  ([dsi ^DatomicKV {:keys [id m r v]} total-bytes]
   {:pre [(string? id) (some? m) (or (int? r) (bytes? v))]}
   (let [eb ^Entity$Builder (Entity/newBuilder (id->key dsi id))]

     (if total-bytes
       (.set eb "t" (->long total-bytes))
       (.set eb "t" (->nil)))

     (.build
       (cond->
         (.set eb "m" (->string m))

         (some? r)
         (.set "r" (->long r))

         (some? v)
         (.set "v" (->blob v)))))))

(defn pack-trailing-blob ^Entity
  [dsi idx parent-id part-v]
  {:pre [(string? parent-id) (bytes? part-v)]}
  (.build
    ^Entity$Builder
    (.set
      ^Entity$Builder
      (Entity/newBuilder ^Key (child-key dsi parent-id idx))
      "x"
      (->blob part-v))))

(defn unpack-entity
  [^Entity e]
  (when e
    (->DatomicKV
      (.getName ^Key (.getKey e))

      (when (.contains e "m")
        (.getString e "m"))

      (when (.contains e "r")
        (.getLong e "r"))

      (when (.contains e "v")
        (.toByteArray (.getBlob e "v"))))))

(defn concat-additional-blobs
  [v ^ByteBuffer buf extra]
  (.put ^ByteBuffer buf ^bytes v)
  (dorun
    (map
      (fn [^Entity e]
        (.put
          ^ByteBuffer buf
          (.toByteArray (.getBlob e "x"))))
      extra))
  (.array buf))

(defn get-keys-by-id-with-ancestors
  [{:keys [^Datastore datastore namespace] :as dsi} id]
  (let [query ^KeyQuery
        (->
          ^KeyQuery$Builder (Query/newKeyQueryBuilder)
          ^KeyQuery$Builder (.setNamespace ^String namespace)
          ^KeyQuery$Builder (.setKind entity-kind)
          ^KeyQuery$Builder (.setFilter (StructuredQuery$PropertyFilter/hasAncestor (id->key dsi id)))
          (.build))]

    (iterator-seq (.run datastore query))))

(defn get-by-id-with-ancestors
  [{:keys [^Datastore datastore namespace] :as dsi} id]
  (let [query ^EntityQuery
        (->
          ^EntityQuery$Builder (Query/newEntityQueryBuilder)
          ^EntityQuery$Builder (.setNamespace ^String namespace)
          ^EntityQuery$Builder (.setKind entity-kind)
          ^EntityQuery$Builder (.setFilter (StructuredQuery$PropertyFilter/hasAncestor (id->key dsi id)))
          ^EntityQuery$Builder (.setOrderBy
                                 ^StructuredQuery$OrderBy
                                 (StructuredQuery$OrderBy/asc "__key__")
                                 ^"[Lcom.google.cloud.datastore.StructuredQuery$OrderBy;"
                                 (make-array StructuredQuery$OrderBy 0))
          (.build))

        results (iterator-seq (.run datastore query))]

    (when-let [^Entity e (first results)]
      (if-let [total-size (when (.contains e "t") (if (.isNull e "t") nil (.getLong e "t")))]
        (update (unpack-entity e) :v concat-additional-blobs (ByteBuffer/allocate total-size) (rest results))
        (unpack-entity e)))))

(defn split-entity
  [dsi bytes-size ent-size ^DatomicKV {:as d :keys [id m v]}]
  (map-indexed
    (fn [idx v-part]
      (if (zero? idx)
        (pack-entity dsi (assoc d :v (bs/to-byte-array v-part)) bytes-size)
        (pack-trailing-blob dsi idx id (bs/to-byte-array v-part))))
    (bs/to-byte-buffers v {:chunk-size (- MAX_ENTITY_SIZE ent-size 2)})))

(defn put!
  [{:keys [^Datastore datastore] :as dsi} ^DatomicKV {:as d :keys [id m v]}]
  (let [ent-size (+ 68 (count m))
        bytes-size (when v (count v))]
    (if (and
          (some? v)
          (<= MAX_ENTITY_SIZE (+ ent-size bytes-size)))
      (.put datastore ^"[Lcom.google.cloud.datastore.Entity;" (into-array Entity (split-entity dsi bytes-size ent-size d)))
      (.put datastore ^Entity (pack-entity dsi d))))
  true)

(defn delete!
  [{:keys [^Datastore datastore] :as dsi} id]
  (.delete datastore (into-array Key (get-keys-by-id-with-ancestors dsi id)))
  true)

(defn update-map!
  [{:keys [^Datastore datastore] :as dsi} id new-r new-m old-r]
  (let [e (.get datastore (id->key dsi id))]
    (when (= old-r (.getLong e "r"))
      (.update datastore
         (into-array [(->
                        (Entity/newBuilder e)
                        (.set "r" (->long new-r))
                        (.set "m" (->string new-m))
                        (.set "t" (->nil))
                        (.build))]))
      true)))
