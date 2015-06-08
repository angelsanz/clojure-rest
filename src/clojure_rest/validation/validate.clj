(ns clojure-rest.validation.validate
  (:require [clojure.java.jdbc :as sql]
            [clojure-rest.db :as db]
            [clojure-rest.util.error :refer [bind-to
                                             err-bad-request]]))

;; String String String -> Boolean
;; Checks if the value exists in the given row of the given table
(defn field-exists-in-table? [table row value]
  (not (empty?
         (sql/with-connection (db/db-connection)
                              (sql/with-query-results results
                                                      [(str "select 1 from " table " where " row " = ?") value]
                                                      (into {} results))))))


;; String String String String -> {}
;; Get the value in supplied row where pkey = pkval in table
(defn- get-value-from-table-where [table pkey pkval row]
  (sql/with-connection (db/db-connection)
                       (sql/with-query-results results
                                               [(str "select " row " from " table " where " pkey " = ?") pkval]
                                               (into {} results))))


;; String String String String String -> Boolean
;; Checks if the given row contains the given value on table where pkey = pkval
(defn field-has-value-in-table? [table pkey pkval row value]
  (= (str value)
     (str (->> (get-value-from-table-where table pkey pkval row)
               ((keyword row))))))


;; {} key pred -> [{}?, Error?]
;; If (pred ({} :key) -> [nil Error]
;; Otherwise [{} nil]
(defn check-field [params field pred]
  (if (pred (params field))
    [nil err-bad-request]
    (bind-to params)))


;; String String String -> Either<{}|nil>
;; Gets the stored hashmap of the table where pkey = value
(defn get-table-values [table pkey value]
  (sql/with-connection (db/db-connection)
                       (sql/with-query-results results
                                               [(str "select * from " table " where " pkey " = ?") value]
                                               (cond (empty? results) nil
                                                     :else (first results)))))
