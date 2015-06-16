(ns ring.adapter.netty
  (:use ring.adapter.plumbing)
  (:import (java.net InetSocketAddress)
	   (java.io ByteArrayInputStream)
	   (io.netty.bootstrap ServerBootstrap)
	   (io.netty.handler.logging LogLevel LoggingHandler)
           (io.netty.channel ChannelOption ChannelInitializer ChannelInboundHandlerAdapter)
	   (io.netty.channel.nio NioEventLoopGroup)
	   (io.netty.channel.socket.nio NioServerSocketChannel)
	   (io.netty.handler.stream ChunkedWriteHandler)
	   (io.netty.handler.codec.http HttpServerCodec HttpObjectAggregator)
           (io.netty.handler.stream ChunkedWriteHandler)))
	   
(defn- make-handler [handler zerocopy]
  (proxy [ChannelInboundHandlerAdapter] []
    (channelRead [ctx msg]
		     (let [request-map (build-request-map ctx msg)
			   ring-response (handler request-map)]
		       (when ring-response
			 (write-response ctx zerocopy (request-map :keep-alive) ring-response))))
    (exceptionCaught [ctx cause]
		     (-> cause .printStackTrace)
		     (-> ctx .channel .close))))

(defn- make-pipeline [ch handler options]
  (let [pipeline (.pipeline ch)
	pipeline (doto pipeline
		  (.addLast "http" (HttpServerCodec.))
		  (.addLast "2" (HttpObjectAggregator. 65636))
                  (.addLast "3" (ChunkedWriteHandler.))
		  ;(.addLast "deflater" (HttpContentCompressor.))
		  (.addLast "handler" (make-handler handler (or (:zerocopy options) false))))]
    pipeline))

(defn- make-child-handler [handler options]
  (proxy [ChannelInitializer] [] (initChannel [ch] (make-pipeline ch handler options))))
	
(defn- create-server [options handler]
  (let [bootstrap (ServerBootstrap.)
	bootstrap (doto bootstrap
                    (.group (NioEventLoopGroup. 1) (NioEventLoopGroup.))
		    (.channel NioServerSocketChannel)
                    (.handler (LoggingHandler. LogLevel/INFO))
                    (.childHandler (make-child-handler handler options)))]
    bootstrap))
	
(defn- bind [bs port]
  (-> bs (.bind port) .sync .channel .closeFuture .sync))

(defn run-netty [handler options]
  (let [bootstrap (create-server options handler)
	port (options :port 80)]
    (println "Running server on port:" port)
    (bind bootstrap port)))

		    
