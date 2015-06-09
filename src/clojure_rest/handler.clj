(ns clojure-rest.handler
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [ring.middleware.json :as json]
            [ring.util.response :refer :all]
            [clojure-rest.util.http :as http]
            [clojure.walk :refer [stringify-keys]]
            [clojure-rest.util.error :refer [err-not-found]]
            [clojure-rest.routes.auth-routes :refer [auth-routes]]
            [clojure-rest.routes.user-routes :refer [user-routes]]
            [clojure-rest.routes.event-routes :refer [event-routes]]
            [clojure-rest.routes.report-routes :refer [report-routes]]
            [clojure-rest.routes.comment-routes :refer [comment-routes]]
            [clojure-rest.routes.coordinate-routes :refer [coordinate-routes]]))


;; Response -> Response
;; Prints the content of the Response to stdout
(defn wrap-log-requests [handler]
  (fn [req]
    (->> (stringify-keys req)
         (interpose "\n")
         (println))
    (handler req)))


;; () -> Response
(defroutes app-routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
 	(route/resources "/")
  
  (context "/api" []

           (context "/auth" [] auth-routes)
           
           (context "/users" [] user-routes)
           
           (context "/events" [] event-routes)
           
           (context "/reports" [] report-routes)

           (context "/comments" [] comment-routes)
           
           (context "/coordinates" [] coordinate-routes)
           
           (OPTIONS "/" [] (http/options [:options] {:version "0.1.0-SNAPSHOT"}))
           
           (ANY "/" [] (http/method-not-allowed [:options]))
           
           (route/not-found {:status err-not-found}))
  
  (route/not-found {:status err-not-found}))


;; Request -> Response
(defn prod-handler []
  (-> (handler/api app-routes)
      (json/wrap-json-body)
      (json/wrap-json-response)))


;; Request -> Response
(defn dev-handler[]
  (-> (prod-handler)
      (wrap-log-requests)))


;; Request -> Response
;; Entry point
(def app (prod-handler))
