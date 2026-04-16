(ns abductcli.core
  "abductcli — 양적추론 판단 엔진 CLI.
   anomaly → candidate signals → memo → evaluation"
  (:require [abductcli.import :as imp]
            [abductcli.engine :as eng]
            [abductcli.io :as mio]
            [abductcli.context :as ctx]
            [abductcli.anomaly :as anom]
            [abductcli.signal :as sig]
            [abductcli.memo :as memo]
            [abductcli.export :as export]
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
      (println "Usage: abductcli import <csv-path>")
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
      (do (println "Usage: abductcli calc --price P --cost C [--commission R]")
          (println "  예: abductcli calc --price 29900 --cost 15000 --commission 0.12"))
      (let [comm-rate (or commission 0M)
            result    (eng/calc-margin price cost comm-rate)]
        (println "abductcli — 마진 계산")
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
      (do (println "Usage: abductcli reverse --target T --cost C [--commission R]")
          (println "  예: abductcli reverse --target 0.25 --cost 15000 --commission 0.12"))
      (let [comm-rate (or commission 0M)
            result    (eng/reverse-margin target cost comm-rate)]
        (if (:error result)
          (println (str "오류: " (:error result)))
          (do
            (println "abductcli — 역산")
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
        (println (str "abductcli — anomaly 탐지 (grain=" grain ")"))
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
      (do (println "Usage: abductcli register-context --type event --title \"...\" --date YYYY-MM-DD --domain retail")
          (println "  옵션: --date-end, --source, --confidence"))
      (let [record (ctx/register-context
                    {:ctx-type   ctx-type
                     :domain     domain
                     :time-start date
                     :time-end   date-end
                     :title      title
                     :source     source
                     :confidence confidence})]
        (println (str "abductcli — context 등록: " (:id record)))
        (println (str "  " (:title record)))))))

;; ── 커맨드: import-context ────────────────────────

(defn cmd-import-context [args]
  (let [opts (parse-opts args)
        pack (get-opt opts "pack")]
    (if (nil? pack)
      (println "Usage: abductcli import-context --pack data/packs/calendar-sample.edn")
      (if-not (.exists (java.io.File. pack))
        (println (str "파일 없음: " pack))
        (let [result (ctx/import-pack pack)]
          (println (str "abductcli — context 팩 임포트: " (:pack-name result)))
          (println (str "  등록: " (:imported result) "건")))))))

;; ── 커맨드: list-contexts ─────────────────────────

(defn cmd-list-contexts [_args]
  (println "abductcli — 등록된 맥락 데이터")
  (println)
  (ctx/print-contexts (ctx/list-contexts)))

;; ── 커맨드: suggest-signals ───────────────────────

(defn cmd-suggest-signals [args]
  (let [opts (parse-opts args)
        anomaly-id (get-opt opts "anomaly")]
    (if (nil? anomaly-id)
      (println "Usage: abductcli suggest-signals --anomaly <id>")
      (let [config (mio/read-config)
            result (sig/suggest anomaly-id config)]
        (sig/print-candidates result)))))

;; ── 커맨드: attach-signal ─────────────────────────

(defn cmd-attach-signal [args]
  (let [opts (parse-opts args)
        anomaly-id (get-opt opts "anomaly")
        ctx-id     (get-opt opts "ctx")]
    (if (or (nil? anomaly-id) (nil? ctx-id))
      (println "Usage: abductcli attach-signal --anomaly <id> --ctx <id>")
      (let [signal (sig/attach anomaly-id ctx-id)]
        (println (str "abductcli — signal 연결: " (:id signal)))
        (println (str "  " (:title signal) " → " anomaly-id))))))

;; ── 커맨드: export ────────────────────────────────

(defn cmd-export [args]
  (let [opts (parse-opts args)
        fmt  (or (get-opt opts "format") "compact")
        out  (get-opt opts "out")]
    (case fmt
      "raw"     (let [path (or out "data/export-raw.jsonl")
                      r    (export/export-raw path)]
                  (println (str "abductcli — raw export: " (:exported r) "건 → " path)))
      "compact" (let [path (or out "data/export-compact.jsonl")
                      r    (export/export-compact path)]
                  (println (str "abductcli — compact export: " (:exported r) "건 → " path)))
      "scenario" (let [anom-id (get-opt opts "anomaly")
                       path    (or out (str "data/scenario-" anom-id ".edn"))]
                   (if (nil? anom-id)
                     (println "Usage: abductcli export --format scenario --anomaly <id>")
                     (do (export/export-scenario anom-id path)
                         (println (str "abductcli — scenario bundle → " path)))))
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
      (do (println "Usage: abductcli write-memo --anomaly <id> --hypothesis \"...\" --evidence \"sig-001\"")
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
        (println (str "abductcli — memo 생성: " (:id m)))
        (memo/print-memo m)))))

;; ── 커맨드: backtest ──────────────────────────────

(defn cmd-backtest [args]
  (let [opts    (parse-opts args)
        memo-id (get-opt opts "memo")
        dir     (or (get-opt opts "direction") "recover")
        timing  (not= "false" (or (get-opt opts "timing") "true"))
        mag     (Double/parseDouble (or (get-opt opts "magnitude") "0.5"))]
    (if (nil? memo-id)
      (do (println "Usage: abductcli backtest --memo <id> --direction recover --timing true --magnitude 0.7")
          (println "  실제 결과를 입력하여 memo를 사후 검증"))
      (let [e (memo/backtest memo-id {:direction dir
                                      :timing    timing
                                      :magnitude mag})]
        (println (str "abductcli — backtest 평가"))
        (println)
        (memo/print-evaluation e)))))

;; ── 커맨드: question ──────────────────────────────

(defn cmd-question [args]
  (let [opts  (parse-opts args)
        claim (get-opt opts "claim")
        num   (some-> (get-opt opts "number") Double/parseDouble)
        unit  (get-opt opts "unit")
        entity (get-opt opts "entity")
        domain (get-opt opts "domain")
        hidden (get-opt opts "hidden")]
    (if (nil? claim)
      (do (println "Usage: abductcli question --claim \"A banana costs 1500 won\" --number 1500 --unit KRW --entity banana --domain agriculture")
          (println "  Optional: --hidden \"hectares,workers,ships\""))
      (let [hidden-vec (when hidden (str/split hidden #","))
            a (anom/declare-anomaly!
               {:claim             claim
                :number            num
                :unit              unit
                :entity            entity
                :domain            domain
                :hidden-quantities hidden-vec})]
        (println "abductcli — question registered as anomaly")
        (println)
        (println (str "  " (:id a) "  [" (:severity a) "]"))
        (println (str "  claim:  " (:claim a)))
        (println (str "  number: " (:value a) " " (:unit a)))
        (println (str "  entity: " (:entity a)))
        (println (str "  domain: " (:domain a)))
        (when (seq (:hidden-quantities a))
          (println (str "  hidden: " (str/join ", " (:hidden-quantities a)))))
        (println)
        (println (str "  Next: register context, then suggest-signals --anomaly " (:id a)))))))

;; ── 커맨드: pipeline ──────────────────────────────

(defn- clean-data-files!
  "이전 실행 결과 JSONL 정리."
  []
  (doseq [path ["data/transactions.jsonl"
                 "data/contexts.jsonl"
                 "data/anomalies.jsonl"
                 "data/signals.jsonl"
                 "data/memos.jsonl"
                 "data/evaluations.jsonl"]]
    (let [f (java.io.File. path)]
      (when (.exists f) (.delete f)))))

(defn- auto-hypothesis
  "anomaly + signal + drill-down 조합에서 가설 텍스트 자동 생성."
  [anomaly signals drill-down]
  (let [entity   (:entity anomaly)
        value    (:value anomaly)
        severity (:severity anomaly)
        sig-titles (mapv :title signals)
        ;; drill-down에서 가장 낮은 margin의 sub-category
        worst (first (sort-by :value drill-down))
        worst-info (when worst
                     (str (:entity worst)
                          " margin=" (format "%.1f%%" (* 100 (double (:value worst))))
                          " avg-discount=" (format "%.0f%%" (* 100 (get-in worst [:detail :avg-discount] 0)))))]
    (str entity " 마진율 " (format "%.1f%%" (* 100 (double value)))
         " — " severity " anomaly 탐지. "
         (when worst-info
           (str "유력 후보: " worst-info ". "))
         "관련 signal: "
         (str/join "; " sig-titles) ".")))

(defn cmd-pipeline [args]
  (let [csv-path (or (first args) "data/superstore.csv")
        pack-path (or (second args) "data/packs/calendar-sample.edn")
        config (mio/read-config)
        max-signals (get-in config [:signal :max-attach] 3)]

    ;; ═══ 0. 정리 ═══
    (clean-data-files!)

    ;; ═══ 1. Import ═══
    (println "═══ 1단계: Import ═══")
    (println)
    (if-not (.exists (java.io.File. csv-path))
      (do (println (str "  파일 없음: " csv-path)) (System/exit 1))
      (let [result (imp/ingest csv-path)]
        (imp/print-summary result)
        (println)

        ;; ═══ 2. Context 등록 ═══
        (println "═══ 2단계: Context 등록 ═══")
        (println)
        (if-not (.exists (java.io.File. pack-path))
          (println (str "  팩 없음: " pack-path " — context 없이 진행"))
          (let [ctx-result (ctx/import-pack pack-path)]
            (println (str "  팩: " (:pack-name ctx-result) " → " (:imported ctx-result) "건"))
            (println)
            (ctx/print-contexts (ctx/list-contexts))))
        (println)

        ;; ═══ 3. Anomaly 탐지 ═══
        (println "═══ 3단계: Anomaly 탐지 ═══")
        (println)
        (let [txs (mio/read-jsonl "data/transactions.jsonl")
              cat-txs (filter #(= "category" (:grain %)) txs)
              anomalies (anom/detect-and-save! cat-txs config)]
          (if (empty? anomalies)
            (println "  탐지된 anomaly 없음. 파이프라인 종료.")
            (do
              (println (str "  탐지: " (count anomalies) "건"))
              (println)
              (anom/print-anomalies anomalies)
              (println)

              ;; ═══ 3.5. Sub-category 드릴다운 ═══
              (println "═══ 3.5단계: Sub-category 드릴다운 ═══")
              (println)
              (let [sub-txs (filter #(= "sub-category" (:grain %)) txs)
                    ;; anomaly별 drill-down 결과 수집
                    drill-map
                    (into {}
                      (for [a anomalies]
                        (let [entity (:entity a)
                              sub-rows (filter
                                        (fn [tx]
                                          (let [sub-cat (:entity tx)]
                                            (some #(and (= entity (get-in % [:product :category]))
                                                        (= sub-cat (get-in % [:product :sub-category])))
                                                  (:rows result))))
                                        sub-txs)
                              sorted (sort-by :value sub-rows)]
                          (println (str "  ── " entity " 하위 ──"))
                          (doseq [tx sorted]
                            (println (str "    " (:entity tx)
                                          "  margin=" (format "%.4f" (double (:value tx)))
                                          "  δ=" (format "%+.4f" (double (:delta tx)))
                                          (let [d (get-in tx [:detail :avg-discount])]
                                            (when d (str "  avg-discount=" (format "%.0f%%" (* 100 d))))))))
                          (println)
                          [(:id a) (vec sorted)])))]
                (println)

                ;; ═══ 4. Signal 연결 ═══
                (println "═══ 4단계: Signal 검색 + 연결 ═══")
                (println)
                (let [attached-signals
                      (doall
                       (for [a anomalies
                             :let [anom-id (:id a)
                                   result (sig/suggest anom-id config)
                                   cands (:candidates result)
                                   top (take max-signals cands)]]
                         (do
                           (println (str "  ── " anom-id " (" (:entity a)
                                         " " (:severity a) ") ──"))
                           (if (empty? cands)
                             (do (println "    signal 후보 없음")
                                 {:anomaly a :signals [] :drill-down []})
                             (let [signals (doall
                                            (for [{:keys [ctx-ref ctx relevance]} top]
                                              (let [s (sig/attach anom-id ctx-ref)]
                                                (println (str "    → " (:id s)
                                                              "  rel=" (format "%.2f" relevance)
                                                              "  [" (:domain ctx) "]"
                                                              "  " (:title ctx)))
                                                s)))]
                               {:anomaly a :signals signals
                                :drill-down (get drill-map anom-id [])})))))]
                  (println)

                  ;; ═══ 5. Memo 생성 ═══
                  (println "═══ 5단계: Memo 생성 (자동 가설) ═══")
                  (println)
                  (doseq [{:keys [anomaly signals drill-down]} attached-signals
                          :when (seq signals)]
                    (let [hypothesis (auto-hypothesis anomaly signals drill-down)
                          m (memo/write-memo
                             {:subject            (:id anomaly)
                              :hypothesis         hypothesis
                              :evidence           (mapv :id signals)
                              :expected-direction "recover"
                              :prediction-window  "다음 분기"
                              :author             "agent:abductcli"})]
                      (memo/print-memo m)
                      (println)))

                  ;; ═══ 6. Export ═══
                  (println "═══ 6단계: Export ═══")
                  (println)
                  (let [r (export/export-compact "data/export-compact.jsonl")]
                    (println (str "  compact JSONL: " (:exported r) "건 → " (:path r))))
                  (println)

                  ;; ═══ 요약 ═══
                  (println "═══ 파이프라인 완료 ═══")
                  (println)
                  (println (str "  tx:       " (count txs) "건"))
                  (println (str "  anomaly:  " (count anomalies) "건"))
                  (println (str "  context:  " (count (ctx/list-contexts)) "건"))
                  (println (str "  signal:   " (count (sig/list-signals)) "건"))
                  (println (str "  memo:     " (count (memo/list-memos)) "건")))))))))))

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
      "pipeline"         (cmd-pipeline rest-args)
      "question"         (cmd-question rest-args)
      ;; 도움말
      (do
        (println "abductcli — 양적추론 판단 엔진 CLI")
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
        (println "  export --format raw|compact|scenario [--anomaly <id>]")
        (println)
        (println "Pipeline:")
        (println "  pipeline [csv] [context-pack]              전체 파이프라인 한 바퀴")
        (println)
        (println "Question (manual anomaly):")
        (println "  question --claim \"...\" --number N --unit U --entity E --domain D")
        (println "           [--hidden \"qty1,qty2,...\"]       scale question 등록")))))
