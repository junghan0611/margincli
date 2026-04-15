(ns abductcli.import
  "Raw Layer — CSV → Clojure 맵 → 정규화 → tx JSONL.
   원본 보존 우선. 스키마를 먼저 확정하지 않는다."
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [abductcli.io :as mio]))

;; ── 파싱 헬퍼 ─────────────────────────────────────

(defn parse-decimal
  "문자열 → BigDecimal. 빈 문자열이나 nil은 0M."
  [s]
  (if (or (nil? s) (str/blank? s))
    0M
    (bigdec (str/trim s))))

(defn parse-int
  "문자열 → int. 빈 문자열이나 nil은 0."
  [s]
  (if (or (nil? s) (str/blank? s))
    0
    (Integer/parseInt (str/trim s))))

;; ── CSV 파싱 ──────────────────────────────────────

(defn csv->maps
  "CSV 파일 → 맵 시퀀스. 첫 행을 헤더로 사용."
  [path]
  (with-open [reader (io/reader path)]
    (let [rows (csv/read-csv reader)
          headers (mapv str/trim (first rows))
          data (rest rows)]
      (doall
       (map (fn [row]
              (zipmap headers (mapv str/trim row)))
            data)))))

;; ── 도메인 매핑 ───────────────────────────────────

(defn normalize-row
  "Superstore 원본 행 → abductcli 도메인 맵.
   원본 필드를 :raw에 보존하면서 도메인 키를 추가."
  [row]
  (let [sales    (parse-decimal (get row "Sales"))
        profit   (parse-decimal (get row "Profit"))
        quantity (parse-int (get row "Quantity"))
        discount (parse-decimal (get row "Discount"))
        cost     (- sales profit)
        unit-price (if (pos? quantity)
                     (.divide sales (bigdec quantity) 2 java.math.RoundingMode/HALF_UP)
                     0M)
        unit-cost  (if (pos? quantity)
                     (.divide cost (bigdec quantity) 2 java.math.RoundingMode/HALF_UP)
                     0M)
        margin-rate (if (and (pos? sales) (not (zero? sales)))
                      (.divide profit sales 4 java.math.RoundingMode/HALF_UP)
                      0M)]
    {:raw row
     :product {:id           (get row "Product ID")
               :name         (get row "Product Name")
               :category     (get row "Category")
               :sub-category (get row "Sub-Category")}
     :channel {:region  (get row "Region")
               :segment (get row "Segment")}
     :order   {:id        (get row "Order ID")
               :date      (get row "Order Date")
               :ship-date (get row "Ship Date")
               :ship-mode (get row "Ship Mode")}
     :margin  {:sales       sales
               :profit      profit
               :cost        cost
               :quantity    quantity
               :discount    discount
               :unit-price  unit-price
               :unit-cost   unit-cost
               :margin-rate margin-rate}}))

;; ── 임포트 ────────────────────────────────────────

