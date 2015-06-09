(ns clojure-rest.validation.user-validate
  (:require [clojure-rest.validation.validate :as v]
            [clojure-rest.util.error :refer [=>>=
                                             =>?=
                                             bind-error
                                             bind-to
                                             apply-if-present
                                             err-unauthorized
                                             err-forbidden]]))


;; String -> Boolean
(defn user-exists? [username]
  (and (v/field-exists-in-table? "users" "username" username)
       (v/field-has-value-in-table? "users" "username" username "deleted" false)))


;; String -> Boolean
(defn email-exists? [email]
  (v/field-exists-in-table? "users" "email" email))


;; String -> Either<{}|nil>
(defn get-user-table [username]
  (v/get-table-values "users" "username" username))


;; {} -> [{}?, Error?]
;; Check for {{} :username}, return error if username exists
(defn- check-username-exists [params]
  (v/check-field params :username user-exists?))


;; {} -> [{}?, Error?]
;; Check for {{} :username}, return error if username doesn't exist
(defn- check-username-not-exists [params]
  (v/check-field params :username (complement user-exists?)))

;; {} -> [{}?, Error?]
;; Check for {{} :email}
(defn- check-email [params]
  (v/check-field params :email email-exists?))


;; {} -> [{}?, Error?]
;; Check if user is trying to sneak a deletion
(defn- check-deletion [params]
  (if (params :deleted) [nil err-unauthorized] [params nil]))


;; {} -> [{}?, Error?]
;; Check if user is trying to sudo
(defn- check-admin-attempt [params]
  (if (params :moderator) [nil err-forbidden] [params nil]))


;; {} -> {}
;; Fills in the default values for a new user
;; From signup form we only get email, username and password
(defn- complete-default-user [content]
  (assoc content :profileImage nil :deleted false :moderator false))


;; [{}?, Error?] -> [{}?, Error?]
(defn validate-signup [params]
  (=>>= params
        #(bind-to (complete-default-user %))
        check-email
        check-username-exists))


;; [{}?, Error?] -> [{}?, Error?]
(defn validate-update [params]
  (=>?= params
       (check-email :email)
       (check-username-exists :username)
       (check-deletion :deleted)
       (check-admin-attempt :moderator)))


;; [{}?, Error?] -> [{}?, Error?]
;; Checks that the username exists
(defn validate-add [params]
  (=>>= params
        check-username-not-exists))
