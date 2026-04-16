# abductcli — quantitative abductive reasoning engine. Clojure.

## How to Read This Project

This human cannot grasp scale.

A banana costs 2,000 won at the corner store. It's heavy. It traveled
from Southeast Asia. How many hectares of land, how many workers sweating
how many hours a day, how many cargo ships — to put bananas in every
neighborhood on Earth at this price?

The Manhattan Project built secret factory cities across the US to
produce a few kilograms of enriched uranium. How many people? How big
were the facilities? What did the logistics look like?

Anthropic's Claude is now answering "what should I eat for lunch" for
millions of non-developers on Opus. How many GPUs does that require?
How large is the datacenter? What does the power consumption look like?

The Battle of Stalingrad consumed roughly 400,000 lives (uncertain).
What scale of logistics, supply chains, and human organization makes
that possible — or inevitable?

**These are all the same question:** given a surprising fact and limited
information, can you reason backward to the hidden quantities that
must be true?

This is abduction — inference to the best explanation. Not prediction.
Not correlation. The question is:

> "I see this number. What combination of conditions would make this
> number possible? Can I cross-check from fragments of other domains?"

abductcli is a tool for practicing this. The pipeline:

```
anomaly   — a number that surprises you (any domain)
signal    — candidate explanations (possibly from a completely different domain)
memo      — a hypothesis connecting them, with evidence chain
evaluation — did it turn out right?
```

The point is not to get the right answer. The point is to build the
habit of reasoning from fragments, tracking your guesses, and learning
which cross-domain connections actually hold.

**Reference:** Geoffrey West, *Scale* — universal laws connecting
biology, cities, and companies. The same power laws appear across
domains that seem to have nothing in common.

## Quick Start

```bash
# Full pipeline (Superstore retail data — first demo dataset)
clj -M:run pipeline

# Individual steps
clj -M:run import data/superstore.csv
clj -M:run detect --grain category
clj -M:run suggest-signals --anomaly anom-001
```

## Current State — Honest Record

**Working:**
- CSV → tx JSONL (category/sub-category grain)
- Robust z-score anomaly detection
- Context registration (EDN pack bulk import)
- Signal relevance scoring (domain/entity/time/source weighted average)
- Auto-generated memo with drill-down hypothesis
- Compact JSONL export (agent-consumable surface)
- 23 tests, 79 assertions passing

**Not working yet:**
- Only one dataset (Superstore retail). Cross-domain is the goal, not single-domain.
- Time-window filtering not applied (full scan + sort only)
- Entity matching hardcoded (Furniture/Technology/Office Supplies)
- No generic import — each dataset needs its own normalizer
- Backtest is manual input only
- Pipeline wipes all data on each run (demo-first)

## Pipeline

```
1. Import     — CSV → normalize → tx JSONL (aggregated by grain)
2. Context    — register external facts (calendar, industry, macro events)
3. Detect     — robust z-score anomaly detection
3.5 Drill     — decompose anomaly entity into sub-entities
4. Signal     — rank contexts by relevance, attach to anomaly
5. Memo       — anomaly + signal + drill-down → evidence-backed hypothesis
6. Export     — compact JSONL (surface for other agents to read)
```

## Changelog

### 2026-04-16: identity pivot — from margin tool to scale reasoning
- Recognized that Superstore demo was proof-of-concept, not the destination
- Real question: cross-domain quantitative abduction (bananas, nukes, GPUs, wars)
- README rewritten around "this human cannot grasp scale"
- Pipeline structure unchanged — it generalizes beyond retail

### 2026-04-16: pipeline vertical slice
- `pipeline` command — full flow in one execution
- Signal relevance weighted average (domain/entity/time/source)
- Sub-category drill-down → improved memo hypothesis resolution
- Open questions: time-window filtering depth, entity taxonomy generalization

### 2026-04-15: anomaly→signal→memo pipeline
- 4-stage pipeline structure (anomaly→signal→memo→evaluation)
- Superstore CSV anomaly detection + context + signal + memo/backtest
- margincli → abductcli rename

### 2026-04-14: margin calculation engine + Kaggle data
- BigDecimal margin calc/reverse engine
- Kaggle Superstore CSV import

## Tech Stack

- **Clojure 1.12+** — deps.edn
- **BigDecimal** — margin calculations (no floats)
- **Kaggle Superstore** — first demo dataset (more to come)

## License

MIT
