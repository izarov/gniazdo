(ns gniazdo.core
  (:import java.net.URI
           java.nio.ByteBuffer
           (org.eclipse.jetty.websocket.client ClientUpgradeRequest
                                               WebSocketClient)
           (org.eclipse.jetty.util.ssl SslContextFactory)
           (org.eclipse.jetty.websocket.api WebSocketListener
                                            RemoteEndpoint
                                            Session)
           (javax.net.ssl SSLContext)))

(set! *warn-on-reflection* 1)

;; ## Messages

(defprotocol Sendable
  (send-to-endpoint [this ^RemoteEndpoint e]
    "Sends an entity to a given WebSocket endpoint."))

(extend-protocol Sendable
  java.lang.String
  (send-to-endpoint [msg ^RemoteEndpoint e]
    @(.sendStringByFuture e msg))

  java.nio.ByteBuffer
  (send-to-endpoint [buf ^RemoteEndpoint e]
    @(.sendBytesByFuture e buf)))

(extend-type (class (byte-array 0))
  Sendable
  (send-to-endpoint [data ^RemoteEndpoint e]
    @(.sendBytesByFuture e (ByteBuffer/wrap data))))

;; ## Client

(defprotocol ^:private Client
  (send-msg [this msg]
    "Sends a message (implementing `gniazdo.core/Sendable`) to the given WebSocket.")
  (close [this]
    "Closes the WebSocket."))

;; ## WebSocket Helpers

(defn- noop
  [& _])

(defn- add-headers!
  [^ClientUpgradeRequest request headers]
  {:pre [(every? string? (keys headers))]}
  (doseq [[header value] headers]
    (let [header-values (if (sequential? value)
                          value
                          [value])]
      (assert (every? string? header-values))
      (.setHeader
        request
        ^String header
        ^java.util.List header-values))))

(defn- upgrade-request
  ^ClientUpgradeRequest
  [{:keys [headers]}]
  (doto (ClientUpgradeRequest.)
    (add-headers! headers)))

(defn- listener
  ^WebSocketListener
  [{:keys [on-connect on-receive on-binary on-error on-close]
    :or {on-connect noop
         on-receive noop
         on-binary  noop
         on-error   noop
         on-close   noop}}
   result-promise]
  (reify WebSocketListener
    (onWebSocketText [_ msg]
      (on-receive msg))
    (onWebSocketBinary [_ data offset length]
      (on-binary data offset length))
    (onWebSocketError [_ throwable]
      (if (realized? result-promise)
        (on-error throwable)
        (deliver result-promise throwable)))
    (onWebSocketConnect [_ session]
      (deliver result-promise session)
      (on-connect session))
    (onWebSocketClose [_ x y]
      (on-close x y))))

(defn- deref-session
  ^Session
  [result-promise]
  (let [result @result-promise]
    (if (instance? Throwable result)
      (throw result)
      result)))

;; ## WebSocket Client + Connection (API)

(defn client
  "Create a new instance of `WebSocketClient`. If the optionally supplied URI
   is representing a secure WebSocket endpoint (\"wss://...\") an SSL-capable
   instance will be returned."
  (^WebSocketClient
    [] (WebSocketClient.))
  (^WebSocketClient
    [uri & {:keys [ssl-context]}]
    (let [^URI uri' 
          (if (instance? String uri) 
           (URI. uri)
           uri)]
      (if (= "wss" (.getScheme uri'))
        (let [ssl-context-factory (SslContextFactory.)]
          (when ssl-context (.setSslContext ssl-context-factory ssl-context))
          (WebSocketClient. ssl-context-factory))
        (WebSocketClient.)))))

(defn- connect-with-client
  "Connect to a WebSocket using the supplied `WebSocketClient` instance."
  [^WebSocketClient client ^URI uri opts]
   (let [request (upgrade-request opts)
         cleanup (::cleanup opts)
         result-promise (promise)
         listener (listener opts result-promise)]
     (.start client)
     (.connect client listener uri request)
     (let [session (deref-session result-promise)]
       (reify Client
         (send-msg [_ msg]
           (send-to-endpoint msg (.getRemote session)))
         (close [_]
           (when cleanup
             (cleanup))
           (.close session))))))

(defn- connect-helper
  [^URI uri opts]
  (let [client (client uri)]
    (try
      (.start client)
      (->> (assoc opts ::cleanup #(.stop client))
           (connect-with-client client uri))
      (catch Throwable ex
        (.stop client)
        (throw ex)))))

(defn connect
  "Connects to a WebSocket at a given URI (e.g. ws://example.org:1234/socket)."
  [uri & {:keys [on-connect on-receive on-binary on-error on-close headers client]
          :as opts}]
  (let [uri' (URI. uri)]
    (if client
      (connect-with-client client uri' opts)
      (connect-helper uri' opts))))
