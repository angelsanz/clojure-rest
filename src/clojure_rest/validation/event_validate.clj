(ns clojure-rest.validation.event-validate
  (:require [clojure-rest.validation.validate :as v]
            [clojure-rest.data.users :as users]
            [clojure-rest.validation.user-validate :as uv]
            [clojure-rest.util.error :refer [bind-to
                                             bind-error
                                             err-bad-request
                                             apply-if-present]]))

;; String -> Boolean
(defn event-exists? [id]
  (v/field-exists-in-table? "events" "eventsid" id))


;; {} -> [{}?, Error?]
(defn check-event-not-exists [params]
  (v/check-field params :eventsid #(not (event-exists? %))))


;; String -> Either<{}|nil>
(defn get-event-table [id]
  (v/get-table-values "events" "eventsid" id))


;; {} -> [{}?, Error?]
;; Check for {{} :author}
(defn- check-author [params]
  (v/check-field params :author #(not (uv/user-exists? %))))


;; {} -> {}
;; Transforms the author from username to uuid
(defn- username->uuid [params]
  (assoc params :author ((users/get-user-id (params :author)) :usersid)))


;; {} -> [{}?, Error?]
;; Casts the coordinates to numbers and checks if the coordinates are in the correct range
(defn- check-coordinates [params]
  (if (and (<= -180 (params :latitude) 180)
           (<= -90 (params :longitude) 90))
    [params nil]
    [nil err-bad-request]))


;; {} -> [{}?, Error?]
;; Checks that the supplied date isn't earlier than today
(defn- check-date [params k]
  (let [result (compare (params k)
                        (.format (java.text.SimpleDateFormat. "yyy-MM-dd")
                                 (java.util.Date.)))]
    (if (>= result 0)
      [params nil]
      [nil err-bad-request])))


;; {} -> [{}?, Error?]
;; Checks that the final date isn't earlier than the initial date
(defn- check-past-finaldate [params]
  (let [result (compare (params :finaldate)
                        (params :initialdate))]
    (if (>= result 0)
      [params nil]
      [nil err-bad-request])))


;; {} -> {}
;; If no :finaldate key is present, set it to nil
(defn- complete-finaldate-if-not-present [params]
  (if (params :finaldate)
    params
    (assoc params :finaldate nil)))


;; [{}?, Error?] -> [{}?, Error?]
(defn validate-create [params]
  (->> (bind-error check-author params)
       (bind-error #(bind-to (username->uuid %)))
       (bind-error check-coordinates)
       (bind-error #(check-date % :initialdate))
       (apply-if-present #(check-date % :finaldate) :finaldate)
       (apply-if-present check-past-finaldate :finaldate)
       (bind-error #(bind-to (complete-finaldate-if-not-present %)))))
