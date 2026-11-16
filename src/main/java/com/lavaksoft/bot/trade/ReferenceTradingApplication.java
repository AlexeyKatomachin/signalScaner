package com.lavaksoft.bot.trade;

import com.lavaksoft.bot.trade.data.CandleAggregationUtils;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.StrategyAnalysis;
import com.lavaksoft.bot.trade.model.TradeSignal;
import com.lavaksoft.bot.trade.mt5.EntryGuard;
import com.lavaksoft.bot.trade.mt5.Mt5AccountSnapshot;
import com.lavaksoft.bot.trade.mt5.Mt5FeedReader;
import com.lavaksoft.bot.trade.mt5.Mt5TradeExecutor;
import com.lavaksoft.bot.trade.strategy.ReferenceStrategySuite;
import com.lavaksoft.bot.trade.ui.DashboardServer;
import com.lavaksoft.bot.trade.ui.DashboardState;
import com.lavaksoft.bot.trade.ui.DashboardStateStore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Optional integration entry point for the imported strategy suite.
 * The old Main/Modern3LevelStrategy remain untouched.
 * AUTO_TRADE is false by default; without explicit enabling this process is analysis-only.
 */
public class ReferenceTradingApplication {
    public static void main(String[] args) throws Exception {
        Path mt5Dir = Paths.get(env("MT5_FILES_PATH", "."));
        String feedKey = env("MT5_FEED_KEY", "ea_signalScanner");
        String symbol = env("MT5_SYMBOL", "SP500");
        int dashboardPort = Integer.parseInt(env("DASHBOARD_PORT", "8080"));
        boolean autoTrade = Boolean.parseBoolean(env("AUTO_TRADE", "false"));
        double riskPct = Double.parseDouble(env("RISK_PCT", "0.5"));
        double contractSize = Double.parseDouble(env("CONTRACT_SIZE", "1.0"));
        double volumeStep = Double.parseDouble(env("VOLUME_STEP", "0.01"));
        double minVolume = Double.parseDouble(env("MIN_VOLUME", "0.01"));
        double spreadPrice = Double.parseDouble(env("SPREAD_PRICE", "0"));

        Mt5FeedReader feed = new Mt5FeedReader(mt5Dir);
        Mt5TradeExecutor executor = new Mt5TradeExecutor(mt5Dir);
        ReferenceStrategySuite strategies = new ReferenceStrategySuite();
        EntryGuard entryGuard = new EntryGuard();
        DashboardStateStore stateStore = new DashboardStateStore();
        DashboardServer dashboard = new DashboardServer(dashboardPort, stateStore);
        dashboard.start();

        Set<String> emittedSignals = new HashSet<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            DashboardState state = new DashboardState();
            state.setSymbol(symbol);
            state.setUpdatedAt(System.currentTimeMillis());
            try {
                String problem = feed.problem(feedKey, symbol);
                state.setFeedProblem(problem);
                state.setHeartbeat(feed.heartbeat(feedKey));
                try {
                    state.setAccount(executor.getAccountSnapshot());
                } catch (Exception ignored) {
                }
                try {
                    state.setLastExecution(executor.getLastResult());
                } catch (Exception ignored) {
                }

                if (problem == null) {
                    List<Candle> m5 = feed.readFeed(feedKey, "M5", true);
                    List<Candle> m15 = feed.readFeed(feedKey, "M15", true);
                    List<Candle> h1 = CandleAggregationUtils.aggregate(m15, 60L * 60_000L);
                    state.setCandles(tail(m5, 180));
                    if (m5.size() >= 60 && h1.size() >= 20) {
                        Map<String, StrategyAnalysis> analyses = strategies.analyze(h1, m5);
                        state.setStrategies(analyses);
                        if (autoTrade) {
                            for (StrategyAnalysis analysis : analyses.values()) {
                                TradeSignal signal = analysis.getSignal();
                                if (signal == null || !emittedSignals.add(signal.getSetupId())) continue;
                                Mt5AccountSnapshot account = executor.getAccountSnapshot();
                                double marketPrice = m5.get(m5.size() - 1).getClose();
                                EntryGuard.Verdict verdict = entryGuard.check(signal, marketPrice, spreadPrice);
                                if (!verdict.isOk()) continue;
                                double volume = Mt5TradeExecutor.computeVolume(account.getBalance(), signal.getEntry(), signal.getStop(),
                                        riskPct, contractSize, volumeStep, minVolume);
                                if (volume > 0)
                                    executor.placeOrder(symbol, signal, volume, "signalScaner-" + signal.getStrategy());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                state.setFeedProblem("Application error: " + e.getMessage());
            } finally {
                stateStore.set(state);
            }
        }, 0, 5, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            dashboard.stop(0);
        }));
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static <T> List<T> tail(List<T> input, int count) {
        if (input.size() <= count) return new ArrayList<>(input);
        return new ArrayList<>(input.subList(input.size() - count, input.size()));
    }
}
