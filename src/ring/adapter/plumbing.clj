(ns ring.adapter.plumbing
  (:import (java.io InputStream File RandomAccessFile FileInputStream)
           (java.net URLConnection)
	   (io.netty.channel ChannelFutureListener DefaultFileRegion ChannelProgressiveFutureListener)
	   (io.netty.buffer ByteBufInputStream Unpooled)
	   (io.netty.handler.stream ChunkedStream ChunkedFile)
	   (io.netty.handler.codec.http HttpHeaders HttpVersion HttpMethod 
					       HttpResponseStatus DefaultFullHttpResponse DefaultHttpResponse)))
	   
(defn- remote-address [ctx]
  (-> ctx .channel .remoteAddress .toString (.split ":") first (subs 1)))

(defn- get-meth [req]
  (-> req .getMethod .name .toLowerCase keyword))

(defn- get-body [req]
  (ByteBufInputStream. (.content req)))

(defn- get-headers [req]
  (reduce (fn [headers name]
	    (assoc headers (.toLowerCase name) (-> req .headers (.get name))))
	  {}
	  (-> req .headers .names)))

(defn- content-type [headers]
  (if-let [ct (headers "content-type")]
    (-> ct (.split ";") first .trim .toLowerCase)))


(defn- uri-query [req]
  (let [uri  (.getUri req)
	idx (.indexOf uri "?")]
    (if (= idx -1)
      [uri nil]
      [(subs uri 0 idx) (subs uri (inc idx))])))
    
(defn- keep-alive? [headers msg]
  (let [version (.getProtocolVersion msg)
	minor (.minorVersion version)
	major (.majorVersion version)]
    (not (or (= (headers "connection") "close")
	     (and (and (= major 1) (= minor 0))
		  (= (headers "connection") "keep-alive"))))))
  
(defn build-request-map
  "Converts a netty request into a ring request map"
  [ctx netty-request]
  (let [headers (get-headers netty-request)
	socket-address (-> ctx .channel .localAddress)
	[uri query-string] (uri-query netty-request)]
    {:server-port        (.getPort socket-address)
     :server-name        (.getHostName socket-address)
     :remote-addr        (remote-address ctx)
     :uri                uri
     :query-string       query-string
     :scheme             (keyword (headers "x-scheme" "http"))
     :request-method     (get-meth netty-request)
     :headers            headers
     :content-type       (content-type headers)
     :content-length     (headers "content-length")
     :character-encoding (headers "content-encoding")
     :body               (get-body netty-request)}))
     ;:keep-alive         (keep-alive? headers netty-request)}))

(defn- set-headers [response headers]
  (doseq [[key val-or-vals]  headers]
    (if (string? val-or-vals)
      (-> response .headers (.set key val-or-vals))
      (doseq [val val-or-vals]
	(-> response .headers (.add key val))))))
		 
(defn- set-content-length [msg length]
  (HttpHeaders/setContentLength msg length))

(defn- write-content [ctx response keep-alive]
  (do (set-content-length response (-> response .content .readableBytes))
    (if keep-alive
	(.writeAndFlush ctx response)
    (-> ctx (.writeAndFlush response) 
                      (.addListener ChannelFutureListener/CLOSE)))))

(defn- write-file [ch response file keep-alive zero-copy]
  (let [raf (RandomAccessFile. file "r")
        len (.length raf)
        region (if zero-copy
                 (DefaultFileRegion. (.getChannel raf) 0 len)
                 (ChunkedFile. raf 0 len 8192))]
    (-> response .headers (.set "Content-Type" (URLConnection/guessContentTypeFromName (.getName file))))
    (set-content-length response len) 
    (.writeAndFlush ch response) ;write initial line and header
    (let [write-future (.writeAndFlush ch region)]
      (if zero-copy
        (.addListener write-future
                      (proxy [ChannelProgressiveFutureListener] []
                             (operationComplete [fut]
                                                (.releaseExternalResources region)))))
      (if not keep-alive
        (.addListener write-future ChannelFutureListener/CLOSE)))))

(defn write-response [ctx zerocopy keep-alive {:keys [status headers body]}]
      (let [ver HttpVersion/HTTP_1_1 
            status-code (HttpResponseStatus/valueOf status)
            content (-> body (.getBytes "utf-8") Unpooled/wrappedBuffer)
            netty-response (DefaultFullHttpResponse. ver status-code content)]
        (write-content ctx netty-response keep-alive)))
