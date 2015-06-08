(ns clojure-rest.validation.comment-validate
  (:require [clojure-rest.validation.validate :as v]
            [clojure-rest.data.users :as users]
            [clojure-rest.validation.user-validate :as uv]
            [clojure-rest.validation.event-validate :as ev]
            [clojure-rest.util.error :refer [bind-to
                                             bind-error
                                             err-bad-request
                                             apply-if-present]]))


;; {} -> [{}?, Error?]
;; Check for {{} :author}
(defn- check-author [params]
  (v/check-field params :author #(not (uv/user-exists? %))))


;; UUID -> Boolean
(defn- comment-exists? [uuid]
  (v/field-exists-in-table? "comments" "commentsid" uuid))


;; {} -> [{}?, Error?]
;; Checks for [{} :parent]
(defn- check-parent [params]
  (v/check-field params :parent #(not (comment-exists? %))))


;; {} -> [{}?, Error?]
;; Checks for {:eventsid}
(defn- check-event [params]
  (v/check-field params :eventsid #(not (ev/event-exists? %))))


;; {} -> [{}?, Error?]
;; Error if ({} :commentsid) does not exist
(defn check-comment-not-exists [params]
  (v/check-field params :commentsid #(not (comment-exists? %))))


;; {} -> {}
;; Transforms the author from username to uuid
(defn username->uuid [params]
  (assoc params :author ((users/get-user-id (params :author)) :usersid)))


;; {} -> {}
;; Fills in the default values for a new comment
;; On create we get the author, content, eventid and maybe parentid (if it's a reply)
(defn- complete-default-comment [content]
  (assoc content :positivevotes 1 :negativevotes 0))


;; {} -> {}
;; If no parent key is present, assign it to the given id
;; If a parent key is present, rename it to parentid
(defn complete-parent-id [content id]
  (if (content :parent)
    (-> content (assoc :parentid (content :parent)) (dissoc :parent))
    (assoc content :parentid id)))


;; [{}?, Error?] -> [{}?, Error?]
(defn validate-comment [params]
  (->> (bind-error check-author params)
       (bind-error #(bind-to (username->uuid %)))
       (bind-error check-event)
       (bind-error #(bind-to (complete-default-comment %)))
       (apply-if-present check-parent :parent)))
