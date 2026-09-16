package com.lavaksoft.bot.trade.model;

import com.lavaksoft.bot.trade.indicators.FibonacciRange;

public class Setup {
    private boolean sweepLiquidity = false;
    private Liquidity liquidity;
    private boolean sweepHtfLiquidity = false;
    private Liquidity htfLiquidity;
    private boolean bosLtf = false;
    private boolean fvgGrab = false;
    private boolean fvgHtfGrab = false;
    private FVG fvg;
    private FVG htfFvg;
    private ActionType typeMtf;
    private FibonacciRange fibonacciRangeMtf;
    private ActionType typeHtf;
    private FibonacciRange fibonacciRangeHtf;
    private String tradingPare;
    private Order order;

    public Setup(String tradingPare) {
        this.tradingPare = tradingPare;
    }

    public boolean isSweepLiquidity() {
        return sweepLiquidity;
    }

    public void setSweepLiquidity(boolean sweepLiquidity) {
        this.sweepLiquidity = sweepLiquidity;
    }

    public boolean isBosLtf() {
        return bosLtf;
    }

    public void setBosLtf(boolean bosLtf) {
        this.bosLtf = bosLtf;
    }

    public boolean isFvgGrab() {
        return fvgGrab;
    }

    public void setFvgGrab(boolean fvgGrab) {
        this.fvgGrab = fvgGrab;
    }

    public Liquidity getLiquidity() {
        return liquidity;
    }

    public void setLiquidity(Liquidity liquidity) {
        this.liquidity = liquidity;
    }

    public FVG getFvg() {
        return fvg;
    }

    public void setFvg(FVG fvg) {
        this.fvg = fvg;
    }

    public ActionType getTypeMtf() {
        return typeMtf;
    }

    public void setTypeMtf(ActionType typeMtf) {
        this.typeMtf = typeMtf;
    }

    public boolean isSweepHtfLiquidity() {
        return sweepHtfLiquidity;
    }

    public void setSweepHtfLiquidity(boolean sweepHtfLiquidity) {
        this.sweepHtfLiquidity = sweepHtfLiquidity;
    }

    public Liquidity getHtfLiquidity() {
        return htfLiquidity;
    }

    public void setHtfLiquidity(Liquidity htfLiquidity) {
        this.htfLiquidity = htfLiquidity;
    }

    public boolean isFvgHtfGrab() {
        return fvgHtfGrab;
    }

    public void setFvgHtfGrab(boolean fvgHtfGrab) {
        this.fvgHtfGrab = fvgHtfGrab;
    }

    public FVG getHtfFvg() {
        return htfFvg;
    }

    public void setHtfFvg(FVG htfFvg) {
        this.htfFvg = htfFvg;
    }

    public FibonacciRange getFibonacciRangeMtf() {
        return fibonacciRangeMtf;
    }

    public void setFibonacciRangeMtf(FibonacciRange fibonacciRangeMtf) {
        this.fibonacciRangeMtf = fibonacciRangeMtf;
    }

    public ActionType getTypeHtf() {
        return typeHtf;
    }

    public void setTypeHtf(ActionType typeHtf) {
        this.typeHtf = typeHtf;
    }

    public FibonacciRange getFibonacciRangeHtf() {
        return fibonacciRangeHtf;
    }

    public void setFibonacciRangeHtf(FibonacciRange fibonacciRangeHtf) {
        this.fibonacciRangeHtf = fibonacciRangeHtf;
    }

    public String getTradingPare() {
        return tradingPare;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}
