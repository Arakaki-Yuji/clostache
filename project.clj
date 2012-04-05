(defproject de.ubercode.clostache/clostache "1.3.0"
  :min-lein-version "2.0.0"
  :description "{{ mustache }} for Clojure"
  :url "http://github.com/fhd/clostache"
  :license {:name "GNU Lesser General Public License 2.1"
            :url "http://www.gnu.org/licenses/lgpl-2.1.txt"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [jline/jline "0.9.94"]
                 [org.clojure/data.json "0.1.2"]]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :resource-paths ["src/test/resources"]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0-beta6"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.4"]}
  :warn-on-reflection true)
