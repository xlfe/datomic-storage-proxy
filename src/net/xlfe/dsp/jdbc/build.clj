; Based on https://clojure.org/guides/tools_build#_compiled_uberjar_application_build

(ns net.xlfe.dsp.jdbc.build
  (:require [clojure.tools.build.api :as b]))

(def lib 'net.xlfe.dsp.jdbc.driver)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn -main []
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile [lib]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main lib}))
