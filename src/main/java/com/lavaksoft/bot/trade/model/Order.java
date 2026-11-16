package com.lavaksoft.bot.trade.model;

import com.lavaksoft.bot.trade.indicators.FibonacciRange;

public class Order {
    private double stopLoss;
    private double takeProfit;
    private double enterPrice;
    private double partialClosePrice;
    private double RR;
    private ActionType type;
    private boolean fulfilledByTake;
    private boolean fulfilledByStop;
    private boolean fulfilledByBU;
    private boolean needPartialClose;
    private boolean closed;
    private Setup setup;
    private boolean needNotifyClients = true;

    public Order(Setup setup, double enterPrice) {
        this.enterPrice = enterPrice;
        this.setup = setup;
        setup.setOrder(this);
        calculate();
    }

    private void calculate() {
        FibonacciRange fibonacciRangeMtf = setup.getFibonacciRangeMtf();
        takeProfit = fibonacciRangeMtf.getLayer141(setup.getTypeMtf());
        if (setup.getTypeMtf().equals(ActionType.LONG)) {
            stopLoss = fibonacciRangeMtf.getLow() - (fibonacciRangeMtf.getLow() * 0.02);
            RR = (takeProfit - enterPrice) / (enterPrice - stopLoss);
            partialClosePrice = fibonacciRangeMtf.getHigh();
        } else if (setup.getTypeMtf().equals(ActionType.SHORT)) {
            stopLoss = fibonacciRangeMtf.getHigh() + (fibonacciRangeMtf.getHigh() * 0.02);
            RR = (enterPrice - takeProfit) / (stopLoss - enterPrice);
            partialClosePrice = fibonacciRangeMtf.getLow();
        }
        type = setup.getTypeMtf();
    }

    public boolean isFulfilledByTake() {
        return fulfilledByTake;
    }

    public void setFulfilledByTake(boolean fulfilledByTake) {
        this.fulfilledByTake = fulfilledByTake;
    }

    public boolean isFulfilledByStop() {
        return fulfilledByStop;
    }

    public void setFulfilledByStop(boolean fulfilledByStop) {
        this.fulfilledByStop = fulfilledByStop;
    }

    public boolean isFulfilledByBU() {
        return fulfilledByBU;
    }

    public void setFulfilledByBU(boolean fulfilledByBU) {
        this.fulfilledByBU = fulfilledByBU;
    }

    public boolean isNeedPartialClose() {
        return needPartialClose;
    }

    public void setNeedPartialClose(boolean needPartialClose) {
        this.needPartialClose = needPartialClose;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getStopLoss() {
        return stopLoss;
    }

    public double getTakeProfit() {
        return takeProfit;
    }

    public double getEnterPrice() {
        return enterPrice;
    }

    public double getPartialClosePrice() {
        return partialClosePrice;
    }

    public double getRR() {
        return RR;
    }

    public ActionType getType() {
        return type;
    }

    public Setup getSetup() {
        return setup;
    }

    public boolean isNeedNotifyClients() {
        return needNotifyClients;
    }

    public void setNeedNotifyClients(boolean needNotifyClients) {
        this.needNotifyClients = needNotifyClients;
    }
}
