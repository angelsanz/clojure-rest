(ns clojure-rest.report-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clojure-rest.handler :refer :all]
            [clojure-rest.data.comments :refer [create-new-comment]]
            [clojure-rest.util.utils :refer [is-uuid]]
            [cheshire.core :refer [parse-string
                                   generate-string]]))


(deftest test-reports
  (let [user (app (-> (mock/request :post "/api/users"
                                    (generate-string {:email "ayy@lmao.com"
                                                      :name "foo"
                                                      :username "realhumanbean"
                                                      :password "secret"}))
                      (mock/content-type "application/json")))
        
        comment-id (:commentsid (:body
                                  (create-new-comment {:content "This is a comment"
                                                       :positivevotes 1
                                                       :negativevotes 0})))
        
        event-id ((parse-string (:body
                                  (app (-> (mock/request :post "/api/events"
                                                         (generate-string {:author "realhumanbean"
                                                                           :title "This is an event title"
                                                                           :content "Politically incorrect content"
                                                                           :coordinates "0, 0"
                                                                           :initialdate "9999-12-31"}))
                                           (mock/content-type "application/json"))))) "eventsid")
        
        token ((parse-string (:body
                               (app (-> (mock/request :post "/api/auth"
                                                      (generate-string {:username "realhumanbean"
                                                                        :password "secret"}))
                                        (mock/content-type "application/json"))))) "token")]
    
    ;; Creating a comment report should return the uuid of the report
    (testing "Creating a comment report"
      (let [response (app (-> (mock/request :post "/api/reports/comments"
                                            (generate-string {:content "This comment is offensive!"
                                                              :commentsid comment-id
                                                              :token token}))
                              (mock/content-type "application/json")))]
        (is (= (:status response) 200))
        (is (true? (is-uuid (:body response))))))
    
    ;; Creating an event report should return the uuid of the report
    (testing "Creating an event report"
      (let [response (app (-> (mock/request :post "/api/reports/events"
                                            (generate-string {:content "This event is offensive!"
                                                              :eventsid event-id
                                                              :token token}))
                              (mock/content-type "application/json")))]
        (is (= (:status response) 200))
        (is (true? (is-uuid (:body response))))))))