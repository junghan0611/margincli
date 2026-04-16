# AGENTS.md — abductcli

## Project Identity

Cross-domain quantitative abductive reasoning engine. Clojure CLI.

The human using this tool cannot grasp scale. A banana at the corner store,
the Manhattan Project, GPU clusters serving millions of LLM queries,
the logistics of Stalingrad — these are all the same problem:
**given a surprising number and fragments of information, reason backward
to the hidden quantities that must be true.**

This is not a margin analysis tool. The Superstore demo was proof-of-concept.
The real purpose is connecting data from unrelated domains to practice
abductive reasoning — inference to the best explanation.

Pipeline: anomaly → signal → memo → evaluation.
The pipeline is domain-agnostic. What changes per domain is the import layer
and the context sources.

## Design Principles

### Data is code
- Clojure immutable maps are the domain model. No ORM, no schema-first.
- Map → map transforms are the entire pipeline.
- EDN internal, JSON/JSONL external interface.

### Preserve originals
- Don't fix the schema first. Preserve the source first.
- Start with public datasets (Kaggle, World Bank, etc). Anyone can replicate.

### Cross-domain by design
- An anomaly in retail margin can be explained by a signal from agriculture.
- A signal about GPU datacenter capacity can explain an anomaly in API latency.
- The tool should make "ridiculous" cross-domain connections testable.
- Entity matching must work across domain boundaries, not just within one dataset.

### CLI + agent skill
- Terminal CLI. Agent-callable via pi-skills.
- No web frontend until the reasoning pipeline is solid.

### Not prediction
- The goal is not to predict the future.
- The goal is: surface what fragments are relevant now, connect scattered data,
  and provide the best current context for a human to make judgment calls.
- Track guesses (memos) and check them later (evaluation).
  The point is learning which cross-domain intuitions actually hold.

## 3층 아키텍처

```
Raw Layer    → 원본 CSV/Excel을 EDN/JSONB로 보존
AI Layer     → JSONL 재표현 — indicator / memo / evidence 단위
Human Layer  → CLI 출력 (테이블, 요약), 추후 프론트엔드는 부가적
```

## 기술 스택

- **Clojure 1.12+** (deps.edn)
- **BigDecimal** — 마진 계산 (JVM 내장, float 금지)
- **clojure.data.csv** — CSV 파싱
- **cheshire** — JSON/JSONL 출력
- **next.jdbc + PostgreSQL** — 확장 시 JSONB 저장
- **SQLite** — 로컬 기본 저장
- **Kaggle Superstore** — 초기 공개 데이터셋

## 도메인 엔티티 (Clojure 맵)

```clojure
;; Product
{:product/id "GX1"
 :product/name "스마트 카메라 GX1"
 :product/category "Camera"
 :product/cost 15000M  ;; BigDecimal
 :product/cost-history [{:date "2026-01-01" :cost 14500M}
                        {:date "2026-03-01" :cost 15000M}]}

;; Channel
{:channel/id "coupang"
 :channel/name "쿠팡"
 :channel/commission-rate 0.12M
 :channel/coupon-policy {:max-rate 0.15M}}

;; Event
{:event/id "EVT-001"
 :event/product-id "GX1"
 :event/channel-id "coupang"
 :event/discount-rate 0.20M
 :event/period {:start "2026-04-15" :end "2026-04-30"}}

;; Margin (계산 결과)
{:margin/product-id "GX1"
 :margin/channel-id "coupang"
 :margin/sale-price 29900M
 :margin/cost 15000M
 :margin/commission 3588M
 :margin/contribution 11312M
 :margin/rate 0.3784M}
```

## AI Layer — JSONL 단위

```jsonl
{"type":"indicator","name":"margin_rate","value":0.3784,"product":"GX1","channel":"coupang","date":"2026-04-15"}
{"type":"memo","text":"GX1 쿠팡 20% 할인 시 마진율 18%로 하락, 경보 수준","date":"2026-04-15","source":"simulation"}
{"type":"evidence","claim":"Q1 평균 마진율 25%","data_points":342,"period":"2026-Q1","source":"superstore.csv"}
```

## 프로젝트 구조

```
abductcli/
├── AGENTS.md
├── README.md
├── deps.edn
├── build.clj
├── run.sh                    # 빌드/실행 진입점
├── src/
│   └── abductcli/
│       ├── core.clj          # CLI 진입점, 서브커맨드 디스패치
│       ├── import.clj        # Raw Layer — CSV/Excel → EDN/DB
│       ├── engine.clj        # 마진 계산/역산 엔진 (BigDecimal)
│       ├── simulate.clj      # 행사 시뮬레이션
│       ├── export.clj        # AI Layer — JSONL 내보내기
│       ├── query.clj         # 데이터 조회
│       └── db.clj            # 저장 (SQLite 기본, PostgreSQL 확장)
├── data/                     # 공개 데이터셋
│   └── superstore.csv
├── test/
│   └── abductcli/
│       ├── engine_test.clj
│       └── import_test.clj
└── skill/                    # pi-skills 연동
    └── SKILL.md
```

## 필수 명령어

```bash
# 실행
clj -M:run import data/superstore.csv
clj -M:run calc --product GX1 --channel coupang --price 29900

# 테스트
clj -M:test

# uber-jar 빌드
clj -T:build uber

# run.sh (통합)
./run.sh build
./run.sh calc --product GX1 --channel coupang --price 29900
```

## Related Documents

- [[denote:20260410T144158][전략기획실 엑셀 데이터의 AI 이해용 JSONB 데이터레이크]]
- [[denote:20250509T135957][©캐글(kaggle) ©허깅페이스(huggingface) 데이터과학 머신러닝 커뮤니티 플랫폼]]
- [[denote:20240815T133910][@제프리웨스트 스케일 - 생물 도시 기업 보편 법칙]]
- [[denote:20240617T052758][미래교육 질적연구 인지언어 바칼로레아]]

## Current State (2026-04-16)

**Working (single-domain demo):**
- pipeline command — full Superstore flow in one execution
- signal relevance weighted average (domain/entity/time/source)
- sub-category drill-down → memo hypothesis
- compact JSONL export

**Known limits:**
- Only Superstore dataset — no cross-domain reasoning yet
- time-window filtering not applied (full scan + sort only)
- entity matching hardcoded (Furniture/Technology/Office Supplies only)
- pipeline wipes all data on each run (demo-first)
- auto-hypothesis is template-based, not LLM-generated

## TODO — Next Sessions

Onboarding ref: [[denote:20260415T154505][abductcli 담당자 온보딩 검증]]

### Priority 1: Second dataset — cross-domain connection
- Add a non-retail dataset (agriculture, energy, population, or logistics)
- Make the import layer generic enough for different CSV schemas
- Test: can a signal from domain A explain an anomaly in domain B?
- This is the existential test for abductcli's identity

### Priority 2: Generic import + context sources
- Import normalizer should be configurable per dataset (column mapping)
- Context can come from Denote notes, web search, or manual entry
- Entity matching via taxonomy file or keyword dictionary (not hardcoded)

### Priority 3: Signal ranking regression tests
- Relevance ordering must be predictable and testable
- Relevance breakdown visible (domain=0.30, entity=0.27, ... → total=0.85)

### Priority 4: Time-aware retrieval
- Anomaly records need :time field
- Context date ±window filter actually applied
- time-overlap-score based on real date overlap ratio

### Later
- Backtest automation (auto-compare memo prediction vs subsequent data)
- pipeline --clean option or run-id/output-dir separation
- LLM-assisted hypothesis generation (use pipeline output as prompt context)
