(ns abductcli.signal
  "Signal 선택 — context에서 anomaly 설명 후보를 찾고 승격.
   register-context는 적재, suggest/attach-signal은 승격."
  (:require [abductcli.io :as mio]
            [abductcli.anomaly :as anom]
            [abductcli.context :as ctx]))

(def signals-path "data/signals.jsonl")

;; ── Relevance 점수 ────────────────────────────────

(defn- domain-weight
  "도메인 가중치. config 기반. 0.0~1.0"
  [domain config]
  (let [default (get-in config [:signal :default-domain-weight] 0.5)]
    (get-in config [:signal :domain-weights (keyword domain)] default)))

(defn- time-range-score
  "context 시간 범위 점수. 범위 있으면 높음."
  [ctx-record]
  (if (:time-end ctx-record) 0.8 0.5))

(defn- source-score
  "출처 유형 점수."
  [source]
  (case source
    "internal"        0.9
    "industry-report" 0.7
    "curated"         0.6
    "FRED"            0.5
    "calendar"        0.4
    0.3))

(defn- entity-match-score
  "anomaly 엔티티와 context 제목/도메인의 키워드 매칭.
   1) 하드코딩 매핑 체크 (Superstore demo용)
   2) entity 이름에서 토큰 추출 → context 제목에서 검색
   3) anomaly domain과 context domain 일치 체크"
  [anomaly-entity anomaly-domain ctx-record]
  (let [title (str (:title ctx-record) " " (:domain ctx-record))
        title-lower (clojure.string/lower-case title)
        entity-lower (clojure.string/lower-case (or anomaly-entity ""))
        ctx-domain (clojure.string/lower-case (or (:domain ctx-record) ""))
        anom-domain (clojure.string/lower-case (or anomaly-domain ""))
        ;; 하드코딩 매핑 (Superstore demo backward compat)
        entity-keywords {"furniture" ["furniture" "가구" "table" "chair" "desk" "bookcase"]
                         "technology" ["technology" "tech" "전자" "기기" "phone" "computer"]
                         "office supplies" ["office" "사무" "용품" "supply" "paper" "binder"]}
        hardcoded (get entity-keywords entity-lower nil)]
    (cond
      ;; 하드코딩 매핑 히트
      (and hardcoded (some #(clojure.string/includes? title-lower %) hardcoded))
      0.9

      ;; entity 토큰이 context 제목에 등장 (e.g. "banana" in "banana production")
      (let [tokens (clojure.string/split entity-lower #"[-_\s]+")]
        (some #(and (> (count %) 2)
                    (clojure.string/includes? title-lower %))
              tokens))
      0.85

      ;; anomaly domain과 context domain 일치
      (and (seq anom-domain) (= anom-domain ctx-domain))
      0.6

      :else 0.2)))

(defn calc-relevance
  "context 1건의 relevance 점수 (0~1).
   4개 축의 가중 평균: domain 30% + time 20% + source 20% + entity 30%."
  [ctx-record anomaly-entity anomaly-domain config]
  (let [d (domain-weight (:domain ctx-record) config)
        t (time-range-score ctx-record)
        s (source-score (:source ctx-record))
        e (entity-match-score anomaly-entity anomaly-domain ctx-record)]
    (+ (* 0.30 d)
       (* 0.20 t)
       (* 0.20 s)
       (* 0.30 e))))

;; ── 후보 검색 ─────────────────────────────────────

(defn suggest
  "anomaly에 대한 signal 후보 검색.
   전체 context를 relevance(domain/entity/time/source) 기준으로 정렬.
   TODO: time-window 실제 필터링 미적용 — 현재는 정렬만."
  [anomaly-id config]
  (let [anomaly   (anom/find-anomaly anomaly-id)
        _         (when-not anomaly
                    (throw (ex-info (str "anomaly 없음: " anomaly-id) {})))
        entity    (:entity anomaly)
        domain    (:domain anomaly)
        max-cands (get-in config [:signal :max-candidates] 20)
        all-ctxs  (ctx/list-contexts)
        scored    (->> all-ctxs
                       (map (fn [c]
                              {:ctx-ref   (:id c)
                               :ctx       c
                               :relevance (calc-relevance c entity domain config)}))
                       (sort-by :relevance >)
                       (take max-cands))]
    {:anomaly-id anomaly-id
     :entity     entity
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
        relevance (calc-relevance ctx (:entity anomaly) (:domain anomaly) config)
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
