(defproject stylefruits/gniazdo "0.3.1-IZ-6"
  :description "A WebSocket client for Clojure"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.eclipse.jetty.websocket/websocket-client "9.3.0.M1"]]
  :repl-options {:init-ns gniazdo.core}
  :profiles {:dev
             {:dependencies [[http-kit "2.1.19"]]}})
