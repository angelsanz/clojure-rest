(ns clojure-rest.routes.report-routes
  (:require [compojure.core :refer :all]
            [clojure-rest.util.http :as http]
            [clojure-rest.data.reports :as reports]))


(defn- comment-report-routes []
  (routes
    (GET "/" [] (http/not-implemented))
    (POST "/" {body :body} (reports/create-new-comment-report body))
    (DELETE "/" [] (http/not-implemented))
    (OPTIONS "/" [] (http/options [:options :post :delete :get]))
    (ANY "/" [] (http/method-not-allowed [:options :post :delete :get]))))


(defn- event-report-routes []
  (routes
    (GET "/" [] (http/not-implemented))
    (POST "/" {body :body} (reports/create-new-event-report body))
    (DELETE "/" [] (http/not-implemented))
    (OPTIONS "/" [] (http/options [:options :post :delete :get]))
    (ANY "/" [] (http/method-not-allowed [:options :post :delete :get]))))


(defroutes report-routes
  (GET "/" [] (http/not-implemented))
  (OPTIONS "/" [] (http/options [:options :get]))
  (ANY "/" [] (http/method-not-allowed [:options :get]))
  (context "/events" [] (event-report-routes))
  (context "/comments" [] (comment-report-routes)))