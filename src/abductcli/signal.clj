(ns abductcli.signal
  "Signal 선택 — context에서 anomaly 설명 후보를 찾고 승격.
   register-context는 적재, suggest/attach-signal은 승격."
  (:require [abductcli.io :as mio]
            [abductcli.anomaly :as anom]
            [abductcli.context :as ctx]))

(def signals-path "data/signals.jsonl")

;; ── Relevance 점수 ────────────────────────────────

(defn- domain-weight
  "도메인 가중치. config 기반."
  [domain config]
  (get-in config [:signal :domain-weights (keyword domain)] 0.3))

(defn- time-overlap-score
  "anomaly 시간 범위와 context 시간 범위의 겹침 점수."
  [ctx-record]
  (if (:time-end ctx-record) 0.7 0.4))

(defn- source-score
  "출처 유형 가산점."
  [source]
  (cond
    (= source "internal") 0.15
    (= source "curated")  0.05
    :else                  0.0))

(defn calc-relevance
  "context 1건의 relevance 점수 (0~1)."
  [ctx-record config]
  (min 1.0
       (+ (domain-weight (:domain ctx-record) config)
          (time-overlap-score ctx-record)
          (source-score (:source ctx-record)))))

;; ── 후보 검색 ─────────────────────────────────────

(defn suggest
  "anomaly에 대한 signal 후보 검색.
   1차: time-window, 2차: domain/relevance 정렬."
  [anomaly-id config]
  (let [anomaly   (anom/find-anomaly anomaly-id)
        _         (when-not anomaly
                    (throw (ex-info (str "anomaly 없음: " anomaly-id) {})))
        ;; tx의 time에서 날짜 범위 추출 (YYYY-MM-DD/YYYY-MM-DD 또는 단일)
        time-str  (or (:time anomaly) "")
        windows   (get-in config [:signal :time-windows] [7 30 90])
        max-cands (get-in config [:signal :max-candidates] 20)
        ;; 모든 context 가져오기 (시간 필터링은 범위가 넓으므로 전체 조회)
        all-ctxs  (ctx/list-contexts)
        scored    (->> all-ctxs
                       (map (fn [c]
                              {:ctx-ref   (:id c)
                               :ctx       c
                               :relevance (calc-relevance c config)}))
                       (sort-by :relevance >)
                       (take max-cands))]
    {:anomaly-id anomaly-id
     :entity     (:entity anomaly)
     :candidates scored}))

;; ── 승격 (attach) ─────────────────────────────────

(defn attach
  "context를 signal로 승격 — anomaly에 연결."
  [anomaly-id ctx-id & {:keys [note]}]
  (let [config    (mio/read-config)
        anomaly   (anom/find-anomaly anomaly-id)
        _         (when-not anomaly
                    (throw (ex-info (str "anomaly 없음: " anomaly-id) {})))
        ctx       (first (filter #(= ctx-id (:id %)) (ctx/list-contexts)))
        _         (when-not ctx
                    (throw (ex-info (str "context 없음: " ctx-id) {})))
        relevance (calc-relevance ctx config)
        signal    {:id         (mio/next-id "sig" signals-path)
                   :type       "signal"
                   :ctx-ref    ctx-id
                   :anomaly-id anomaly-id
                   :relevance  relevance
                   :status     "attached"
                   :title      (:title ctx)
                   :domain     (:domain ctx)
                   :note       note}]
    (mio/append-jsonl signals-path signal)
    signal))

;; ── 조회 ──────────────────────────────────────────

(defn list-signals []
  (mio/read-jsonl signals-path))

(defn find-by-anomaly [anomaly-id]
  (filter #(= anomaly-id (:anomaly-id %)) (list-signals)))

;; ── 출력 ──────────────────────────────────────────

(defn print-candidates
  "후보 목록 출력."
  [{:keys [anomaly-id entity candidates]}]
  (println (str "abductcli — signal 후보 (" anomaly-id " / " entity ")"))
  (println)
  (doseq [{:keys [ctx-ref ctx relevance]} candidates]
    (println (str "  " ctx-ref
                  "  rel=" (format "%.2f" relevance)
                  "  [" (:ctx-type ctx) "]"
                  "  " (:domain ctx)
                  "  " (:title ctx)))))

(defn print-signals
  [signals]
  (if (empty? signals)
    (println "  (signal 없음)")
    (doseq [s signals]
      (println (str "  " (:id s)
                    "  → " (:anomaly-id s)
                    "  ctx=" (:ctx-ref s)
                    "  rel=" (format "%.2f" (double (:relevance s)))
                    "  [" (:status s) "]"
                    "  " (:title s))))))
