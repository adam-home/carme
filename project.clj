(defproject carme "0.1.4"
  :description "Carme, a Gemini server written in Clojure."
  :url "gemini://spikydinosaur.com"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [less-awful-ssl      "1.0.6"]]
  :repl-options {:init-ns carme.core}
  :profiles {:uberjar {:aot :all}}
  :main carme.core)
