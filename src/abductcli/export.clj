(ns abductcli.export
  "AI Layer — JSONL 내보내기 3종.
   raw: 기계용 원본 / compact: LLM 읽기용 / scenario: anomaly 묶음"
  (:require [abductcli.io :as mio]
            [abductcli.anomaly :as anom]
            [abductcli.signal :as sig]
            [cheshire.core :as json]))

;; ── raw JSONL ─────────────────────────────────────

(defn export-raw
  "모든 레코드를 타입별로 raw JSONL로 내보내기."
  [out-path]
  (let [txs   (mio/read-jsonl "data/transactions.jsonl")
        ctxs  (mio/read-jsonl "data/contexts.jsonl")
        anoms (mio/read-jsonl "data/anomalies.jsonl")
        sigs  (mio/read-jsonl "data/signals.jsonl")
        memos (mio/read-jsonl "data/memos.jsonl")
        evals (mio/read-jsonl "data/evaluations.jsonl")
        all   (concat txs ctxs anoms sigs memos evals)]
    (mio/write-jsonl out-path all)
    {:exported (count all) :path out-path}))

;; ── compact JSONL ─────────────────────────────────

(defn- tx->compact [tx]
  {:type   "indicator"
   :name   (:metric tx)
   :entity (:entity tx)
   :value  (:value tx)
   :grain  (:grain tx)
   :time   (:time tx)
   :delta  (:delta tx)})

(defn- anomaly->compact [a]
  {:type     "anomaly"
   :entity   (:entity a)
   :metric   (:metric a)
   :value    (:value a)
   :severity (:severity a)
   :score    (get-in a [:detail :robust-z])})

(defn- signal->compact [s]
  {:type      "signal"
   :title     (:title s)
   :domain    (:domain s)
   :relevance (:relevance s)
   :anomaly   (:anomaly-id s)})

(defn- memo->compact [m]
  {:type       "memo"
   :hypothesis (:hypothesis m)
   :evidence   (:evidence m)
   :direction  (:expected-direction m)
   :author     (:author m)
   :status     (:status m)})

(defn export-compact
  "LLM 읽기용 compact JSONL. 0.3~1.5KB/줄."
  [out-path]
  (let [txs   (map tx->compact (mio/read-jsonl "data/transactions.jsonl"))
        anoms (map anomaly->compact (mio/read-jsonl "data/anomalies.jsonl"))
        sigs  (map signal->compact (mio/read-jsonl "data/signals.jsonl"))
        memos (map memo->compact (mio/read-jsonl "data/memos.jsonl"))
        all   (concat txs anoms sigs memos)]
    (mio/write-jsonl out-path all)
    {:exported (count all) :path out-path}))

;; ── scenario bundle ───────────────────────────────

(defn export-scenario
  "anomaly 1건 + 관련 tx + signal + memo 묶음 → EDN."
  [anomaly-id out-path]
  (let [anomaly  (anom/find-anomaly anomaly-id)
        _        (when-not anomaly
                   (throw (ex-info (str "anomaly 없음: " anomaly-id) {})))
        tx       (first (filter #(= (:tx-id anomaly) (:id %))
                                (mio/read-jsonl "data/transactions.jsonl")))
        signals  (sig/find-by-anomaly anomaly-id)
        memos    (filter #(= anomaly-id (:subject %))
                         (mio/read-jsonl "data/memos.jsonl"))
        evals    (let [memo-ids (set (map :id memos))]
                   (filter #(memo-ids (:memo-id %))
                           (mio/read-jsonl "data/evaluations.jsonl")))
        bundle   {:anomaly     anomaly
                  :transaction tx
                  :signals     (vec signals)
                  :memos       (vec memos)
                  :evaluations (vec evals)}]
    (spit out-path (pr-str bundle))
    {:anomaly-id anomaly-id :path out-path}))
