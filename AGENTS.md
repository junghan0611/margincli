# AGENTS.md — abductcli

## 프로젝트 정체성

양적추론 판단 엔진 CLI. Clojure.

운영 데이터를 원본 손실 없이 보존하고, AI가 읽을 수 있는 JSONL 단위로 재표현한다.
이 도구의 목표는 예측이 아니다.
**현재 시점의 최선 판단 재료를 드러내는 데이터 표면**이다.

## 설계 원칙

### 데이터는 코드다

- Clojure의 immutable 맵이 곧 도메인 모델이다.
- 별도 ORM/스키마 정의 없이, 맵 → 맵 변환이 파이프라인의 전부.
- EDN은 내부 표현, JSON/JSONL은 외부 인터페이스.
- BigDecimal은 JVM 내장 — 마진 계산에서 float을 쓰지 않는다.

### 원본 보존 우선

- 스키마를 먼저 확정하지 않는다. 원본을 잃지 않는 것이 먼저.
- 공개 데이터셋(Kaggle 등)으로 시작한다. 누구나 복제해서 돌릴 수 있어야 한다.
- Datomic 사상: immutable + time-aware + 원본 보존.

### CLI + 에이전트 스킬

- 웹앱이 아니다. 터미널에서 돌아가는 CLI.
- pi-skills로 등록 가능한 구조 — 에이전트가 직접 호출.
- 프론트엔드는 필요할 때 붙인다 (ClojureScript, API 분리).

### 예측에 대한 태도

AI가 해야 할 일은 미래를 단정하는 것이 아니라:
- 현재 시점에 어떤 데이터가 중요한지 드러내고
- 서로 흩어진 자료를 연결하며
- 사람이 결정을 내리기 위한 최선의 현재 맥락을 제공하는 것

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

## 관련 문서

- [[denote:20260410T144158][전략기획실 엑셀 데이터의 AI 이해용 JSONB 데이터레이크]]
- [[denote:20250509T135957][©캐글(kaggle) ©허깅페이스(huggingface) 데이터과학 머신러닝 커뮤니티 플랫폼]]

## 현재 상태 (2026-04-16)

**완료:**
- pipeline 커맨드 — 한 번 실행으로 전체 파이프라인 확인
- signal relevance 가중 평균 (domain 30%, entity 30%, time 20%, source 20%)
- sub-category drill-down → memo 가설에 반영
- entity 키워드 매칭 (하드코딩 — Furniture/Technology/Office Supplies)
- compact JSONL export

**알려진 한계:**
- time-window 필터 미적용 — 전체 context 조회 후 relevance 정렬만
- entity 매칭 하드코딩 — taxonomy 파일 분리 필요
- pipeline 실행 시 기존 JSONL 전체 삭제 (demo-first)
- memo 문장이 auto-hypothesis 템플릿 — LLM 연동 시 개선 여지

## 다음 세션 — TODO

온보딩 검증 기준: [[denote:20260415T154505][abductcli 담당자 온보딩 검증]]

### 우선순위 1: signal ranking 테스트 강화
- Furniture anomaly일 때 여름 가구 할인전이 상위권에 와야 함
- relevance 순서가 기대와 맞는지 regression test
- relevance breakdown 노출 (domain=0.30, entity=0.27, ... → total=0.85)

### 우선순위 2: anomaly 시간 필드 + time-window 실제 필터
- anomaly 레코드에 :time 필드 추가
- context 날짜 ±window 필터 실제 적용 (현재는 전체 조회 후 정렬만)
- time-overlap-score를 실제 날짜 겹침 비율로 교체

### 우선순위 3: 시계열 tx 보강
- 일별/주간 tx 생성 (현재는 전체 기간 category/sub-category 집계만)
- baseline을 이동평균으로 교체 (현재는 전체 평균)
- region grain 추가

### 우선순위 4: backtest 자동화
- 현재는 사람이 direction/timing/magnitude 직접 입력
- 후속 tx와 자동 비교하는 평가 함수 추가

### 정리 사항
- entity taxonomy를 config.edn 또는 별도 파일로 분리 (하드코딩 제거)
- pipeline --clean 옵션 또는 run-id/output-dir 분리
