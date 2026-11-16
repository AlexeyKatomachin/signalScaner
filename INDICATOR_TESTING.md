# Indicator validation framework

The goal is to validate every market indicator independently **before** it is used by a strategy or backtest.

## What a visual scenario produces

After running the visual suite, open:

`target/indicator-tests/index.html`

Each scenario contains:

- `before.png` — raw candles only;
- `after.png` — exactly the same candles and price scale with the indicator overlay;
- `context.json` — exact values/indexes detected by the algorithm;
- `result.txt` — compact human-readable summary;
- `meta.properties` — metadata used to build the HTML report.

The report is generated automatically under `target/indicator-tests/index.html`.

## Run all indicator validation tests

From the project root:

```bash
mvn -Dtest=visual.IndicatorVisualTest,reference.ReferenceIndicatorLogicTest,reference.IndicatorTimingTest test
```

On Windows you can simply run:

`run-indicator-tests.bat`

The batch file runs the tests and opens the HTML report if the run succeeds.

## Run only visual validation

```bash
mvn -Dtest=visual.IndicatorVisualTest test
```

## Run one scenario only

Example:

```bash
mvn -Dtest=visual.IndicatorVisualTest#oteRealMarketScenario test
```

Other useful method names:

- `fractalSyntheticScenario`
- `keyFractalRealMarketScenario`
- `legacyFvgSyntheticScenario`
- `referenceFvgBullishSyntheticScenario`
- `referenceFvgBearishSyntheticScenario`
- `marketStructureSyntheticScenario`
- `sweepAndChochSyntheticScenario`
- `contextRealMarketScenario`
- `liquidityRealMarketScenario`
- `poiRealMarketScenario`
- `bosRealMarketScenario`
- `oteRealMarketScenario`
- `bprRealMarketScenario`
- `breakerRealMarketScenario`

## Test data

Synthetic datasets live under:

`src/test/resources/indicator-scenarios/`

They are deliberately small and deterministic. Their purpose is automated correctness assertions.

Current synthetic fixtures cover:

- basic high/low fractals;
- bullish legacy/reference FVG;
- bearish reference FVG;
- bullish market structure with deterministic swing indexes;
- bullish liquidity sweep followed by CHoCH.

Real-market validation uses:

`src/test/resources/market-data/BTCUSDT_15m_400.csv`

It is used for the more complex, integrated indicator behaviour: key fractals, context, liquidity, POI, BOS, OTE, BPR and Breaker.

## How to review a scenario

Always check it in this order:

1. Open `before.png` and understand the raw price action first.
2. Open `after.png` and compare it with BEFORE. The candle set and price scale are identical.
3. Verify every marker/zone visually.
4. Open `context.json` and inspect indexes/prices/types numerically.
5. Read `result.txt` for the expected interpretation.
6. If the image looks wrong, do not change the strategy. Fix or clarify the indicator definition first.

## What is automated vs manual

Synthetic scenarios contain hard assertions for values that should be objectively stable.

Real-market scenarios intentionally do not assert every single market interpretation. They assert that the algorithm produces a meaningful result, while `before.png`, `after.png` and `context.json` are the human validation layer.

When a real-market case exposes a bug, copy a small candle window into a new synthetic CSV and add an exact assertion. This turns every discovered bug into a permanent regression test.

## Look-ahead protection

`reference.IndicatorTimingTest` contains explicit timing checks. For example, a 2-left/2-right swing is not visible until both right-side candles exist.

This rule is critical for the future replay/backtest engine: an indicator may only expose information that was actually knowable at that historical moment.

## Adding a new indicator

For every new indicator:

1. Add at least one small synthetic CSV under `src/test/resources/indicator-scenarios/<indicator>/`.
2. Add a visual scenario to `visual.IndicatorVisualTest`.
3. Render BEFORE before applying the indicator.
4. Apply the indicator.
5. Add hard assertions for deterministic behaviour.
6. Render AFTER using the exact same candle list.
7. Write all important values to `context.json` and the short explanation to `result.txt`.
8. Add a real-market scenario when the indicator is complex enough that visual behaviour matters.

Only after the indicator is trusted should a strategy compose it with other indicators.
