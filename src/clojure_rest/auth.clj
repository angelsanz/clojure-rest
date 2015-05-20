(ns clojure-rest.auth
  (:require [ring.util.response :refer :all]
            [clojure-rest.data.users :as users]
            [clojure-rest.util.user-sanitize :as us]
            [pandect.core :refer [sha256-hmac]]
            [environ.core :refer [env]]
            [clojure-rest.data.db :as db]
            [clojure.java.jdbc :as sql]
            [clojure-rest.util.http :as h]
            [clojure-rest.util.error :refer [bind-error]]
            [clojure-rest.util.utils :refer [time-now
                                             format-time]]))


;; Either<String|nil>
;; Gets the secret key from the environment variable secret-key, returns nil if not found
;; (will throw exception at runtime)
(def ^:private SECRET-KEY
  (when-let [key (env :secret-key)] key))


;; () -> String
;; Generates a random token with username$timestamp$hmac(sha256, username$timestamp)
(defn- generate-session [username date]
  (str username "$" date "$" (sha256-hmac (str username "$" date) SECRET-KEY)))


;; UUID -> String
;; Creates a token and inserts it into the session table, then returns that token
(defn- make-token! [username user-id]
  (let [now (time-now)
        token (generate-session username now)]
    (sql/with-connection (db/db-connection)
                         (sql/insert-values :sessions [] [token
                                                          user-id
                                                          (format-time now)]))
    token))


;; [{}?, Error?] -> [String?, Error?]
;; Gets the user's uuid and generates an auth token
(defn- bind-token-gen [params]
  (bind-error (fn [value]
                (let [user-id (users/get-user-id (value :username))]
                  (if (nil? user-id)
                    [nil 404]
                    [{:token (make-token! (value :username) (user-id :usersid))} nil]))) params))


;; [{}?, Error?] -> [{}?, Error?]
;; Checks if the given user / pass combination is correct, returns an error tuple
(defn- bind-validate [params]
  (bind-error #(if (users/pass-matches? (% :username) (% :password))
                 [% nil]
                 [nil 401]) params))


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
  (let [user-id (validate-token (params :token))]
    (if (nil? user-id)
      [nil 401]
      [(-> params (dissoc :token) (assoc :issuer user-id)) nil])))


;; {} -> [{}?, Error?]
;; Checks for the :token field in the given map
(defn- check-token [params]
  (if (params :token)
    [params nil]
    [nil 401]))


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
