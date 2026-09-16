package com.lavaksoft.bot.trade.model;

public class POI {
    private ActionType type;
    private FVG fvg;
    private Liquidity liquidity;
    private double value;

    public POI(ActionType type, FVG fvg, Liquidity liquidity) {
        this.type = type;
        setFvg(fvg);
        setLiquidity(liquidity);
    }

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public FVG getFvg() {
        return fvg;
    }

    public void setFvg(FVG fvg) {
        this.fvg = fvg;
        if (fvg != null && fvg.getType().equals(type)) {
            fvg.setPoi(this);
            if (type.equals(ActionType.LONG)) {
                value = fvg.getHigh();
            } else {
                value = fvg.getLow();
            }
        }
    }

    public Liquidity getLiquidity() {
        return liquidity;
    }

    public void setLiquidity(Liquidity liquidity) {
        this.liquidity = liquidity;
        if (liquidity != null) {
            liquidity.setPoi(this);
            if (type.equals(ActionType.LONG)) {
                value = liquidity.getFractal().getCandle().getLow();
            } else {
                value = liquidity.getFractal().getCandle().getHigh();
            }
        }
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
