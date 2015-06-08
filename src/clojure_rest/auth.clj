(ns clojure-rest.auth
  (:require [ring.util.response :refer :all]
            [clojure-rest.validation.validate :as v]
            [clojure-rest.data.users :as users]
            [clojure-rest.sanitize.user-sanitize :as us]
            [pandect.core :refer [sha256-hmac]]
            [environ.core :refer [env]]
            [clojure-rest.db :as db]
            [clojure.java.jdbc :as sql]
            [clojure-rest.util.http :as h]
            [clojure-rest.util.error :refer :all]
            [clojure-rest.util.utils :refer :all]))


;; Either<String|nil>
;; Gets the secret key from the environment variable secret-key, returns nil if not found
;; (will throw exception at runtime)
(def ^:private SECRET-KEY
  (when-let [key (env :secret-key)] key))


;; () -> String
;; Generates a random token with username$timestamp$hmac(sha256, username$timestamp)
(defn- generate-session [username date]
  (str username "$" date "$" (sha256-hmac (str username "$" date) SECRET-KEY)))


;; String -> Boolean
;; Check if token exists in database
(defn- token-exists? [token]
  (v/field-exists-in-table? "sessions" "token" token))


;; UUID -> String
;; Creates a token and inserts it into the session table, then returns that token
(defn- make-token! [username user-id]
  (try-backoff []
               (let [now (time-now)
                     token (generate-session username now)]
                 (sql/with-connection (db/db-connection)
                                      (sql/insert-values :sessions [] [token
                                                                       user-id
                                                                       (format-time now)]))
                 token)))


;; [{}?, Error?] -> [String?, Error?]
;; Gets the user's uuid and generates an auth token
(defn- bind-token-gen [params]
  (bind-error (fn [value]
                (let [user-id (users/get-user-id (value :username))]
                  (if (nil? user-id)
                    [nil err-not-found]
                    [{:token (make-token! (value :username) (user-id :usersid))} nil]))) params))


;; String -> ()
;; Deletes the token from the database
(defn- revoke-token! [token]
  (sql/with-connection (db/db-connection)
                       (sql/delete-rows :sessions ["token = ?" token])))


;; [{}?, Error?] -> [{}?, Error?]
;; Checks if the given user / pass combination is correct, returns an error tuple
(defn- bind-validate [params]
  (bind-error #(if (users/pass-matches? (% :username) (% :password))
                 [% nil]
                 [nil err-unauthorized]) params))


;; String -> Either<{}|nil>
;; Returns the username associated with the given token if that token is less than 6 hours old
;; Returns nil otherwise
(defn- validate-token [token]
  (sql/with-connection (db/db-connection)
                       (sql/with-query-results results
                                               ["select username
                                                from sessions
                                                inner join users on (sessions.usersid = users.usersid)
                                                where token = ?
                                                and createdAt > timestampadd(minute, -21600, current_timestamp)" token]
                                               (when-not (empty? results)
                                                 (first results)))))


;; {} -> [{}?, Error?]
;; Takes a map with a token key and returns one without it
;; Adding the issuer key with the appropiate username
;; Returns an error if (params :token) is not valid
(defn- bind-token-validation [params]
  (let [user-id (:username (validate-token (params :token)))]
    (if (nil? user-id)
      [nil err-unauthorized]
      [(-> params (dissoc :token) (assoc :issuer user-id)) nil])))


;; {} -> [{}?, Error?]
;; Checks for the :token field in the given map
(defn- check-token [params]
  (if (params :token)
    [params nil]
    [nil err-unauthorized]))


;; {} -> Response [:body nil :status Natural]
;; Destructures the given content into username and password for validation ingestion
(defn auth-handler [content]
  (->> content
       clojure.walk/keywordize-keys
       us/sanitize-auth
       bind-validate
       bind-token-gen
       h/wrap-response))


;; {} -> [{}?, Error?]
;; Checks if there is a :token key in the supplied map
;; If it does, and it is valid, it dissocs said key and assocs the issuer username
;; If anything goes wrong, returns an error
(defn auth-adapter [content]
  (->> content
       clojure.walk/keywordize-keys
       check-token
       (bind-error bind-token-validation)))


;; String, {} -> Response[:status Either<204|403|404>]
;; Deletes the given token if it exists and the params supply the same token as a parameter
(defn delete-token [token params]
  (if (token-exists? token)
    (->> params
         clojure.walk/keywordize-keys
         check-token
         (bind-error (fn [v] (if (= token (v :token)) [v nil] [nil err-forbidden])))
         (bind-error (fn [v] (do (revoke-token! (v :token)) [(v :token) nil])))
         (bind-error (fn [_] [nil status-deleted]))
         h/wrap-response)
    {:status err-not-found}))
