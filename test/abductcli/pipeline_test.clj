(ns abductcli.pipeline-test
  "Furniture -14.68% 시나리오 — 파이프라인 한 바퀴 통합 테스트."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [abductcli.import :as imp]
            [abductcli.io :as mio]
            [abductcli.context :as ctx]
            [abductcli.anomaly :as anom]
            [abductcli.signal :as sig]
            [abductcli.memo :as memo]
            [clojure.java.io :as io]))

;; ── 테스트 픽스처: 생성된 JSONL 정리 ──────────────

(defn clean-data [f]
  (doseq [path ["data/transactions.jsonl"
                 "data/contexts.jsonl"
                 "data/anomalies.jsonl"
                 "data/signals.jsonl"
                 "data/memos.jsonl"
                 "data/evaluations.jsonl"]]
    (let [file (io/file path)]
      (when (.exists file) (.delete file))))
  (f))

(use-fixtures :each clean-data)

;; ── 1단계: Ingest ─────────────────────────────────

(deftest ingest-test
  (testing "CSV → tx JSONL"
    (let [result (imp/ingest "data/superstore.csv")
          txs    (:tx-records result)]
      (is (pos? (count txs)))
      ;; category grain에 Furniture 존재
      (is (some #(and (= "Furniture" (:entity %))
                      (= "category" (:grain %)))
                txs))
      ;; transactions.jsonl 파일 생성됨
      (is (.exists (io/file "data/transactions.jsonl"))))))

;; ── 2단계: Context 등록 ──────────────────────────

(deftest context-pack-test
  (testing "맥락팩 임포트"
    (let [result (ctx/import-pack "data/packs/calendar-sample.edn")]
      (is (= 10 (:imported result)))
      (is (= 10 (count (ctx/list-contexts)))))))

;; ── 3단계: Anomaly 탐지 ──────────────────────────

(deftest anomaly-detect-test
  (testing "Furniture anomaly 탐지"
    (imp/ingest "data/superstore.csv")
    (let [config (mio/read-config)
          txs    (filter #(= "category" (:grain %))
                         (mio/read-jsonl "data/transactions.jsonl"))
          anoms  (anom/detect-and-save! txs config)]
      ;; Furniture가 high severity로 탐지
      (is (some #(and (= "Furniture" (:entity %))
                      (= "high" (:severity %)))
                anoms)))))

;; ── 4단계: Signal 연결 ───────────────────────────

(deftest signal-attach-test
  (testing "context → signal 승격"
    ;; 준비
    (imp/ingest "data/superstore.csv")
    (ctx/import-pack "data/packs/calendar-sample.edn")
    (let [config (mio/read-config)
          txs    (filter #(= "category" (:grain %))
                         (mio/read-jsonl "data/transactions.jsonl"))
          anoms  (anom/detect-and-save! txs config)
          anom-id (:id (first (filter #(= "Furniture" (:entity %)) anoms)))
          ;; signal 후보 검색
          result (sig/suggest anom-id config)]
      (is (pos? (count (:candidates result))))
      ;; 첫 번째 후보를 attach
      (let [best-ctx (:ctx-ref (first (:candidates result)))
            signal   (sig/attach anom-id best-ctx)]
        (is (= "attached" (:status signal)))
        (is (= anom-id (:anomaly-id signal)))))))

;; ── 5~6단계: Memo + Backtest ─────────────────────

(deftest memo-backtest-test
  (testing "memo 생성 + backtest 평가"
    ;; 풀 파이프라인
    (imp/ingest "data/superstore.csv")
    (ctx/import-pack "data/packs/calendar-sample.edn")
    (let [config  (mio/read-config)
          txs     (filter #(= "category" (:grain %))
                          (mio/read-jsonl "data/transactions.jsonl"))
          anoms   (anom/detect-and-save! txs config)
          anom-id (:id (first (filter #(= "Furniture" (:entity %)) anoms)))
          ;; signal 연결
          ctxs    (:candidates (sig/suggest anom-id config))
          signal  (sig/attach anom-id (:ctx-ref (first ctxs)))
          ;; memo 작성
          m       (memo/write-memo
                   {:subject            anom-id
                    :hypothesis         "Tables 과도한 할인(평균 45%)이 Furniture 마진을 음수로 끌어내림"
                    :evidence           [(:id signal)]
                    :expected-direction "recover"
                    :prediction-window  "다음 분기"
                    :author             "agent:claude"})
          ;; backtest
          e       (memo/backtest (:id m) {:direction "recover"
                                          :timing    true
                                          :magnitude 0.7})]
      ;; memo 생성 확인
      (is (= "proposed" (:status m)))
      (is (= anom-id (:subject m)))
      ;; evaluation 확인
      (is (:direction-match e))
      (is (:timing-match e))
      (is (= 0.7 (:magnitude-score e)))
      ;; composite score: 0.5*1 + 0.25*1 + 0.25*0.7 = 0.925 → 93
      (is (= 93 (:composite-score e))))))
