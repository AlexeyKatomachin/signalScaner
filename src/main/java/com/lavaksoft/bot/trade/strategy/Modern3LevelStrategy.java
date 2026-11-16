package com.lavaksoft.bot.trade.strategy;

import com.lavaksoft.bot.trade.data.MarketDataProvider;
import com.lavaksoft.bot.trade.indicators.BOSUtils;
import com.lavaksoft.bot.trade.indicators.ContextUtils;
import com.lavaksoft.bot.trade.indicators.FVGUtils;
import com.lavaksoft.bot.trade.indicators.FibonacciRange;
import com.lavaksoft.bot.trade.indicators.FractalUtils;
import com.lavaksoft.bot.trade.indicators.LiquidityUtils;
import com.lavaksoft.bot.trade.indicators.POIUtils;
import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.ContextFlow;
import com.lavaksoft.bot.trade.model.IntervalType;
import com.lavaksoft.bot.trade.model.KeyFractal;
import com.lavaksoft.bot.trade.model.KeyFractalType;
import com.lavaksoft.bot.trade.model.Order;
import com.lavaksoft.bot.trade.model.POI;
import com.lavaksoft.bot.trade.model.Setup;
import com.lavaksoft.bot.trade.model.Signal;
import com.lavaksoft.bot.trade.notifier.ScreenshotGenerator;
import com.lavaksoft.bot.trade.notifier.TelegramNotifier;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Modern3LevelStrategy implements Strategy {
    private final FractalUtils fractalUtils;
    private final ContextUtils contextUtils;
    private final FVGUtils fvgUtils;
    private final LiquidityUtils liquidityUtils;
    private final POIUtils poiUtils;
    private final BOSUtils bosUtils;
    private final MarketDataProvider marketDataProvider;
    private final TelegramNotifier telegramNotifier;
    private final ScreenshotGenerator screenshotGenerator;
    private final String tradingPare;
    private List<Candle> htf1WCandles, htf1DCandles, mtf4hCandles, mtf1hCandles, ltf15mCandles, ltf5mCandles;
    private Setup setup;
    private List<Order> orders = new ArrayList<>();

    public Modern3LevelStrategy(FractalUtils fractalUtils, ContextUtils contextUtils, FVGUtils fvgUtils, LiquidityUtils liquidityUtils, POIUtils poiUtils, BOSUtils bosUtils, MarketDataProvider marketDataProvider, TelegramNotifier telegramNotifier, ScreenshotGenerator screenshotGenerator, String tradingPare) {
        this.fractalUtils = fractalUtils;
        this.contextUtils = contextUtils;
        this.fvgUtils = fvgUtils;
        this.liquidityUtils = liquidityUtils;
        this.poiUtils = poiUtils;
        this.bosUtils = bosUtils;
        this.marketDataProvider = marketDataProvider;
        this.telegramNotifier = telegramNotifier;
        this.screenshotGenerator = screenshotGenerator;
        this.tradingPare = tradingPare;
    }

    @Override
    public void init(List<Candle> htf1WCandles, List<Candle> htf1DCandles, List<Candle> mtf4hCandles,
                     List<Candle> mtf1hCandles, List<Candle> ltf15mCandles, List<Candle> ltf5mCandles) {
        this.htf1WCandles = init(htf1WCandles);
        this.htf1DCandles = init(htf1DCandles);
        this.mtf4hCandles = init(mtf4hCandles);
        this.mtf1hCandles = init(mtf1hCandles);
        this.ltf15mCandles = init(ltf15mCandles);
        this.ltf5mCandles = init(ltf5mCandles);
    }

    @Override
    public void iteration() {
        LocalDateTime dateTime = ltf5mCandles.get(ltf5mCandles.size() - 1).getDateTime();

    }

    public void iteration(LocalDateTime dateTime) {
        updateTimeframesBacktest(dateTime);
    }

    private void updateTimeframesBacktest(LocalDateTime dateTime) {
//        boolean updatedLtf5m = updateTimeframeBacktest(dateTime, ltf5mCandles, IntervalType.m5, dateTime.getMinute() % 5 == 0);
        boolean updatedLtf15m = updateTimeframeBacktest(dateTime, ltf15mCandles, IntervalType.m15, dateTime.getMinute() % 15 == 0);
//        boolean updatedMtf1H = updateTimeframeBacktest(dateTime, mtf1hCandles, IntervalType.H1, dateTime.getHour() % 1 == 0);
        boolean updatedMtf4H = updateTimeframeBacktest(dateTime, mtf4hCandles, IntervalType.H4, dateTime.getHour() % 4 == 0);
        boolean updatedHtf1D = updateTimeframeBacktest(dateTime, htf1DCandles, IntervalType.D1, dateTime.getDayOfMonth() % 1 == 0);
//        boolean updatedHtf1W = updateTimeframeBacktest(dateTime, htf1WCandles, IntervalType.W1, dateTime.getMonthValue() % 1 == 0);

        if (updatedLtf15m) {
            synchronizeLtf(ltf15mCandles);
            telegramNotifier.sendTestPhoto(screenshotGenerator.testScreenshot(ltf15mCandles, IntervalType.m15, tradingPare));
        }
        if (updatedMtf4H) {
            synchronizeMtf(mtf4hCandles);
        }
        if (updatedHtf1D) {
            liquidityUtils.updateDaylyLiquidity(htf1DCandles.get(htf1DCandles.size() - 1), mtf4hCandles);
            synchronizeHtf();
        }

        Candle currentLtfCandle = ltf15mCandles.get(ltf15mCandles.size() - 1);
        createNewOrders(currentLtfCandle);
        analyzeOrders(currentLtfCandle);
        sendNotification(currentLtfCandle);
    }

    private void sendNotification(Candle currentLtfCandle) {
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            if (order.isNeedNotifyClients()) {
                order.setNeedNotifyClients(false);
                if (order.isNeedPartialClose()) {
                    telegramNotifier.sendPartialCloseSignal(new Signal(order), currentLtfCandle.getTime());
                } else {
                    try {
                        telegramNotifier.sendNewSignal(new Signal(order), screenshotGenerator.generateChart(order.getSetup(), htf1DCandles, IntervalType.D1),
                                screenshotGenerator.generateChart(order.getSetup(), mtf4hCandles, IntervalType.H4),
                                screenshotGenerator.generateChart(order.getSetup(), ltf15mCandles, IntervalType.m15), currentLtfCandle.getTime());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            if (order.isClosed()) {
                telegramNotifier.sendFulfilledSignal(new Signal(order), currentLtfCandle.getTime());
                orders.remove(i);
                i = i - 1;
            }
        }
    }

    private void createNewOrders(Candle currentLtfCandle) {
        if (setup != null) {
            if (setup.isBosLtf() && setup.getOrder() == null && ((setup.isSweepLiquidity() || setup.isSweepHtfLiquidity()) || (setup.isFvgGrab() || setup.isFvgHtfGrab()))) {
                orders.add(new Order(setup, currentLtfCandle.getClose()));
                setup = null;
            }
        }
    }

    private void analyzeOrders(Candle currentLtfCandle) {
        for (Order order : orders) {
            if (order.getType().equals(ActionType.LONG) && !order.isClosed()) {
                if (order.getEnterPrice() > currentLtfCandle.getLow() && order.isNeedPartialClose()) {
                    order.setFulfilledByBU(true);
                    order.setClosed(true);
                } else if (order.getTakeProfit() < currentLtfCandle.getHigh()) {
                    order.setFulfilledByTake(true);
                    order.setClosed(true);
                } else if (order.getStopLoss() > currentLtfCandle.getLow()) {
                    order.setClosed(true);
                } else if (order.getSetup().getFibonacciRangeMtf().getHigh() < currentLtfCandle.getHigh()) {
                    order.setNeedPartialClose(true);
                    order.setNeedNotifyClients(true);
                }
            } else if (order.getType().equals(ActionType.SHORT) && !order.isClosed()) {
                if (order.getEnterPrice() < currentLtfCandle.getHigh() && order.isNeedPartialClose()) {
                    order.setFulfilledByBU(true);
                    order.setClosed(true);
                } else if (order.getTakeProfit() > currentLtfCandle.getLow()) {
                    order.setFulfilledByTake(true);
                    order.setClosed(true);
                } else if (order.getStopLoss() < currentLtfCandle.getHigh()) {
                    order.setFulfilledByStop(true);
                    order.setClosed(true);
                } else if (order.getSetup().getFibonacciRangeMtf().getLow() > currentLtfCandle.getLow()) {
                    order.setNeedPartialClose(true);
                    order.setNeedNotifyClients(true);
                }
            }
        }
    }

    private void synchronizeHtf() {
        List<ContextFlow> contextFlow = contextUtils.getContextFlow(htf1DCandles);
        if (contextFlow.isEmpty()) return;
        ContextFlow currentContext = contextFlow.get(contextFlow.size() - 1);
        if (currentContext.getType().equals(ActionType.LONG)) {
            Optional<KeyFractal> maxHHOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.HH)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            Optional<KeyFractal> maxHLOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.HL)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            if (maxHHOptional.isPresent() && maxHLOptional.isPresent()) {
                KeyFractal maxHH = maxHHOptional.get();
                KeyFractal maxHL = maxHLOptional.get();
                FibonacciRange fibonacciRange = new FibonacciRange(maxHH.getFractal().getCandle().getHigh(), maxHL.getFractal().getCandle().getLow());
                Candle lastCandle = htf1DCandles.get(htf1DCandles.size() - 1);
                if (fibonacciRange.isDiscount(lastCandle.getMidPrice())) {
                    POI poi = poiUtils.checkIsInPOI(lastCandle, htf1DCandles);
                    if (poi != null) {
                        if (setup == null) {
                            setup = new Setup(tradingPare);
                            setup.setTypeHtf(ActionType.LONG);
                            setup.setFibonacciRangeHtf(fibonacciRange);
                        }
                        if (poi.getLiquidity() != null) {
                            setup.setSweepHtfLiquidity(true);
                            setup.setHtfLiquidity(poi.getLiquidity());
                        }
                        if (poi.getFvg() != null) {
                            setup.setFvgHtfGrab(true);
                            setup.setHtfFvg(poi.getFvg());
                        }
                    }
                }
            }
        } else if (currentContext.getType().equals(ActionType.SHORT)) {
            Optional<KeyFractal> maxLLOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.LL)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            Optional<KeyFractal> maxLHOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.LH)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            if (maxLLOptional.isPresent() && maxLHOptional.isPresent()) {
                KeyFractal maxLL = maxLLOptional.get();
                KeyFractal maxLH = maxLHOptional.get();
                FibonacciRange fibonacciRange = new FibonacciRange(maxLH.getFractal().getCandle().getHigh(), maxLL.getFractal().getCandle().getLow());
                Candle lastCandle = htf1DCandles.get(htf1DCandles.size() - 1);
                if (fibonacciRange.isPremium(lastCandle.getMidPrice())) {
                    POI poi = poiUtils.checkIsInPOI(lastCandle, htf1DCandles);
                    if (poi != null) {
                        if (setup == null) {
                            setup = new Setup(tradingPare);
                            setup.setTypeHtf(ActionType.SHORT);
                            setup.setFibonacciRangeHtf(fibonacciRange);
                        }
                        if (poi.getLiquidity() != null) {
                            setup.setSweepHtfLiquidity(true);
                            setup.setHtfLiquidity(poi.getLiquidity());
                        }
                        if (poi.getFvg() != null) {
                            setup.setFvgHtfGrab(true);
                            setup.setHtfFvg(poi.getFvg());
                        }
                    }
                }
            }
        }
    }

    private void synchronizeLtf(List<Candle> ltfCandles) {
        List<ContextFlow> contextFlows = contextUtils.getContextFlow(ltfCandles);
        if (contextFlows.isEmpty()) return;
        ContextFlow contextFlow = contextFlows.get(contextFlows.size() - 1);
        if (setup != null && (setup.isFvgGrab() || setup.isSweepLiquidity()) && !setup.isBosLtf()) {
            setup.setBosLtf(contextFlow.isHasBOS());
        }
    }

    private void synchronizeMtf(List<Candle> mtfCandles) {
        List<ContextFlow> contextFlow = contextUtils.getContextFlow(mtfCandles);
        if (contextFlow.isEmpty()) return;
        ContextFlow currentContext = contextFlow.get(contextFlow.size() - 1);
        if (currentContext.getType().equals(ActionType.LONG)) {
            Optional<KeyFractal> maxHHOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.HH)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            Optional<KeyFractal> maxHLOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.HL)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            if (maxHHOptional.isPresent() && maxHLOptional.isPresent()) {
                KeyFractal maxHH = maxHHOptional.get();
                KeyFractal maxHL = maxHLOptional.get();
                FibonacciRange fibonacciRange = new FibonacciRange(maxHH.getFractal().getCandle().getHigh(), maxHL.getFractal().getCandle().getLow());
                Candle lastCandle = mtfCandles.get(mtfCandles.size() - 1);
                if (fibonacciRange.isDiscount(lastCandle.getMidPrice())) {
                    POI poi = poiUtils.checkIsInPOI(lastCandle, mtfCandles);
                    if (poi != null) {
                        if (setup == null) {
                            setup = new Setup(tradingPare);
                            setup.setTypeMtf(ActionType.LONG);
                            setup.setFibonacciRangeMtf(fibonacciRange);
                        }
                        if (poi.getLiquidity() != null) {
                            setup.setSweepLiquidity(true);
                            setup.setLiquidity(poi.getLiquidity());
                            setup.setBosLtf(false);
                        }
                        if (poi.getFvg() != null) {
                            setup.setFvgGrab(true);
                            setup.setFvg(poi.getFvg());
                            setup.setBosLtf(false);
                        }
                    }
                } else {
                    setup = null;
                }
            } else {
                setup = null;
            }
        } else if (currentContext.getType().equals(ActionType.SHORT)) {
            Optional<KeyFractal> maxLLOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.LL)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            Optional<KeyFractal> maxLHOptional = currentContext.getKeyFractals().stream().filter(x -> x.getType().equals(KeyFractalType.LH)).max(Comparator.comparing(x -> x.getFractal().getCandle().getDateTime()));
            if (maxLLOptional.isPresent() && maxLHOptional.isPresent()) {
                KeyFractal maxLL = maxLLOptional.get();
                KeyFractal maxLH = maxLHOptional.get();
                FibonacciRange fibonacciRange = new FibonacciRange(maxLH.getFractal().getCandle().getHigh(), maxLL.getFractal().getCandle().getLow());
                Candle lastCandle = mtfCandles.get(mtfCandles.size() - 1);
                if (fibonacciRange.isPremium(lastCandle.getMidPrice())) {
                    POI poi = poiUtils.checkIsInPOI(lastCandle, mtfCandles);
                    if (poi != null) {
                        if (setup == null) {
                            setup = new Setup(tradingPare);
                            setup.setTypeMtf(ActionType.SHORT);
                            setup.setFibonacciRangeMtf(fibonacciRange);
                        }
                        if (poi.getLiquidity() != null) {
                            setup.setSweepLiquidity(true);
                            setup.setLiquidity(poi.getLiquidity());
                            setup.setBosLtf(false);
                        }
                        if (poi.getFvg() != null) {
                            setup.setFvgGrab(true);
                            setup.setFvg(poi.getFvg());
                            setup.setBosLtf(false);
                        }
                    }
                } else {
                    setup = null;
                }
            } else {
                setup = null;
            }
        }
    }

    private boolean updateTimeframeBacktest(LocalDateTime dateTime, List<Candle> candles, IntervalType interval, boolean scheduled) {
        Candle candle = candles.get(candles.size() - 1);
        boolean needSynchronized = false;
        if (scheduled && candle.getDateTime().compareTo(dateTime) < 0) {
            List<Candle> newCandles = marketDataProvider.getKlinesClosed(tradingPare, interval.getName(),
                    candle.getTime(),
                    dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            for (Candle newCandle : newCandles) {
                if (newCandle.getTime() >= candles.get(candles.size() - 1).getTime()) {
                    candles.add(newCandle);
                    needSynchronized = true;
                }
            }
        }
        if (needSynchronized) {
            updateNewIteration(candles);
        }
        return needSynchronized;
    }

    private void updateNewIteration(List<Candle> candles) {
        fractalUtils.updateFractals(candles);
        fractalUtils.updateKeyFractals(candles);
        fvgUtils.updateFVG(candles);
        List<ContextFlow> contextFlow = contextUtils.getContextFlow(candles);
        if (contextFlow.isEmpty()) return;
        bosUtils.hasBOS(contextFlow.get(contextFlow.size() - 1), candles.get(candles.size() - 1));
        liquidityUtils.findLiquidity(candles, contextFlow);
        poiUtils.findPOI(candles);
    }


    private List<Candle> init(List<Candle> candles) {
        if (candles == null) {
            return null;
        }
        fractalUtils.findFractals(candles);
        fractalUtils.findKeyFractals(candles);
        fvgUtils.findFVG(candles);
        List<ContextFlow> contextFlow = contextUtils.getContextFlow(candles);
        if (contextFlow.isEmpty()) return candles;
        liquidityUtils.findLiquidity(candles, contextFlow);
        poiUtils.findPOI(candles);
        return candles;
    }
}
