package com.lavaksoft.bot.trade.mt5;

import java.util.ArrayList;
import java.util.List;

public class Mt5AccountSnapshot {
    private double balance;
    private double equity;
    private boolean connected;
    private boolean tradeAllowed;
    private long login;
    private String server;
    private String accountKind;
    private List<Mt5Position> positions = new ArrayList<>();

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getEquity() {
        return equity;
    }

    public void setEquity(double equity) {
        this.equity = equity;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isTradeAllowed() {
        return tradeAllowed;
    }

    public void setTradeAllowed(boolean tradeAllowed) {
        this.tradeAllowed = tradeAllowed;
    }

    public long getLogin() {
        return login;
    }

    public void setLogin(long login) {
        this.login = login;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getAccountKind() {
        return accountKind;
    }

    public void setAccountKind(String accountKind) {
        this.accountKind = accountKind;
    }

    public List<Mt5Position> getPositions() {
        return positions;
    }

    public void setPositions(List<Mt5Position> positions) {
        this.positions = positions;
    }
}
