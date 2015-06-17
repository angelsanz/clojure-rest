(ns clojure-rest.data.reports
  (:require [ring.util.response :refer [response]]
            [clojure.set :refer [rename-keys]]
            [clojure.java.jdbc :as sql]
            [clojure-rest.db :as db]
            [clojure-rest.auth :as auth]
            [clojure-rest.util.http :as h]
            [clojure-rest.util.error :refer :all]
            [clojure-rest.util.utils :refer [-!>>]]
            [clojure-rest.sanitize.sanitize :as sanitize]
            [clojure-rest.validation.event-validate :as ev]
            [clojure-rest.validation.comment-validate :as cv]
            [clojure-rest.data.users :refer [get-user-id]]))


;; --------------
;; Query literals

(def ^:private comment-report-extract
  (str "select reports.reportsid as id, "
       "users.username as author, "
       "reports.content, comments_reports.commentsid as contentid "
       "from reports "
       "inner join comments_reports on (reports.reportsid = comments_reports.reportsid) "
       "inner join users_comments on (users_comments.commentsid = comments_reports.commentsid) "
       "inner join users on (users_comments.author = users.usersid)"))

(def ^:private event-report-extract
  (str "select reports.reportsid as id, "
       "users.username as author, "
       "reports.content, events_reports.eventsid as contentid "
       "from reports "
       "inner join events_reports on (reports.reportsid = events_reports.reportsid) "
       "inner join events_author on (events_author.eventsid = events_reports.eventsid) "
       "inner join users on (events_author.usersid = users.usersid)"))


(def ^:private comment-report-extract-query
  (str comment-report-extract " where reports.reportsid = ?"))

(def ^:private comment-report-extract-all
  (str comment-report-extract " group by reports.reportsid"))


(def ^:private event-report-extract-query
  (str event-report-extract " where reports.reportsid = ?"))

(def ^:private event-report-extract-all
  (str event-report-extract " group by reports.reportsid"))


;; --------------------
;; Basic CRD Operations

;; String -> [{:author :content :contentid :id}?]
(defn- report-extract-all! [query]
  (sql/with-connection (db/db-connection)
                       (sql/with-query-results results
                                               [query]
                                               (vec results))))

;; String UUID -> {:author :content :contentid :id}?
(defn- report-extract! [query id]
  (sql/with-connection (db/db-connection)
                       (sql/with-query-results results
                                               [query id]
                                               (when-not (empty? results)
                                                 (first results)))))


;; {:issuer :content :commentsid} -> UUID
(defn- comment-report-insert! [content]
  (let [id (db/uuid)
        primitive (assoc content :issuer (:usersid (get-user-id (content :issuer))))
        form (-> primitive (assoc :reportsid id) (rename-keys {:issuer :author}))
        report (dissoc form :commentsid)
        comment-report (-> form (dissoc :author) (dissoc :content))]
    (sql/with-connection (db/db-connection)
                         (sql/insert-record :reports report)
                         (sql/insert-record :comments_reports comment-report))
    id))


;; {:issuer :content :eventsid} -> UUID
(defn- event-report-insert! [content]
  (let [id (db/uuid)
        primitive (assoc content :issuer (:usersid (get-user-id (content :issuer))))
        form (-> primitive (assoc :reportsid id) (rename-keys {:issuer :author}))
        report (dissoc form :eventsid)
        event-report (-> form (dissoc :author) (dissoc :content))]
    (sql/with-connection (db/db-connection)
                         (sql/insert-record :reports report)
                         (sql/insert-record :events_reports event-report))
    id))


;; UUID -> ()
(defn- report-delete! [id]
  (sql/with-connection (db/db-connection)
                       (sql/delete-rows :reports ["reportsid = ?" id])))


;; ------------------
;; Error binding glue


;; [UUID?, Error?] -> [{}?, Error?]
(defn- bind-comment-report-extract [params]
  (bind-error #(let [res (report-extract! comment-report-extract-query %)]
                 (if (nil? res)
                   [nil err-not-found]
                   [res nil])) params))


;; [UUID?, Error?] -> [{}?, Error?]
(defn- bind-event-report-extract [params]
  (bind-error #(let [res (report-extract! event-report-extract-query %)]
                 (if (nil? res)
                   [nil err-not-found]
                   [res nil])) params))


;; [{}?, Error?] -> [UUID, nil]
(defn- bind-comment-report-insert [params]
  (bind-error #(bind-to
                 (comment-report-insert! %)) params))


;; [{}?, Error?] -> [UUID, nil]
(defn- bind-event-report-insert [params]
  (bind-error #(bind-to
                 (event-report-insert! %)) params))


;; UUID -> Natural
(defn- bind-report-delete [id]
  (-> (-!>> id report-delete!)
      (#(when (= % id) status-deleted))))


;; ---------------
;; Pipe operations


;; {"content" "commentsid" "token"} -> Response[:body UUID? :status Either<200|401|...>]
(defn create-new-comment-report [content]
  (->> content
       auth/auth-adapter
       (bind-error #(cv/check-comment-not-exists %))
       (bind-error (fn [m] (bind-to (->> m :content sanitize/escape-html (assoc m :content)))))
       bind-comment-report-insert
       h/wrap-response))


;; {"content" "eventsid" "token"} -> Response[:body {}? :status Either<200|401|...>]
(defn create-new-event-report [content]
  (->> content
       auth/auth-adapter
       (bind-error #(ev/check-event-not-exists %))
       (bind-error (fn [m] (bind-to (->> m :content sanitize/escape-html (assoc m :content)))))
       bind-event-report-insert
       h/wrap-response))


;; String {"token"} -> Response[:body nil :status Either<204|401|404>
(defn delete-event-report [id content]
  (->> content
       auth/auth-adapter
       h/wrap-response))

;; () -> Response[:body [{}?] :status 200]
(defn get-all-comment-reports []
  (response (report-extract-all! comment-report-extract-all)))

;; () -> Response[:body [{}?] :status 200]
(defn get-all-event-reports []
  (response (report-extract-all! event-report-extract-all)))

;; () -> Response[:body [{}?] :status 200]
(defn get-all-reports []
  (response (vec (concat (report-extract-all! comment-report-extract-all)
                         (report-extract-all! event-report-extract-all)))))
