package com.lavaksoft.bot.trade.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class Candle {
    private long time;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private FVG fvg;
    private Fractal fractal;

    public Candle(long time, double open, double high, double low, double close, double volume, FVG fvg, Fractal fractal) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.fvg = fvg;
        this.fractal = fractal;
    }

    public Candle(long time, double open, double high, double low, double close, double volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public LocalDateTime getDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
    }

    public double getMidPrice() {
        return (high + low) / 2;
    }

    // Геттеры и сеттеры (оставлены как у вас, без изменений)
    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public FVG getFvg() {
        return fvg;
    }

    public void setFvg(FVG fvg) {
        this.fvg = fvg;
    }

    public Fractal getFractal() {
        return fractal;
    }

    public void setFractal(Fractal fractal) {
        this.fractal = fractal;
    }
}
