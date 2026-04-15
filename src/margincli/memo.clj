(ns margincli.memo
  "판단층 — memo 생성 + backtest 평가.
   memo는 append-only. 과거의 멍청했던 판단도 지워지지 않는다."
  (:require [margincli.io :as mio]))

(def memos-path "data/memos.jsonl")
(def evaluations-path "data/evaluations.jsonl")

;; ── Memo 생성 ─────────────────────────────────────

(defn write-memo
  "memo 1건 생성 → memos.jsonl에 append.
   필수: :subject (anomaly-id), :hypothesis, :evidence (signal-id vec), :author"
  [{:keys [subject hypothesis evidence author
           expected-direction prediction-window parent-id]
    :or   {expected-direction "recover"
           author             "human"}}]
  (let [id   (mio/next-id "memo" memos-path)
        memo {:id                 id
              :type               "memo"
              :subject            subject
              :hypothesis         hypothesis
              :evidence           (if (string? evidence) [evidence] (vec evidence))
              :expected-direction expected-direction
              :prediction-window  prediction-window
              :author             author
              :status             "proposed"
              :parent-id          parent-id
              :created-at         (mio/now-iso)}]
    (mio/append-jsonl memos-path memo)
    memo))

;; ── Backtest 평가 ─────────────────────────────────

(defn backtest
  "memo의 direction/timing/magnitude를 사후 검증.
   actual: {:direction :recover|:worsen|:stable
            :timing    true|false
            :magnitude 0.0~1.0}
   → evaluation record"
  [memo-id actual]
  (let [memo    (first (filter #(= memo-id (:id %))
                               (mio/read-jsonl memos-path)))
        _       (when-not memo
                  (throw (ex-info (str "memo 없음: " memo-id) {})))
        dir-match   (= (:expected-direction memo) (:direction actual))
        timing      (:timing actual true)
        magnitude   (:magnitude actual 0.5)
        ;; 종합 점수: direction 50% + timing 25% + magnitude 25%
        score       (+ (* 0.50 (if dir-match 1.0 0.0))
                       (* 0.25 (if timing 1.0 0.0))
                       (* 0.25 magnitude))
        eval-record {:id              (mio/next-id "eval" evaluations-path)
                     :type            "evaluation"
                     :memo-id         memo-id
                     :direction-match dir-match
                     :timing-match    timing
                     :magnitude-score magnitude
                     :composite-score (Math/round (* score 100.0))
                     :evaluated-at    (mio/now-iso)
                     :detail          actual}]
    (mio/append-jsonl evaluations-path eval-record)
    eval-record))

;; ── 조회 ──────────────────────────────────────────

(defn list-memos []
  (mio/read-jsonl memos-path))

(defn list-evaluations []
  (mio/read-jsonl evaluations-path))

(defn find-memo [id]
  (first (filter #(= id (:id %)) (list-memos))))

;; ── 출력 ──────────────────────────────────────────

(defn print-memo [m]
  (println (str "  " (:id m)
                "  [" (:status m) "]"
                "  " (:subject m)))
  (println (str "    가설: " (:hypothesis m)))
  (println (str "    증거: " (pr-str (:evidence m))))
  (println (str "    방향: " (:expected-direction m)
                "  작성: " (:author m))))

(defn print-evaluation [e]
  (println (str "  " (:id e)
                "  → " (:memo-id e)
                "  score=" (:composite-score e) "/100"))
  (println (str "    방향:" (if (:direction-match e) "✓" "✗")
                "  타이밍:" (if (:timing-match e) "✓" "✗")
                "  크기:" (format "%.2f" (double (:magnitude-score e))))))
