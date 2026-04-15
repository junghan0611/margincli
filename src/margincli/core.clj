(ns margincli.core
  "margincli — 양적추론 판단 엔진 CLI.
   anomaly → candidate signals → memo → evaluation"
  (:require [margincli.import :as imp]
            [margincli.engine :as eng]
            [margincli.io :as mio]
            [margincli.context :as ctx]
            [margincli.anomaly :as anom]
            [margincli.signal :as sig]
            [margincli.memo :as memo]
            [margincli.export :as export]
            [clojure.string :as str])
  (:gen-class))

;; ── 옵션 파싱 ─────────────────────────────────────

(defn- parse-opts
  "CLI 옵션 파싱. --key value 쌍 → {:key value} 맵."
  [args]
  (into {}
    (map (fn [[k v]] [(keyword (str/replace k #"^--" "")) v])
         (partition 2 (filter #(or (str/starts-with? % "--")
                                   (not (str/starts-with? % "--")))
                              args)))))

(defn- get-opt [opts k]
  (get opts (keyword k)))

;; ── 커맨드: import ────────────────────────────────

(defn cmd-import [args]
  (let [path (first args)]
    (if (nil? path)
      (println "Usage: margincli import <csv-path>")
      (if-not (.exists (java.io.File. path))
        (println (str "파일 없음: " path))
        (let [result (imp/ingest path)]
          (imp/print-summary result)
          (println)
          (println (str "  → data/transactions.jsonl 생성 ("
                        (count (:tx-records result)) "건)")))))))

;; ── 커맨드: calc ──────────────────────────────────

(defn cmd-calc [args]
  (let [opts       (parse-opts args)
        price      (some-> (get-opt opts "price") bigdec)
        cost       (some-> (get-opt opts "cost") bigdec)
        commission (some-> (get-opt opts "commission") bigdec)]
    (if (or (nil? price) (nil? cost))
      (do (println "Usage: margincli calc --price P --cost C [--commission R]")
          (println "  예: margincli calc --price 29900 --cost 15000 --commission 0.12"))
      (let [comm-rate (or commission 0M)
            result    (eng/calc-margin price cost comm-rate)]
        (println "margincli — 마진 계산")
        (println)
        (println (str "  판매가:   " (:sale-price result)))
        (println (str "  원가:     " (:cost result)))
        (println (str "  수수료:   " (:commission result)
                      " (" (.multiply comm-rate 100M) "%)"))
        (println (str "  공헌이익: " (:contribution result)))
        (println (str "  마진율:   " (.multiply (:margin-rate result) 100M) "%"))))))

;; ── 커맨드: reverse ───────────────────────────────

(defn cmd-reverse [args]
  (let [opts       (parse-opts args)
        target     (some-> (get-opt opts "target") bigdec)
        cost       (some-> (get-opt opts "cost") bigdec)
        commission (some-> (get-opt opts "commission") bigdec)]
    (if (or (nil? target) (nil? cost))
      (do (println "Usage: margincli reverse --target T --cost C [--commission R]")
          (println "  예: margincli reverse --target 0.25 --cost 15000 --commission 0.12"))
      (let [comm-rate (or commission 0M)
            result    (eng/reverse-margin target cost comm-rate)]
        (if (:error result)
          (println (str "오류: " (:error result)))
          (do
            (println "margincli — 역산")
            (println)
            (println (str "  목표 마진율: " (.multiply target 100M) "%"))
            (println (str "  원가:       " cost))
            (println (str "  수수료율:   " (.multiply comm-rate 100M) "%"))
            (println (str "  → 최소 판매가: " (:min-sale-price result)))
            (println)
            (println "  검증:")
            (let [v (:verify result)]
              (println (str "    공헌이익: " (:contribution v)))
              (println (str "    마진율:   " (.multiply (:margin-rate v) 100M) "%")))))))))

;; ── 커맨드: detect ────────────────────────────────

(defn cmd-detect [args]
  (let [opts   (parse-opts args)
        grain  (or (get-opt opts "grain") "category")
        config (mio/read-config)
        txs    (mio/read-jsonl "data/transactions.jsonl")]
    (if (empty? txs)
      (println "data/transactions.jsonl 없음. 먼저 import를 실행하세요.")
      (let [filtered  (filter #(= grain (:grain %)) txs)
            anomalies (anom/detect-and-save! filtered config)]
        (println (str "margincli — anomaly 탐지 (grain=" grain ")"))
        (println)
        (if (empty? anomalies)
          (println "  탐지된 anomaly 없음")
          (do
            (println (str "  탐지: " (count anomalies) "건"))
            (println)
            (anom/print-anomalies anomalies)))))))

;; ── 커맨드: register-context ──────────────────────

(defn cmd-register-context [args]
  (let [opts (parse-opts args)
        ctx-type   (get-opt opts "type")
        title      (get-opt opts "title")
        date       (get-opt opts "date")
        domain     (get-opt opts "domain")
        date-end   (get-opt opts "date-end")
        source     (or (get-opt opts "source") "manual")
        confidence (or (get-opt opts "confidence") "confirmed")]
    (if (or (nil? ctx-type) (nil? title) (nil? date) (nil? domain))
      (do (println "Usage: margincli register-context --type event --title \"...\" --date YYYY-MM-DD --domain retail")
          (println "  옵션: --date-end, --source, --confidence"))
      (let [record (ctx/register-context
                    {:ctx-type   ctx-type
                     :domain     domain
                     :time-start date
                     :time-end   date-end
                     :title      title
                     :source     source
                     :confidence confidence})]
        (println (str "margincli — context 등록: " (:id record)))
        (println (str "  " (:title record)))))))

;; ── 커맨드: import-context ────────────────────────

(defn cmd-import-context [args]
  (let [opts (parse-opts args)
        pack (get-opt opts "pack")]
    (if (nil? pack)
      (println "Usage: margincli import-context --pack data/packs/calendar-sample.edn")
      (if-not (.exists (java.io.File. pack))
        (println (str "파일 없음: " pack))
        (let [result (ctx/import-pack pack)]
          (println (str "margincli — context 팩 임포트: " (:pack-name result)))
          (println (str "  등록: " (:imported result) "건")))))))

;; ── 커맨드: list-contexts ─────────────────────────

(defn cmd-list-contexts [_args]
  (println "margincli — 등록된 맥락 데이터")
  (println)
  (ctx/print-contexts (ctx/list-contexts)))

;; ── 커맨드: suggest-signals ───────────────────────

(defn cmd-suggest-signals [args]
  (let [opts (parse-opts args)
        anomaly-id (get-opt opts "anomaly")]
    (if (nil? anomaly-id)
      (println "Usage: margincli suggest-signals --anomaly <id>")
      (let [config (mio/read-config)
            result (sig/suggest anomaly-id config)]
        (sig/print-candidates result)))))

;; ── 커맨드: attach-signal ─────────────────────────

(defn cmd-attach-signal [args]
  (let [opts (parse-opts args)
        anomaly-id (get-opt opts "anomaly")
        ctx-id     (get-opt opts "ctx")]
    (if (or (nil? anomaly-id) (nil? ctx-id))
      (println "Usage: margincli attach-signal --anomaly <id> --ctx <id>")
      (let [signal (sig/attach anomaly-id ctx-id)]
        (println (str "margincli — signal 연결: " (:id signal)))
        (println (str "  " (:title signal) " → " anomaly-id))))))

;; ── 커맨드: export ────────────────────────────────

(defn cmd-export [args]
  (let [opts (parse-opts args)
        fmt  (or (get-opt opts "format") "compact")
        out  (get-opt opts "out")]
    (case fmt
      "raw"     (let [path (or out "data/export-raw.jsonl")
                      r    (export/export-raw path)]
                  (println (str "margincli — raw export: " (:exported r) "건 → " path)))
      "compact" (let [path (or out "data/export-compact.jsonl")
                      r    (export/export-compact path)]
                  (println (str "margincli — compact export: " (:exported r) "건 → " path)))
      "scenario" (let [anom-id (get-opt opts "anomaly")
                       path    (or out (str "data/scenario-" anom-id ".edn"))]
                   (if (nil? anom-id)
                     (println "Usage: margincli export --format scenario --anomaly <id>")
                     (do (export/export-scenario anom-id path)
                         (println (str "margincli — scenario bundle → " path)))))
      (println (str "알 수 없는 형식: " fmt ". raw | compact | scenario")))))

;; ── 커맨드: write-memo ────────────────────────────

(defn cmd-write-memo [args]
  (let [opts (parse-opts args)
        anomaly-id (get-opt opts "anomaly")
        hypothesis (get-opt opts "hypothesis")
        evidence   (get-opt opts "evidence")
        direction  (or (get-opt opts "direction") "recover")
        window     (get-opt opts "window")
        author     (or (get-opt opts "author") "human")]
    (if (or (nil? anomaly-id) (nil? hypothesis) (nil? evidence))
      (do (println "Usage: margincli write-memo --anomaly <id> --hypothesis \"...\" --evidence \"sig-001\"")
          (println "  옵션: --direction recover|worsen|stable --window \"...\" --author human|agent:claude"))
      (let [evidence-vec (if (str/starts-with? evidence "[")
                           (read-string evidence)
                           (str/split evidence #","))
            m (memo/write-memo {:subject            anomaly-id
                                :hypothesis         hypothesis
                                :evidence           evidence-vec
                                :expected-direction direction
                                :prediction-window  window
                                :author             author})]
        (println (str "margincli — memo 생성: " (:id m)))
        (memo/print-memo m)))))

;; ── 커맨드: backtest ──────────────────────────────

(defn cmd-backtest [args]
  (let [opts    (parse-opts args)
        memo-id (get-opt opts "memo")
        dir     (or (get-opt opts "direction") "recover")
        timing  (not= "false" (or (get-opt opts "timing") "true"))
        mag     (Double/parseDouble (or (get-opt opts "magnitude") "0.5"))]
    (if (nil? memo-id)
      (do (println "Usage: margincli backtest --memo <id> --direction recover --timing true --magnitude 0.7")
          (println "  실제 결과를 입력하여 memo를 사후 검증"))
      (let [e (memo/backtest memo-id {:direction dir
                                      :timing    timing
                                      :magnitude mag})]
        (println (str "margincli — backtest 평가"))
        (println)
        (memo/print-evaluation e)))))

;; ── 메인 ──────────────────────────────────────────

(defn -main [& args]
  (let [cmd       (first args)
        rest-args (rest args)]
    (case cmd
      "import"           (cmd-import rest-args)
      "calc"             (cmd-calc rest-args)
      "reverse"          (cmd-reverse rest-args)
      "detect"           (cmd-detect rest-args)
      "register-context" (cmd-register-context rest-args)
      "import-context"   (cmd-import-context rest-args)
      "list-contexts"    (cmd-list-contexts rest-args)
      "suggest-signals"  (cmd-suggest-signals rest-args)
      "attach-signal"    (cmd-attach-signal rest-args)
      "export"           (cmd-export rest-args)
      "write-memo"       (cmd-write-memo rest-args)
      "backtest"         (cmd-backtest rest-args)
      ;; 도움말
      (do
        (println "margincli — 양적추론 판단 엔진 CLI")
        (println)
        (println "Pipeline: anomaly → candidate signals → memo → evaluation")
        (println)
        (println "Data:")
        (println "  import <csv>                               CSV 가져오기 + tx JSONL 생성")
        (println "  calc --price P --cost C [--commission R]   마진 계산")
        (println "  reverse --target T --cost C [--commission R]  역산")
        (println)
        (println "Context:")
        (println "  register-context --type T --title \"...\" --date D --domain D")
        (println "  import-context --pack <file.edn>           맥락팩 일괄 등록")
        (println "  list-contexts                              등록된 맥락 목록")
        (println)
        (println "Analysis:")
        (println "  detect [--grain category|sub-category|region]  변동점 탐지")
        (println "  suggest-signals --anomaly <id>             signal 후보 검색")
        (println "  attach-signal --anomaly <id> --ctx <id>    signal 승격")
        (println)
        (println "Judgment:")
        (println "  write-memo --anomaly <id> --hypothesis \"...\" --evidence \"sig-001\"")
        (println "  backtest --memo <id> --direction D --timing T --magnitude M")
        (println)
        (println "Export:")
        (println "  export --format raw|compact|scenario [--anomaly <id>]")))))
