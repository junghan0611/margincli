(ns abductcli.engine
  "마진 계산/역산 엔진 — BigDecimal only.
   float을 쓰지 않는다."
  (:import [java.math RoundingMode]))

(def ^:private SCALE 4)
(def ^:private ROUND RoundingMode/HALF_UP)

;; ── 마진 계산 ─────────────────────────────────────

(defn calc-margin
  "마진 계산.
   sale-price:      판매가 (BigDecimal)
   cost:            원가 (BigDecimal)
   commission-rate: 수수료율 (BigDecimal, 0~1)

   → {:sale-price, :cost, :commission, :contribution, :margin-rate}"
  [sale-price cost commission-rate]
  (let [commission (.setScale (.multiply sale-price commission-rate) 2 ROUND)
        contribution (.setScale (- sale-price cost commission) 2 ROUND)
        margin-rate (if (pos? sale-price)
                      (.divide contribution sale-price SCALE ROUND)
                      0M)]
    {:sale-price      sale-price
     :cost            cost
     :commission-rate commission-rate
     :commission      commission
     :contribution    contribution
     :margin-rate     margin-rate}))

;; ── 역산 ──────────────────────────────────────────

(defn reverse-margin
  "목표 마진율 → 최소 판매가 역산.
   target-rate:     목표 마진율 (BigDecimal, 0~1)
   cost:            원가 (BigDecimal)
   commission-rate: 수수료율 (BigDecimal, 0~1)

   공식: price = cost / (1 - commission-rate - target-rate)"
  [target-rate cost commission-rate]
  (let [denominator (- 1M commission-rate target-rate)]
    (if (<= (.compareTo denominator 0M) 0)
      {:error           "목표 마진율 + 수수료율 >= 100% — 불가능한 조건"
       :target-rate     target-rate
       :commission-rate commission-rate}
      (let [min-price (.divide cost denominator SCALE ROUND)]
        {:min-sale-price  min-price
         :target-rate     target-rate
         :cost            cost
         :commission-rate commission-rate
         :verify          (calc-margin min-price cost commission-rate)}))))

;; ── Superstore 행 → 마진 지표 ─────────────────────

(defn calc-from-row
  "import에서 정규화된 행의 마진 지표 추출.
   Superstore는 수수료 개념이 없으므로 commission = 0."
  [normalized-row]
  (let [m (:margin normalized-row)]
    {:product-id   (get-in normalized-row [:product :id])
     :product-name (get-in normalized-row [:product :name])
     :category     (get-in normalized-row [:product :category])
     :channel      (str (get-in normalized-row [:channel :region])
                        "/" (get-in normalized-row [:channel :segment]))
     :sales        (:sales m)
     :cost         (:cost m)
     :profit       (:profit m)
     :quantity     (:quantity m)
     :discount     (:discount m)
     :unit-price   (:unit-price m)
     :unit-cost    (:unit-cost m)
     :margin-rate  (:margin-rate m)}))
