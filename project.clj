(defproject ring-netty-adapter "0.0.3"
  :description "Ring Netty adapter"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [io.netty/netty-all "4.0.28.Final"]
                 [ring "0.2.5"]]
  :dev-dependencies [[swank-clojure "1.2.1"]]
  :namespaces [ring.adapter.netty ring.adapter.plumbing]
  :main ring.example.hello-world)
