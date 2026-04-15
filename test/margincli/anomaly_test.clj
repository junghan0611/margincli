(ns margincli.anomaly-test
  (:require [clojure.test :refer [deftest is testing]]
            [margincli.anomaly :as anom]))

(deftest median-test
  (testing "중앙값"
    (is (= 3.0 (anom/median [1 3 5])))
    (is (= 2.5 (anom/median [1 2 3 4])))
    (is (= 0.0 (anom/median [])))))

(deftest mad-test
  (testing "Median Absolute Deviation"
    ;; [1 3 5] → median=3, deviations=[2 0 2] → MAD=2
    (is (= 2.0 (anom/mad [1 3 5])))
    ;; [-14.68 25.49 31.62] → median=25.49, devs=[40.17 0 6.13] → MAD=6.13
    (is (< (Math/abs (- 6.13 (anom/mad [-14.68 25.49 31.62]))) 0.01))))

(deftest robust-z-score-test
  (testing "Furniture -14.68%의 robust z-score"
    (let [values [-14.68 25.49 31.62]
          z      (anom/robust-z-score -14.68 values)]
      ;; z = 0.6745 * (-14.68 - 25.49) / 6.13 ≈ -4.42
      (is (< z -4.0) "Furniture는 강한 음수 z-score")
      (is (> z -5.0)))))

(deftest severity-test
  (testing "심각도 판정"
    (let [th {:low 1.5 :medium 2.5 :high 3.5}]
      (is (= "high" (anom/severity -4.42 th)))
      (is (= "medium" (anom/severity 3.0 th)))
      (is (= "low" (anom/severity 1.8 th)))
      (is (nil? (anom/severity 0.5 th))))))

(deftest detect-test
  (testing "tx 레코드에서 anomaly 탐지"
    (let [txs [{:id "tx-001" :entity "Furniture" :metric "margin-rate"
                :value -0.1468 :grain "category" :baseline 0.1414 :delta -0.2882}
               {:id "tx-002" :entity "Office Supplies" :metric "margin-rate"
                :value 0.2549 :grain "category" :baseline 0.1414 :delta 0.1135}
               {:id "tx-003" :entity "Technology" :metric "margin-rate"
                :value 0.3162 :grain "category" :baseline 0.1414 :delta 0.1748}]
          config {:anomaly {:severity-thresholds {:low 1.5 :medium 2.5 :high 3.5}}}
          anomalies (anom/detect txs config)]
      ;; Furniture만 탐지되어야 함
      (is (= 1 (count anomalies)))
      (is (= "Furniture" (:entity (first anomalies))))
      (is (= "high" (:severity (first anomalies)))))))