(defn import-csv
  "CSV 파일 임포트 → 정규화된 맵 벡터 + 요약."
  [path]
  (let [raw-rows (csv->maps path)
        normalized (mapv normalize-row raw-rows)
        total-sales (reduce + 0M (map #(get-in % [:margin :sales]) normalized))
        total-profit (reduce + 0M (map #(get-in % [:margin :profit]) normalized))
        categories (distinct (map #(get-in % [:product :category]) normalized))
        regions (distinct (map #(get-in % [:channel :region]) normalized))
        segments (distinct (map #(get-in % [:channel :segment]) normalized))
        by-category (group-by #(get-in % [:product :category]) normalized)]
    {:rows normalized
     :summary {:total-rows    (count normalized)
               :total-sales   total-sales
               :total-profit  total-profit
               :margin-rate   (if (pos? total-sales)
                                (.divide total-profit total-sales 4 java.math.RoundingMode/HALF_UP)
                                0M)
               :categories    (vec categories)
               :regions       (vec regions)
               :segments      (vec segments)
               :by-category   (into {}
                                (map (fn [[cat rows]]
                                       (let [cat-sales (reduce + 0M (map #(get-in % [:margin :sales]) rows))
                                             cat-profit (reduce + 0M (map #(get-in % [:margin :profit]) rows))]
                                         [cat {:rows   (count rows)
                                               :sales  cat-sales
                                               :profit cat-profit
                                               :margin-rate (if (pos? cat-sales)
                                                              (.divide cat-profit cat-sales 4 java.math.RoundingMode/HALF_UP)
                                                              0M)}]))
                                     by-category))}}))

;; ── 출력 ──────────────────────────────────────────

(defn print-summary
  "임포트 요약 출력."
  [{:keys [summary]}]
  (println "abductcli — import 완료")
  (println)
  (println (str "  총 행:     " (:total-rows summary)))
  (println (str "  총 매출:   " (:total-sales summary)))
  (println (str "  총 이익:   " (:total-profit summary)))
  (println (str "  마진율:    " (.multiply (:margin-rate summary) 100M) "%"))
  (println (str "  카테고리:  " (str/join ", " (:categories summary))))
  (println (str "  지역:      " (str/join ", " (:regions summary))))
  (println (str "  고객유형:  " (str/join ", " (:segments summary))))
  (println)
  (println "  카테고리별:")
  (doseq [[cat info] (sort-by key (:by-category summary))]
    (println (str "    " cat
                  "  행:" (:rows info)
                  "  매출:" (:sales info)
                  "  이익:" (:profit info)
                  "  마진:" (.multiply (:margin-rate info) 100M) "%"))))

;; ── 집계 (Ingest) ─────────────────────────────────

(defn- aggregate-group
  "행 그룹의 집계 지표를 계산."
  [rows]
  (let [sales  (reduce + 0M (map #(get-in % [:margin :sales]) rows))
        profit (reduce + 0M (map #(get-in % [:margin :profit]) rows))
        cost   (- sales profit)
        n      (count rows)
        avg-discount (if (pos? n)
                       (.divide (reduce + 0M (map #(get-in % [:margin :discount]) rows))
                                (bigdec n) 4 java.math.RoundingMode/HALF_UP)
                       0M)
        margin-rate (if (pos? sales)
                      (.divide profit sales 4 java.math.RoundingMode/HALF_UP)
                      0M)]
    {:sales       (double sales)
     :profit      (double profit)
     :cost        (double cost)
     :rows        n
     :avg-discount (double avg-discount)
     :margin-rate  (double margin-rate)}))

(defn aggregate-by
  "정규화된 행을 grain 기준으로 집계.
   grain: :category, :sub-category, :region"
  [normalized-rows grain]
  (let [key-fn (case grain
                 :category     #(get-in % [:product :category])
                 :sub-category #(get-in % [:product :sub-category])
                 :region       #(get-in % [:channel :region]))]
    (->> (group-by key-fn normalized-rows)
         (map (fn [[entity rows]]
                {:entity  entity
                 :grain   (name grain)
                 :metrics (aggregate-group rows)})))))

(defn add-baseline-delta
  "집계 결과에 baseline(전체 평균)과 delta(편차) 추가."
  [aggregated metric-key]
  (let [values (mapv #(get-in % [:metrics metric-key]) aggregated)
        n      (count values)
        baseline (if (pos? n) (/ (reduce + 0.0 values) n) 0.0)]
    (mapv (fn [agg]
            (let [v (get-in agg [:metrics metric-key])]
              (assoc agg
                     :baseline baseline
                     :delta    (- v baseline))))
          aggregated)))

(defn rows->tx-records
  "정규화된 행 → tx JSONL 레코드. metric별로 생성."
  [normalized-rows source grain]
  (let [aggregated (aggregate-by normalized-rows grain)
        with-bd    (add-baseline-delta aggregated :margin-rate)
        time-range (let [dates (keep #(get-in % [:order :date]) normalized-rows)]
                     (if (seq dates)
                       (let [sorted (sort dates)]
                         (str (first sorted) "/" (last sorted)))
                       "unknown"))]
    (vec
     (map-indexed
      (fn [idx agg]
        {:id       (str "tx-" (name grain) "-" (format "%03d" (inc idx)))
         :type     "tx"
         :entity   (:entity agg)
         :metric   "margin-rate"
         :value    (get-in agg [:metrics :margin-rate])
         :time     time-range
         :grain    (name grain)
         :source   source
         :baseline (:baseline agg)
         :delta    (:delta agg)
         :detail   (:metrics agg)})
      with-bd))))

(defn ingest
  "CSV → 정규화 → 집계 → tx JSONL 내보내기.
   returns {:rows, :summary, :tx-records}"
  [csv-path]
  (let [result (import-csv csv-path)
        rows   (:rows result)
        cat-tx (rows->tx-records rows csv-path :category)
        sub-tx (rows->tx-records rows csv-path :sub-category)
        all-tx (into cat-tx sub-tx)]
    (mio/write-jsonl "data/transactions.jsonl" all-tx)
    (assoc result :tx-records all-tx)))
