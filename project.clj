(defproject stylefruits/gniazdo "0.4.1-IZ"
  :description "A WebSocket client for Clojure"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.eclipse.jetty.websocket/websocket-client "9.3.0.RC0"]]
  :repl-options {:init-ns gniazdo.core}
  :profiles {:dev
             {:dependencies [[http-kit "2.1.19"]]}})
