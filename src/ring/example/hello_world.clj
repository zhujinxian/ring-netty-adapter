(ns ring.example.hello-world
  (:use ring.adapter.netty)
  (:import java.util.Date java.text.SimpleDateFormat))

(defn app
  [req]
  (println "vvvvvvvvvvvvvvvvv")
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (str "0000000000000000")})


(defn -main [& args]
  (run-netty app {:port 8080}))

