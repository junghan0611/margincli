(ns abductcli.import-test
  (:require [clojure.test :refer [deftest is testing]]
            [abductcli.import :as imp]))

(deftest parse-decimal-test
  (testing "문자열 → BigDecimal"
    (is (= 100.50M (imp/parse-decimal "100.50")))
    (is (= 0M (imp/parse-decimal "")))
    (is (= 0M (imp/parse-decimal nil)))
    (is (= -50.25M (imp/parse-decimal "-50.25")))
    (is (= 0M (imp/parse-decimal "  ")))))

(deftest parse-int-test
  (testing "문자열 → int"
    (is (= 5 (imp/parse-int "5")))
    (is (= 0 (imp/parse-int "")))
    (is (= 0 (imp/parse-int nil)))))

(deftest normalize-row-test
  (testing "Superstore 행 → 도메인 맵"
    (let [row {"Row ID"       "1"
               "Order ID"     "CA-2024-001"
               "Order Date"   "2024-01-15"
               "Ship Date"    "2024-01-20"
               "Ship Mode"    "Standard Class"
               "Customer ID"  "CG-12520"
               "Customer Name" "Test User"
               "Segment"      "Consumer"
               "Country"      "United States"
               "City"         "Henderson"
               "State"        "Kentucky"
               "Postal Code"  "42420"
               "Region"       "East"
               "Product ID"   "TEC-PH-001"
               "Product Name" "Samsung Galaxy"
               "Category"     "Technology"
               "Sub-Category" "Phones"
               "Sales"        "500.00"
               "Profit"       "125.00"
               "Quantity"     "2"
               "Discount"     "0.1"}
          result (imp/normalize-row row)]
      ;; product
      (is (= "TEC-PH-001" (get-in result [:product :id])))
      (is (= "Samsung Galaxy" (get-in result [:product :name])))
      (is (= "Technology" (get-in result [:product :category])))
      (is (= "Phones" (get-in result [:product :sub-category])))
      ;; channel
      (is (= "East" (get-in result [:channel :region])))
      (is (= "Consumer" (get-in result [:channel :segment])))
      ;; margin
      (is (= 500.00M (get-in result [:margin :sales])))
      (is (= 125.00M (get-in result [:margin :profit])))
      (is (= 375.00M (get-in result [:margin :cost])))
      (is (= 250.00M (get-in result [:margin :unit-price])))
      (is (= 187.50M (get-in result [:margin :unit-cost])))
      (is (= 0.2500M (get-in result [:margin :margin-rate])))
      ;; 원본 보존
      (is (= row (:raw result))))))

(deftest normalize-row-negative-profit
  (testing "음수 이익 행 — 마진율 음수"
    (let [row {"Product ID"   "FUR-TA-001"
               "Product Name" "Table"
               "Category"     "Furniture"
               "Sub-Category" "Tables"
               "Region"       "South"
               "Segment"      "Consumer"
               "Order ID"     "CA-2024-002"
               "Order Date"   "2024-01-20"
               "Ship Date"    "2024-01-25"
               "Ship Mode"    "Standard Class"
               "Sales"        "957.58"
               "Profit"       "-383.03"
               "Quantity"     "5"
               "Discount"     "0.45"}
          result (imp/normalize-row row)]
      (is (neg? (.signum (get-in result [:margin :margin-rate]))))
      ;; cost = 957.58 - (-383.03) = 1340.61
      (is (= 1340.61M (get-in result [:margin :cost]))))))

(deftest import-csv-integration
  (testing "CSV 파일 임포트 — superstore.csv"
    (let [result (imp/import-csv "data/superstore.csv")]
      (is (= 30 (count (:rows result))))
      (is (= 30 (get-in result [:summary :total-rows])))
      (is (pos? (.signum (get-in result [:summary :total-sales]))))
      (is (contains? (set (get-in result [:summary :categories])) "Technology"))
      (is (contains? (set (get-in result [:summary :categories])) "Furniture"))
      (is (contains? (set (get-in result [:summary :categories])) "Office Supplies"))
      ;; 카테고리별 요약 존재
      (is (some? (get-in result [:summary :by-category "Technology"]))))))
