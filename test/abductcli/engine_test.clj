(ns abductcli.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [abductcli.engine :as eng]))

(deftest calc-margin-basic
  (testing "기본 마진 계산 — 수수료 없음"
    (let [result (eng/calc-margin 29900M 15000M 0M)]
      (is (= 29900M (:sale-price result)))
      (is (= 15000M (:cost result)))
      (is (= 14900.00M (:contribution result)))
      (is (= 0.4983M (:margin-rate result))))))

(deftest calc-margin-with-commission
  (testing "마진 계산 — 수수료 12%"
    (let [result (eng/calc-margin 29900M 15000M 0.12M)]
      (is (= 3588.00M (:commission result)))
      (is (= 11312.00M (:contribution result)))
      (is (= 0.3783M (:margin-rate result))))))

(deftest calc-margin-zero-price
  (testing "판매가 0 — 마진율 0"
    (let [result (eng/calc-margin 0M 15000M 0.12M)]
      (is (= 0M (:margin-rate result))))))

(deftest calc-margin-negative-profit
  (testing "원가 > 판매가 — 마진율 음수"
    (let [result (eng/calc-margin 10000M 15000M 0.10M)]
      (is (neg? (.signum (:contribution result))))
      (is (neg? (.signum (:margin-rate result)))))))

(deftest reverse-margin-basic
  (testing "역산 — 목표 마진율 25%, 수수료 12%"
    (let [result (eng/reverse-margin 0.25M 15000M 0.12M)]
      (is (nil? (:error result)))
      (is (pos? (.signum (:min-sale-price result))))
      ;; 검증: 역산 결과로 계산하면 목표 마진율 도달
      (is (>= (.compareTo (:margin-rate (:verify result)) 0.25M) 0)))))

(deftest reverse-margin-no-commission
  (testing "역산 — 수수료 없음, 목표 30%"
    (let [result (eng/reverse-margin 0.30M 10000M 0M)]
      (is (nil? (:error result)))
      ;; price = 10000 / (1 - 0 - 0.30) = 10000 / 0.70 = 14285.7143
      (is (pos? (.signum (:min-sale-price result)))))))

(deftest reverse-margin-impossible
  (testing "역산 — 불가능한 조건 (마진율+수수료율 >= 100%)"
    (let [result (eng/reverse-margin 0.90M 15000M 0.12M)]
      (is (some? (:error result))))))

(deftest calc-from-row-test
  (testing "정규화된 행에서 마진 지표 추출"
    (let [row {:product {:id "TEC-PH-001" :name "Galaxy" :category "Technology"}
               :channel {:region "East" :segment "Consumer"}
               :margin  {:sales 500M :profit 125M :cost 375M
                         :quantity 2 :discount 0.1M
                         :unit-price 250M :unit-cost 187.50M
                         :margin-rate 0.2500M}}
          result (eng/calc-from-row row)]
      (is (= "TEC-PH-001" (:product-id result)))
      (is (= "East/Consumer" (:channel result)))
      (is (= 500M (:sales result)))
      (is (= 0.2500M (:margin-rate result))))))
