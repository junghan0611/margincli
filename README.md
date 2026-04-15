# abductcli

에이전트 친화적 마진 분석 CLI. Clojure.

운영 데이터(엑셀/시트/CSV)를 원본 손실 없이 모으고,
AI가 이해 가능한 JSONL 구조로 재표현하여,
**예측이 아니라 현재 시점의 최선 판단 재료를 드러내는 도구.**

## 철학

- 데이터는 코드다. Clojure의 immutable 맵이 곧 도메인이다.
- 예측 모델이 아니다. **현재 시점의 최선 데이터 표면**이다.
- 웹앱이 아니다. **CLI + 에이전트 스킬**이다. 프론트는 필요할 때 붙인다.
- 스키마를 먼저 확정하지 않는다. **원본을 잃지 않는 것**이 먼저다.

## 3층 아키텍처

```
1. Raw Layer    — 원본 보존 (CSV/Excel → EDN/JSONB)
2. AI Layer     — JSONL 재표현 (indicator / memo / evidence 단위)
3. Human Layer  — 조회면 (CLI 출력, 추후 프론트엔드)
```

## 도메인

제품(SKU)의 채널별 판매에서 공헌이익(Contribution Margin)을 계산하고,
행사(할인/프로모션) 시뮬레이션과 역산을 지원한다.

## 사용 예시 (목표)

```bash
# 데이터 가져오기
abductcli import data/superstore.csv

# 마진 계산
abductcli calc --product "GX1" --channel "coupang" --price 29900

# 역산: 목표 마진율 달성을 위한 최소 판매가
abductcli reverse --product "GX1" --channel "coupang" --target-margin 0.15

# 행사 시뮬레이션
abductcli simulate --product "GX1" --discount 0.2

# JSONL 내보내기 (에이전트용)
abductcli export --format jsonl --unit indicator

# 쿼리
abductcli query "마진율 10% 이하 제품"
```

## 기술 스택

- **Clojure 1.12+** — 메타언어, 데이터 = 코드
- **deps.edn** — 빌드
- **BigDecimal** — 마진 계산 (float 금지, JVM 내장)
- **Kaggle Superstore** — 공개 데이터셋으로 시작

## License

MIT
