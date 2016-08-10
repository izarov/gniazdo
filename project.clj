(defproject stylefruits/gniazdo "1.0.0-IZ"
  :description "A WebSocket client for Clojure"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.eclipse.jetty.websocket/websocket-client "9.3.8.v20160314"]]
  :repl-options {:init-ns gniazdo.core}
  :profiles {:dev
             {:dependencies [[http-kit "2.1.19"]]}})
