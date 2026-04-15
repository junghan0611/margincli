(ns margincli.anomaly
  "Anomaly Detection — transaction-first 변동점 탐지.
   결과가 흔들린 시점에서만 외부를 탐색한다."
  (:require [margincli.io :as mio]))

(def anomalies-path "data/anomalies.jsonl")

;; ── 통계 함수 ─────────────────────────────────────

(defn median
  "중앙값. 빈 컬렉션은 0."
  [coll]
  (if (empty? coll) 0.0
    (let [sorted (vec (sort coll))
          n      (count sorted)]
      (if (odd? n)
        (double (nth sorted (quot n 2)))
        (/ (+ (double (nth sorted (dec (quot n 2))))
              (double (nth sorted (quot n 2))))
           2.0)))))

(defn mad
  "Median Absolute Deviation."
  [coll]
  (let [med (median coll)]
    (median (mapv #(Math/abs (- (double %) med)) coll))))

(defn robust-z-score
  "Modified z-score (MAD 기반). MAD=0이면 0 반환."
  [value coll]
  (let [med (median coll)
        m   (mad coll)]
    (if (zero? m) 0.0
      (* 0.6745 (/ (- (double value) med) m)))))

(defn rolling-mean-deviation
  "전체 평균 대비 편차."
  [value coll]
  (if (empty? coll) 0.0
    (let [mean (/ (reduce + 0.0 (map double coll)) (count coll))]
      (- (double value) mean))))

;; ── 탐지 ──────────────────────────────────────────

(defn severity
  "점수 → 심각도. thresholds = {:low N :medium N :high N}"
  [score thresholds]
  (let [abs-score (Math/abs (double score))]
    (cond
      (>= abs-score (:high thresholds))   "high"
      (>= abs-score (:medium thresholds)) "medium"
      (>= abs-score (:low thresholds))    "low"
      :else nil)))

(defn detect
  "tx 레코드에서 anomaly 탐지.
   config: {:severity-thresholds {...}}
   returns: anomaly 레코드 벡터 (severity가 있는 것만)"
  [tx-records config]
  (let [thresholds (get-in config [:anomaly :severity-thresholds]
                           {:low 1.5 :medium 2.5 :high 3.5})
        values     (mapv :value tx-records)
        now        (mio/now-iso)]
    (->> tx-records
         (map (fn [tx]
                (let [rz    (robust-z-score (:value tx) values)
                      rmd   (rolling-mean-deviation (:value tx) values)
                      ;; 더 보수적인 robust-z를 주 점수로
                      sev   (severity rz thresholds)]
                  (when sev
                    {:id          (mio/next-id "anom" anomalies-path)
                     :type        "anomaly"
                     :tx-id       (:id tx)
                     :entity      (:entity tx)
                     :grain       (:grain tx)
                     :metric      (:metric tx)
                     :value       (:value tx)
                     :method      "robust-z"
                     :score       (Math/round (* rz 100.0) )
                     :score-raw   rz
                     :severity    sev
                     :detected-at now
                     :detail      {:robust-z rz
                                   :rolling-mean-dev rmd
                                   :baseline (:baseline tx)
                                   :delta    (:delta tx)}}))))
         (keep identity)
         vec)))

(defn detect-and-save!
  "탐지 + anomalies.jsonl에 저장."
  [tx-records config]
  (let [anomalies (detect tx-records config)]
    (doseq [a anomalies]
      (mio/append-jsonl anomalies-path a))
    anomalies))

;; ── 조회 ──────────────────────────────────────────

(defn list-anomalies []
  (mio/read-jsonl anomalies-path))

(defn find-anomaly [id]
  (first (filter #(= id (:id %)) (list-anomalies))))

;; ── 출력 ──────────────────────────────────────────

(defn print-anomalies
  [anomalies]
  (if (empty? anomalies)
    (println "  (탐지된 anomaly 없음)")
    (doseq [a anomalies]
      (println (str "  " (:id a)
                    "  " (:entity a)
                    "  " (:metric a) "=" (format "%.4f" (double (:value a)))
                    "  severity=" (:severity a)
                    "  z=" (format "%.2f" (double (get-in a [:detail :robust-z]))))))))
