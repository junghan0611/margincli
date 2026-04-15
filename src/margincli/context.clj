(ns margincli.context
  "맥락 데이터 등록 — event/indicator 관리.
   register-context는 적재, suggest/attach-signal은 승격."
  (:require [margincli.io :as mio]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def contexts-path "data/contexts.jsonl")

;; ── 등록 ──────────────────────────────────────────

(defn register-context
  "단건 event/indicator 등록 → contexts.jsonl에 append.
   필수: :ctx-type, :domain, :time-start, :title, :source"
  [ctx-map]
  (let [id (mio/next-id "ctx" contexts-path)
        record (assoc ctx-map :id id)]
    (mio/append-jsonl contexts-path record)
    record))

(defn import-pack
  "EDN 팩 파일에서 맥락 일괄 등록.
   팩 형식: {:name \"..\" :desc \"..\" :contexts [...]}"
  [pack-path]
  (let [pack (edn/read-string (slurp pack-path))
        records (mapv register-context (:contexts pack))]
    {:imported  (count records)
     :pack-name (:name pack)
     :ids       (mapv :id records)}))

;; ── 조회 ──────────────────────────────────────────

(defn list-contexts
  "등록된 모든 맥락 데이터."
  []
  (mio/read-jsonl contexts-path))

(defn find-by-window
  "time-start 기준으로 윈도우 안의 맥락 검색.
   date: 기준 날짜 문자열 (YYYY-MM-DD)
   window-days: 전후 일수"
  [date window-days]
  (let [base (java.time.LocalDate/parse date)
        start (.minusDays base window-days)
        end   (.plusDays base window-days)
        ctxs  (list-contexts)]
    (filter (fn [ctx]
              (when-let [ts (:time-start ctx)]
                (try
                  (let [ctx-date (java.time.LocalDate/parse (subs ts 0 (min 10 (count ts))))]
                    (and (not (.isBefore ctx-date start))
                         (not (.isAfter ctx-date end))))
                  (catch Exception _ false))))
            ctxs)))

(defn find-by-domain
  "도메인으로 맥락 필터링."
  [domain]
  (filter #(= domain (:domain %)) (list-contexts)))

;; ── 출력 ──────────────────────────────────────────

(defn print-contexts
  "맥락 목록 출력."
  [ctxs]
  (if (empty? ctxs)
    (println "  (등록된 맥락 없음)")
    (doseq [ctx ctxs]
      (println (str "  " (:id ctx)
                    "  [" (:ctx-type ctx) "]"
                    "  " (:domain ctx)
                    "  " (:time-start ctx)
                    (when (:time-end ctx) (str "~" (:time-end ctx)))
                    "  " (:title ctx))))))
