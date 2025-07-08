(defproject carme "0.1.5"
  :description "Carme, a Gemini server written in Clojure."
  :url "gemini://spikydinosaur.com"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure             "1.12.1"]
                 [org.bouncycastle/bcpkix-jdk15on "1.70"]]
  :repl-options {:init-ns carme.core}
  :profiles {:uberjar {:aot :all}}
  :main carme.core)
