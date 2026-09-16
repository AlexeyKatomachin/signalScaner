package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.Fractal;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.KeyFractalType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Утилиты для работы с фракталами, снятием ликвидности и сломом структуры (BOS).
 */
public class FractalUtils {
    /**
     * Находит индексы фракталов.
     * Фрактал считается подтверждённым, если слева и справа по 1 свечи с меньшим High или с больгим Low.
     */
    public List<Fractal> findFractals(List<Candle> candles) {
        List<Fractal> fractals = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            findFractalsFromIndex(candles, fractals, i);
        }
        return fractals;
    }

    public void updateFractals(List<Candle> candles) {
        List<Fractal> fractals = candles.stream().map(Candle::getFractal).filter(Objects::nonNull).collect(Collectors.toList());
        fractals.stream().max(Comparator.comparing(x -> x.getCandle().getDateTime())).ifPresent(x -> {
            findFractalsFromIndex(candles, fractals, candles.indexOf(x.getCandle()));
        });
    }

    private void findFractalsFromIndex(List<Candle> candles, List<Fractal> fractals, int i) {
        double curHigh = candles.get(i).getHigh();
        double curLow = candles.get(i).getLow();
        boolean isHigh = false, isLow = false;
        if (candles.get(i - 1).getHigh() < curHigh &&
                candles.get(i + 1).getHigh() < curHigh) {
            isHigh = true;
        }
        if (candles.get(i - 1).getLow() > curLow &&
                candles.get(i + 1).getLow() > curLow) {
            isLow = true;
        }
        if (i <= 3) {
            fractals.add(new Fractal(candles.get(i), isHigh, isLow));
        } else {
            if (candles.get(i - 3).getFractal() != null && (candles.get(i - 3).getFractal().isLow())) {
                isLow = false;
            }
            if (candles.get(i - 3).getFractal() != null && (candles.get(i - 3).getFractal().isHigh())) {
                isHigh = false;
            }
            if (isHigh || isLow) {
                fractals.add(new Fractal(candles.get(i), isHigh, isLow));
            }
        }
    }

    /**
     * Находит ключевые фракталы.
     */
    public List<KeyFractal> findKeyFractals(List<Candle> candles) {
        List<KeyFractal> keyFractals = new ArrayList<>();
        List<Fractal> fractals = candles.stream().map(Candle::getFractal)
                .filter(x -> Objects.nonNull(x) && !(x.isLow() && x.isHigh())).collect(Collectors.toList());
        Fractal localExtremumLow = null;
        Fractal localExtremumHigh = null;

        for (int i = 1; i < fractals.size() - 1; i++) {
            i = updateKeyFractalFromIndex(fractals, keyFractals, localExtremumLow, localExtremumHigh, i);
        }

        return keyFractals;
    }

    public int updateKeyFractalFromIndex(List<Fractal> fractals, List<KeyFractal> keyFractals, Fractal localExtremumLow,
                                         Fractal localExtremumHigh, int i) {
        Fractal currentFractal = fractals.get(i);

        if (keyFractals.isEmpty()) {
            int nextExtremumHighIndex = getNextExtremumHighIndex(fractals, i - 1, currentFractal);
            int nextExtremumLowIndex = getNextExtremumLowIndex(fractals, i - 1, currentFractal);
            if (nextExtremumHighIndex < nextExtremumLowIndex) {
                keyFractals.add(new KeyFractal(fractals.get(nextExtremumHighIndex), KeyFractalType.HH));
                keyFractals.add(new KeyFractal(fractals.get(nextExtremumLowIndex), KeyFractalType.HL));
                i = nextExtremumLowIndex;
            } else {
                keyFractals.add(new KeyFractal(fractals.get(nextExtremumLowIndex), KeyFractalType.LL));
                keyFractals.add(new KeyFractal(fractals.get(nextExtremumHighIndex), KeyFractalType.LH));
                i = nextExtremumHighIndex;
            }
        } else {
            int nextExtremumHighIndex = getNextExtremumHighIndex(fractals, i, currentFractal);
            int nextExtremumLowIndex = getNextExtremumLowIndex(fractals, i, currentFractal);

            Fractal nextExtremumLow = fractals.get(nextExtremumLowIndex);
            if (localExtremumLow == null || localExtremumLow.getCandle().getLow() > nextExtremumLow.getCandle().getLow()) {
                localExtremumLow = nextExtremumLow;
            }

            Fractal nextExtremumHigh = fractals.get(nextExtremumHighIndex);
            if (localExtremumHigh == null || localExtremumHigh.getCandle().getHigh() > nextExtremumHigh.getCandle().getHigh()) {
                localExtremumHigh = nextExtremumHigh;
            }

            KeyFractal lastKeyFractalHigh = getLastKeyFractalHigh(keyFractals);
            KeyFractal lastKeyFractalLow = getLastKeyFractalLow(keyFractals);

            if (lastKeyFractalHigh != null && lastKeyFractalHigh.getFractal().getCandle().getHigh() < nextExtremumHigh.getCandle().getHigh()
                    && isBiggerThen50PercentOfCandleBody(lastKeyFractalHigh.getFractal().getCandle(), nextExtremumHigh.getCandle())) {
                if (lastKeyFractalHigh.getType().equals(KeyFractalType.HH)) {
                    if (localExtremumLow != null) {
                        keyFractals.add(new KeyFractal(localExtremumLow, KeyFractalType.HL));
                        localExtremumLow = null;
                    } else {
                        throw new RuntimeException("can't be lastKeyFractalLow null n process of creating new Key High");
                    }
                }
                KeyFractal keyFractalHigh = new KeyFractal(nextExtremumHigh, KeyFractalType.HH);
                lastKeyFractalHigh = getLastKeyFractalHigh(keyFractals);
                if (lastKeyFractalHigh.getType().equals(KeyFractalType.LH)) {
                    lastKeyFractalHigh.setHasBos(true);
                }
                keyFractals.add(keyFractalHigh);
            }

            if (lastKeyFractalLow != null && lastKeyFractalLow.getFractal().getCandle().getLow() > nextExtremumLow.getCandle().getLow()
                    && isLowerThen50PercentOfCandleBody(lastKeyFractalLow.getFractal().getCandle(), nextExtremumLow.getCandle())) {
                if (lastKeyFractalLow.getType().equals(KeyFractalType.LL)) {
                    if (localExtremumHigh != null) {
                        keyFractals.add(new KeyFractal(localExtremumHigh, KeyFractalType.LH));
                        localExtremumHigh = null;
                    } else {
                        throw new RuntimeException("can't be lastKeyFractalLow null n process of creating new Key High");
                    }
                } else {
                    KeyFractal keyFractalLow = new KeyFractal(nextExtremumLow, KeyFractalType.LL);
                    lastKeyFractalLow = getLastKeyFractalLow(keyFractals);
                    if (lastKeyFractalLow.getType().equals(KeyFractalType.LH)) {
                        lastKeyFractalLow.setHasBos(true);
                    }
                    keyFractals.add(keyFractalLow);
                }
            }
        }

        return i;
    }

    private static boolean isBiggerThen50PercentOfCandleBody(Candle keyFractalHighCandle, Candle nextFractalHighCandle) {
        double openHigher = nextFractalHighCandle.getOpen() - keyFractalHighCandle.getHigh();
        double closeSmaller = keyFractalHighCandle.getHigh() - nextFractalHighCandle.getClose();
        return (openHigher - closeSmaller) > 0;
    }

    private static boolean isLowerThen50PercentOfCandleBody(Candle keyFractalLowCandle, Candle nextFractalLowCandle) {
        double openLower = keyFractalLowCandle.getLow() - nextFractalLowCandle.getClose();
        double closeHigher = nextFractalLowCandle.getOpen() - keyFractalLowCandle.getLow();
        return (openLower - closeHigher) > 0;
    }

    private static KeyFractal getLastKeyFractalHigh(List<KeyFractal> keyFractals) {
        for (int i = keyFractals.size() - 1; i >= 0; i--) {
            if (keyFractals.get(i).getFractal().isHigh()) {
                return keyFractals.get(i);
            }
        }
        return null;
    }

    private static KeyFractal getLastKeyFractalLow(List<KeyFractal> keyFractals) {
        for (int i = keyFractals.size() - 1; i >= 0; i--) {
            if (keyFractals.get(i).getFractal().isLow()) {
                return keyFractals.get(i);
            }
        }
        return null;
    }

    private static int getNextExtremumHighIndex(List<Fractal> fractals, int currentIndex, Fractal currentFractal) {
        for (int i = currentIndex + 1; i < fractals.size(); i++) {
            if (currentFractal.isHigh()) {
                Fractal nearestHighNext = getNearestHighValidFractalNext(currentIndex, fractals.size(), fractals);
                Fractal nearestHighPrevious = getNearestHighValidFractalPrevious(currentIndex, fractals);
                if (nearestHighNext != null && currentFractal.getCandle().getHigh() > nearestHighNext.getCandle().getHigh() &&
                        (nearestHighPrevious == null || currentFractal.getCandle().getHigh() > nearestHighPrevious.getCandle().getHigh())) {
                    return i;
                }
            }
        }
        return currentIndex;
    }

    private static int getNextExtremumLowIndex(List<Fractal> fractals, int currentIndex, Fractal currentFractal) {
        for (int i = currentIndex + 1; i < fractals.size(); i++) {
            if (currentFractal.isLow()) {
                Fractal nearestLowNext = getNearestLowValidFractalNext(currentIndex, fractals.size(), fractals);
                Fractal nearestLowPrevious = getNearestLowValidFractalPrevious(currentIndex, fractals);
                if (nearestLowNext != null && currentFractal.getCandle().getLow() < nearestLowNext.getCandle().getLow() &&
                        (nearestLowPrevious == null || currentFractal.getCandle().getLow() < nearestLowPrevious.getCandle().getLow())) {
                    return i;
                }
            }
        }
        return currentIndex;
    }


    private static Fractal getNearestHighValidFractalNext(int currentIndex, int length, List<Fractal> fractals) {
        boolean hasLow = false;
        for (int i = currentIndex + 1; i < length; i++) {
            if (fractals.get(i).isLow()) hasLow = true;
            if (fractals.get(i).isHigh() && hasLow) return fractals.get(i);
        }
        return null;
    }

    private static Fractal getNearestHighValidFractalPrevious(int currentIndex, List<Fractal> fractals) {
        boolean hasLow = false;
        for (int i = currentIndex - 1; i > 0; i--) {
            if (fractals.get(i).isLow()) hasLow = true;
            if (fractals.get(i).isHigh() && hasLow) return fractals.get(i);
        }
        return null;
    }


    private static Fractal getNearestLowValidFractalNext(int currentIndex, int length, List<Fractal> fractals) {
        boolean hasHigh = false;
        for (int i = currentIndex + 1; i < length; i++) {
            if (fractals.get(i).isHigh()) hasHigh = true;
            if (fractals.get(i).isLow() && hasHigh) return fractals.get(i);
        }
        return null;
    }

    private static Fractal getNearestLowValidFractalPrevious(int currentIndex, List<Fractal> fractals) {
        boolean hasHigh = false;
        for (int i = currentIndex - 1; i > 0; i--) {
            if (fractals.get(i).isHigh()) hasHigh = true;
            if (fractals.get(i).isLow() && hasHigh) return fractals.get(i);
        }
        return null;
    }


    /**
     * Находит индексы фракталов HIGH (локальные максимумы).
     * Фрактал считается подтверждённым, если слева и справа по 2 свечи с меньшим High.
     */
    @Deprecated
    public static List<Fractal> findFractalHighs(List<Candle> candles) {
        List<Fractal> idxList = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            double curHigh = candles.get(i).getHigh();
            if (candles.get(i - 1).getHigh() < curHigh &&
                    candles.get(i + 1).getHigh() < curHigh) {
                if (i <= 3) {
                    idxList.add(new Fractal(candles.get(i), true, false));
                } else {
                    if (candles.get(i - 3).getFractal() != null && (candles.get(i - 3).getFractal().isHigh())) continue;
                    idxList.add(new Fractal(candles.get(i), true, false));
                }
            }
        }
        return idxList;
    }

    /**
     * Находит индексы фракталов LOW (локальные минимумы).
     */
    @Deprecated
    public static List<Fractal> findFractalLows(List<Candle> candles) {
        List<Fractal> idxList = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            double curLow = candles.get(i).getLow();
            if (candles.get(i - 1).getLow() > curLow &&
                    candles.get(i + 1).getLow() > curLow) {
                if (i <= 3) {
                    idxList.add(new Fractal(candles.get(i), false, true));
                } else {
                    if (candles.get(i - 3).getFractal() != null && (candles.get(i - 3).getFractal().isLow())) continue;
                    idxList.add(new Fractal(candles.get(i), false, true));
                }
            }
        }
        return idxList;
    }

    /**
     * Проверяет, было ли снятие ликвидности (Liquidity Grab) на указанном фрактале.
     * Снятие ликвидности может происходить как телом свечи, так и фитилем (тенью).
     * Для HIGH: свеча после фрактала должна пробить фрактальный HIGH (high > fractalHigh)
     * и закрыться ниже (close < fractalHigh) – это классический sweep.
     * Для LOW: свеча после фрактала должна пробить LOW (low < fractalLow) и закрыться выше.
     */
    @Deprecated
    public static boolean isLiquiditySwept(List<Candle> candles, int fractalIndex, boolean isHigh) {
        if (fractalIndex + 1 >= candles.size()) return false;
        Candle fractalCandle = candles.get(fractalIndex);
        Candle nextCandle = candles.get(fractalIndex + 1);
        if (isHigh) {
            double fractalHigh = fractalCandle.getHigh();
            // Снятие ликвидности: касание (high > fractalHigh) + возврат тела под уровень
            return nextCandle.getHigh() > fractalHigh && nextCandle.getClose() < fractalHigh;
        } else {
            double fractalLow = fractalCandle.getLow();
            return nextCandle.getLow() < fractalLow && nextCandle.getClose() > fractalLow;
        }
    }

    /**
     * Возвращает true, если слом структуры (BOS) произошёл.
     * BOS считается только при перекрытии ключевого уровня ТЕЛОМ свечи (close).
     * Для восходящего BOS: close последней свечи > ключевого уровня.
     * Для нисходящего BOS: close последней свечи < ключевого уровня.
     */
    public static boolean hasBOS(List<Candle> candles, boolean upward) {
        return getBOSLevel(candles, upward) != null;
    }

    /**
     * Возвращает цену ключевого уровня (пробитого фрактала), если слом структуры произошёл.
     * Иначе возвращает null.
     * <p>
     * Алгоритм:
     * 1. Находим два последних фрактала нужного направления.
     * 2. Выбираем экстремальный (максимальный для BUY, минимальный для SELL).
     * 3. Проверяем, что последняя закрытая свеча перекрыла этот уровень ТЕЛОМ.
     */
    @Deprecated
    public static Double getBOSLevel(List<Candle> candles, boolean upward) {
        if (candles.size() < 5) return null;

        List<Integer> fractals = upward ? findFractalHighs(candles).stream().map(x -> ((Double) x.getCandle().getHigh()).intValue()).collect(Collectors.toList())
                : findFractalLows(candles).stream().map(x -> ((Double) x.getCandle().getLow()).intValue()).collect(Collectors.toList());
        if (fractals.size() < 2) return null;

        int lastIdx = fractals.get(fractals.size() - 1);
        int prevIdx = fractals.get(fractals.size() - 2);
        if (lastIdx > candles.size() - 3) return null; // фрактал ещё не окончательный

        double lastValue = upward ? candles.get(lastIdx).getHigh() : candles.get(lastIdx).getLow();
        double prevValue = upward ? candles.get(prevIdx).getHigh() : candles.get(prevIdx).getLow();
        double keyLevel = upward ? Math.max(lastValue, prevValue) : Math.min(lastValue, prevValue);

        // BOS определяется именно закрытием свечи (телом)
        double lastClose = candles.get(candles.size() - 1).getClose();
        boolean isBroken = upward ? lastClose > keyLevel : lastClose < keyLevel;
        return isBroken ? keyLevel : null;
    }

    public void updateKeyFractals(List<Candle> candles) {
        List<Fractal> fractals = candles.stream().map(Candle::getFractal)
                .filter(x -> Objects.nonNull(x) && !(x.isLow() && x.isHigh())).collect(Collectors.toList());
        List<KeyFractal> keyFractals = new ArrayList<>(fractals.stream()
                .map(Fractal::getKeyFractal)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        Fractal localExtremumLow = null;
        Fractal localExtremumHigh = null;

        keyFractals.stream().max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime())).ifPresent(x -> {
            for (int i = fractals.indexOf(x.getFractal()); i < fractals.size(); i++) {
                updateKeyFractalFromIndex(fractals, keyFractals, localExtremumLow, localExtremumHigh, i);
            }
        });
    }
}