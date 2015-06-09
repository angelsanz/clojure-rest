(ns clojure-rest.routes.comment-routes
  (:require [compojure.core :refer :all]
            [clojure-rest.util.http :as http]))


(defn- comment-id-routes [id]
  (routes
    (GET "/" [] (http/not-implemented))
    (PUT "/" [] (http/not-implemented))
    (DELETE "/" [] (http/not-implemented))
    (OPTIONS "/" [] (http/options [:options :get :put :delete]))
    (ANY "/" [] (http/method-not-allowed [:options :get :put :delete]))))


(defroutes comment-routes
  (GET "/" [] (http/not-implemented))
  (POST "/" [] (http/not-implemented))
  (OPTIONS "/" [] (http/options [:options :get :post]))
  (ANY "/" [] (http/method-not-allowed [:options :get :post]))
  (context "/:id" [id] (comment-id-routes id)))
