(ns margincli.io
  "JSONL 파일 입출력 — append-only 저장.
   모든 레코드는 JSONL 한 줄 단위."
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn read-jsonl
  "JSONL 파일 → 맵 벡터. 없으면 빈 벡터."
  [path]
  (if (.exists (io/file path))
    (with-open [rdr (io/reader path)]
      (vec (keep (fn [line]
                   (when-not (str/blank? line)
                     (json/parse-string line true)))
                 (line-seq rdr))))
    []))

(defn write-jsonl
  "맵 벡터 → JSONL 파일 (덮어쓰기)."
  [path records]
  (io/make-parents path)
  (with-open [wrt (io/writer path)]
    (doseq [r records]
      (.write wrt (str (json/generate-string r) "\n")))))

(defn append-jsonl
  "맵 1건 → JSONL 파일에 추가."
  [path record]
  (io/make-parents path)
  (spit path (str (json/generate-string record) "\n") :append true))

(defn next-id
  "JSONL 파일에서 다음 ID 생성. prefix-NNN 형식."
  [prefix path]
  (let [records (read-jsonl path)
        pat (re-pattern (str "^" (java.util.regex.Pattern/quote prefix) "-(\\d+)$"))
        extract (fn [r]
                  (when-let [id (:id r)]
                    (when-let [m (re-matches pat (str id))]
                      (parse-long (second m)))))
        max-num (reduce max 0 (keep extract records))]
    (str prefix "-" (format "%03d" (inc max-num)))))

(defn read-config
  "config.edn 로드."
  []
  (-> (slurp "data/config.edn")
      (clojure.edn/read-string)))

(defn now-iso
  "현재 시각 ISO 문자열."
  []
  (str (java.time.LocalDateTime/now)))
