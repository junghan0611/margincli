# data/ — abductcli 데이터셋

## superstore.csv

Kaggle "Sample - Superstore" 데이터셋 스키마 기반 샘플 데이터.

### 출처

- **원본**: [Kaggle Sample - Superstore](https://www.kaggle.com/datasets/vivek468/superstore-dataset-final)
- **라이선스**: CC0 Public Domain
- **현재 파일**: 원본 스키마에 맞춘 30행 샘플. 전체 데이터(~10,000행)는 Kaggle에서 다운로드 가능.

### 컬럼 → abductcli 도메인 매핑

| Superstore 컬럼 | abductcli 도메인 | 설명 |
|----------------|-----------------|------|
| Product ID, Product Name | `:product/id`, `:product/name` | 상품 식별 |
| Category, Sub-Category | `:product/category`, `:product/sub-category` | 상품 분류 |
| Region, Segment | `:channel/region`, `:channel/segment` | 판매 채널 (지역×고객유형) |
| Ship Mode | `:order/ship-mode` | 물류 방식 |
| Sales | `:margin/sales` | 매출 (할인 적용 후) |
| Profit | `:margin/profit` | 이익 |
| Sales - Profit | `:margin/cost` | 원가 (역산) |
| Discount | `:margin/discount` | 할인율 (0~1) |
| Quantity | `:margin/quantity` | 수량 |

### 도메인 매핑 설계 판단

Superstore 데이터에는 마진 분석의 모든 요소가 1:1로 있지 않다.
자연스러운 재해석이 필요한 부분:

- **Channel**: Superstore에 쿠팡/아마존 같은 채널 구분은 없다.
  Region × Segment 조합을 채널로 재해석한다.
  "East/Consumer"와 "West/Corporate"는 마진 구조가 다르다 — 이것이 채널의 본질.
- **Cost**: Superstore는 원가를 직접 제공하지 않는다.
  `Cost = Sales - Profit`으로 역산한다.
- **Commission**: Superstore에 수수료 개념은 없다.
  채널별 수수료는 별도 설정으로 확장 가능.
- **Event**: `Discount > 0`인 주문을 행사/프로모션으로 해석할 수 있다.

### 전체 데이터셋 사용

```bash
# Kaggle CLI로 다운로드 (kaggle 계정 필요)
kaggle datasets download vivek468/superstore-dataset-final
unzip superstore-dataset-final.zip -d data/

# 또는 직접 다운로드 후
mv "Sample - Superstore.csv" data/superstore.csv
```

코드는 샘플(30행)과 전체(~10,000행) 모두 동일하게 동작한다.
