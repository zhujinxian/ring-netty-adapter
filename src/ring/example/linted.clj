; An example of inserting the linter between each component to ensure 
; compliance to the Ring spec.

(ns ring.example.linted
  (:use (ring.handler dump)
        (ring.middleware stacktrace file file-info reload lint)
        (ring.adapter netty)))

(def app
  (-> handle-dump
    wrap-lint
    wrap-stacktrace
    wrap-lint
    wrap-file-info
    wrap-lint
    (wrap-file "example/public")
    wrap-lint
    (wrap-reload '(ring.handler.dump))
    wrap-lint))

(run-netty app {:port 8080})
