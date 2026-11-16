package com.lavaksoft.bot.trade.ui;

import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.StrategyAnalysis;
import com.lavaksoft.bot.trade.mt5.Mt5AccountSnapshot;
import com.lavaksoft.bot.trade.mt5.Mt5ExecutionResult;
import com.lavaksoft.bot.trade.mt5.Mt5Heartbeat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardState {
    private String symbol;
    private long updatedAt;
    private String feedProblem;
    private Mt5Heartbeat heartbeat;
    private Mt5AccountSnapshot account;
    private Mt5ExecutionResult lastExecution;
    private List<Candle> candles = new ArrayList<>();
    private Map<String, StrategyAnalysis> strategies = new LinkedHashMap<>();

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFeedProblem() {
        return feedProblem;
    }

    public void setFeedProblem(String feedProblem) {
        this.feedProblem = feedProblem;
    }

    public Mt5Heartbeat getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Mt5Heartbeat heartbeat) {
        this.heartbeat = heartbeat;
    }

    public Mt5AccountSnapshot getAccount() {
        return account;
    }

    public void setAccount(Mt5AccountSnapshot account) {
        this.account = account;
    }

    public Mt5ExecutionResult getLastExecution() {
        return lastExecution;
    }

    public void setLastExecution(Mt5ExecutionResult lastExecution) {
        this.lastExecution = lastExecution;
    }

    public List<Candle> getCandles() {
        return candles;
    }

    public void setCandles(List<Candle> candles) {
        this.candles = candles;
    }

    public Map<String, StrategyAnalysis> getStrategies() {
        return strategies;
    }

    public void setStrategies(Map<String, StrategyAnalysis> strategies) {
        this.strategies = strategies;
    }
}
