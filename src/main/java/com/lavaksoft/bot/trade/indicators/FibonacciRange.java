package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;

public class FibonacciRange {
    private double high, low, layer141, layer161;

    public FibonacciRange(double high, double low) {
        this.high = high;
        this.low = low;
    }

    public boolean isDiscount(double price) {
        return price <= low + 0.5 * (high - low);
    }

    public boolean isPremium(double price) {
        return price >= low + 0.5 * (high - low);
    }

    public double getLayer141(ActionType type) {
        double value = (high - low) * (1.41d);
        if (type.equals(ActionType.LONG)) {
            layer141 = high + value;
        } else {
            layer141 = low - value;
        }
        return layer141;
    }

    public double getLayer161(ActionType type) {
        double value = (high - low) * (1.61d);
        if (type.equals(ActionType.LONG)) {
            layer161 = high + value;
        } else {
            layer161 = low - value;
        }
        return layer161;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }
}