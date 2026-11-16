package com.lavaksoft.bot.trade.mt5;

public class Mt5Heartbeat {
    private long utc;
    private long serverOffset;
    private String feedMode;
    private boolean connected;
    private String symbol;
    private String version;
    private double ageSeconds;

    public long getUtc() {
        return utc;
    }

    public void setUtc(long utc) {
        this.utc = utc;
    }

    public long getServerOffset() {
        return serverOffset;
    }

    public void setServerOffset(long serverOffset) {
        this.serverOffset = serverOffset;
    }

    public String getFeedMode() {
        return feedMode;
    }

    public void setFeedMode(String feedMode) {
        this.feedMode = feedMode;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public double getAgeSeconds() {
        return ageSeconds;
    }

    public void setAgeSeconds(double ageSeconds) {
        this.ageSeconds = ageSeconds;
    }
}
