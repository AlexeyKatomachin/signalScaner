package com.lavaksoft.bot.trade.mt5;

public class Mt5Position {
    private long ticket;
    private String symbol;
    private String direction;
    private double volume;
    private double entry;
    private double current;
    private double sl;
    private double tp;
    private double pl;

    public long getTicket() {
        return ticket;
    }

    public void setTicket(long ticket) {
        this.ticket = ticket;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public double getEntry() {
        return entry;
    }

    public void setEntry(double entry) {
        this.entry = entry;
    }

    public double getCurrent() {
        return current;
    }

    public void setCurrent(double current) {
        this.current = current;
    }

    public double getSl() {
        return sl;
    }

    public void setSl(double sl) {
        this.sl = sl;
    }

    public double getTp() {
        return tp;
    }

    public void setTp(double tp) {
        this.tp = tp;
    }

    public double getPl() {
        return pl;
    }

    public void setPl(double pl) {
        this.pl = pl;
    }
}
