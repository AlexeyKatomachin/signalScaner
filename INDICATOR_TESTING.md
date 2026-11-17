# Проверка индикаторов signalScaner

Цель этого слоя — сначала сделать каждый индикатор понятным и воспроизводимым, и только после этого использовать его в стратегии/backtest.

## Что генерируется

После запуска открой:

`target/indicator-tests/index.html`

Для каждого сценария создаются:

- `before.png` — чистые свечи;
- `after.png` — те же свечи и тот же масштаб + результат индикатора;
- `context.json` — индексы, цены, типы и другие вычисленные значения;
- `result.txt` — короткое описание того, что проверять;
- `meta.properties` — технические данные для HTML-отчёта.

## Запуск

Windows:

`run-indicator-tests.bat`

Или из терминала:

```bash
mvn -Dtest=visual.IndicatorVisualTest,reference.ReferenceIndicatorLogicTest,reference.StructureIndicatorLogicTest,reference.IndicatorTimingTest test
```

Только visual tests:

```bash
mvn -Dtest=visual.IndicatorVisualTest test
```

Один сценарий, например BOS:

```bash
mvn -Dtest=visual.IndicatorVisualTest#bosRealMarketScenario test
```

Полезные сценарии:

- `fractalRealMarketScenario`
- `keyFractalRealMarketScenario`
- `legacyFvgRealMarketScenario`
- `referenceFvgRealMarketScenario`
- `marketStructureRealMarketScenario`
- `sweepAndChochMainStructureScenario`
- `contextRealMarketScenario`
- `liquidityRealMarketScenario`
- `poiRealMarketScenario`
- `bosRealMarketScenario`
- `oteMultipleRealMarketScenario`
- `bprReadableRealMarketScenario`
- `breakerMultipleRealMarketScenario`

## Что изменилось в текущей итерации

### Fractal / Key Fractal

Basic fractal остаётся простым локальным экстремумом 1+1.

Key fractal теперь строится как более сильный swing 2+2. Соседние swing одного типа схлопываются до более экстремального, затем структура классифицируется:

- high выше предыдущего structural high → `HH`;
- high ниже/равен → `LH`;
- low выше предыдущего structural low → `HL`;
- low ниже/равен → `LL`.

На картинке key-fractals соединяются zig-zag линией.

### Context

Контекст больше не рисуется сплошными вертикальными фонами.

- `HH + HL` → LONG context;
- `LH + LL` → SHORT context.

На `after.png` видно сам structural zig-zag и точки, на которых контекст меняется.

### BOS / CHoCH

BOS теперь не является просто флагом на старом key-fractal.

Хранится реальное событие:

- какой pivot был структурным уровнем;
- на какой свече он был пробит;
- какая цена уровня;
- направление пробоя;
- `BOS` это или `CHoCH`.

Пробой подтверждается **закрытием свечи**, и линия на графике заканчивается на фактической break-candle.

### EQH / EQL

Старая формула была удалена: допустимое отклонение больше не растёт вместе с расстоянием между свечами.

Сейчас equality проверяется по ценовой близости относительно ATR:

`tolerance = 0.10 * ATR(до 200 свечей)`

с небольшим минимальным price tolerance для очень маленького ATR.

### FVG

Маленькие synthetic datasets всё ещё используются для точных assertions, но visual-графики Legacy/Reference FVG теперь строятся на реальном BTC-массиве. На картинке показываются последние FVG, а активная зона не тянется бесконечно через весь график.

### OTE

Теперь визуализируется несколько исторических OTE, а не один последний.

Для каждого видны:

- structural level;
- break candle;
- Order Block;
- `LEG LOW` / `LEG HIGH`;
- 0.618–0.786 OTE zone;
- mid;
- confluent FVG, если найден.

Важно: impulse leg фиксируется на break-candle. Будущие свечи больше не могут менять OTE диапазон — это убирает look-ahead из этой части расчёта.

### BPR

Visual test больше не рисует все FVG за весь период. Показываются несколько последних BPR и только два FVG, из overlap которых конкретный BPR получен.

### Breaker

Показывается несколько исторических breaker blocks. Для каждого отдельно отмечены исходный OB и candle, которая его invalidated.

### Sweep + CHoCH

Проверка выполняется на полном real-market dataset и строит несколько sweep событий основной структуры, а не один маленький synthetic пример.

## Как проверять руками

Для каждого индикатора:

1. Сначала открой `before.png`.
2. Сам определи ожидаемые точки/зоны без подсказки алгоритма.
3. Открой `after.png`.
4. Проверь, совпадает ли разметка.
5. Если конкретная точка выглядит неправильно — открой `context.json`, найди candle index и price.
6. Зафиксируй ошибочный участок как отдельный небольшой CSV regression scenario.
7. Только потом меняй алгоритм.

Главный принцип: найденный визуально баг должен превращаться в автоматический regression test и больше не возвращаться.

## Automated checks

`ReferenceIndicatorLogicTest` проверяет точную математику FVG/OTE/BPR.

`StructureIndicatorLogicTest` дополнительно проверяет:

- BOS создаётся именно на candle, которая **закрылась** через confirmed pivot, а не просто проколола его wick;
- близкие highs считаются EQH по price/ATR tolerance;
- явно разные highs не могут стать EQH только потому, что между ними много свечей.

`IndicatorTimingTest` защищает от look-ahead: swing 2+2 становится доступен только после двух right-side candles.

## По присланному SMC Pine-коду

Присланный исходник LuxAlgo имеет собственную лицензию CC BY-NC-SA 4.0. В Java-проект не делался построчный перенос исходника. Использованы общие алгоритмические идеи market structure: confirmed pivots, одноразовый break уровня, BOS/CHoCH в зависимости от предыдущего trend и ATR-relative equality для EQH/EQL.

Если в будущем планируется коммерческое распространение прямой производной от Pine-исходника, лицензионные условия исходного файла нужно проверять отдельно.
