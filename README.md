# abductcli — 양적추론 판단 엔진. Clojure.

## How to Read This Project

이 프로젝트는 **하나의 질문**을 추적한다:

> "데이터에서 이상 징후를 발견했을 때, 왜 그런지를 어떻게 증거 기반으로 추적하는가?"

LLM에게 "왜 Furniture 마진이 떨어졌어?"라고 물으면 그럴듯한 답이 즉시 나온다.
문제는 그게 맞는지 틀린지 검증할 구조가 없다는 것이다.

abductcli는 **anomaly → signal → memo → evaluation** 파이프라인으로
판단의 근거를 추적 가능하게 만드는 도구다.

## Quick Start

```bash
# 전체 파이프라인 한 바퀴 (Superstore 데이터)
clj -M:run pipeline

# 개별 단계
clj -M:run import data/superstore.csv
clj -M:run detect --grain category
clj -M:run suggest-signals --anomaly anom-001
```

`clj -M:run pipeline` 출력 예시:

```
═══ 3단계: Anomaly 탐지 ═══
  anom-001  Furniture  margin-rate=-0.1468  severity=high  z=-4.42

═══ 3.5단계: Sub-category 드릴다운 ═══
  Tables      margin=-0.4001  δ=-0.6405  avg-discount=37%
  Bookcases   margin=0.1600   δ=-0.0804  avg-discount=0%

═══ 4단계: Signal 검색 + 연결 ═══
  → sig-001  rel=0.85  [retail]  여름 가구 할인전 — Tables/Chairs 최대 45% off
  → sig-002  rel=0.78  [industry]  가구 업계 과잉재고 처분 트렌드

═══ 5단계: Memo 생성 ═══
  가설: Furniture 마진율 -14.7% — high anomaly 탐지.
        유력 후보: Tables margin=-40.0% avg-discount=37%.
        관련 signal: 여름 가구 할인전; 가구 업계 과잉재고 처분 트렌드.
```

## 현재 상태 — 정직한 기록

**동작하는 것:**
- CSV → tx JSONL 변환 (category/sub-category grain)
- Robust z-score 기반 anomaly 탐지
- Context 등록 (EDN 팩 일괄 임포트)
- Signal relevance 점수 (domain/entity/time/source 가중 평균)
- Memo 자동 생성 (drill-down 기반 가설)
- Compact JSONL export (에이전트 소비용)
- 23 tests, 79 assertions 통과

**아직 안 되는 것:**
- time-window 실제 필터링 (현재는 전체 조회 후 정렬만)
- 일별/주간 시계열 tx (현재는 전체 기간 집계)
- entity 매칭이 하드코딩 (Furniture/Technology/Office Supplies만)
- backtest 자동화 (현재는 수동 입력)
- pipeline 실행 시 기존 데이터 전체 삭제 (demo-first 결정)

## 파이프라인

```
1. Import     — CSV → 정규화 → tx JSONL (category/sub-category 집계)
2. Context    — 외부 맥락 등록 (달력, 업계, 매크로 이벤트)
3. Detect     — robust z-score로 anomaly 탐지
3.5 Drill     — anomaly category의 sub-category 분해
4. Signal     — context를 relevance 기준으로 anomaly에 연결
5. Memo       — anomaly + signal + drill-down → 증거 기반 가설
6. Export     — compact JSONL (다른 에이전트가 읽을 수 있는 표면)
```

## Changelog

### 2026-04-16: pipeline vertical slice
- `pipeline` 커맨드 추가 — 한 번 실행으로 전체 흐름 확인
- signal relevance 가중 평균 방식 전환 (domain/entity/time/source)
- sub-category drill-down → memo 가설 해상도 향상
- 질문: time-window 필터를 어디까지 넣어야 하는가? entity taxonomy를 어떻게 일반화하는가?

### 2026-04-15: anomaly→signal→memo 파이프라인 구현
- 4단계 파이프라인 구조 확립 (anomaly→signal→memo→evaluation)
- Superstore CSV 기반 anomaly 탐지 + context 등록 + signal 연결 + memo/backtest
- margincli → abductcli 리네이밍

### 2026-04-14: 마진 계산 엔진 + Kaggle 데이터
- BigDecimal 마진 계산/역산 엔진
- Kaggle Superstore CSV import

## 기술 스택

- **Clojure 1.12+** — deps.edn
- **BigDecimal** — 마진 계산 (float 금지)
- **Kaggle Superstore** — 공개 데이터셋

## License

MIT
