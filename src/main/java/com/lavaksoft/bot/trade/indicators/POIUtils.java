package com.lavaksoft.bot.trade.indicators;

import com.lavaksoft.bot.trade.model.ActionType;
import com.lavaksoft.bot.trade.model.Candle;
import com.lavaksoft.bot.trade.model.FVG;
import com.lavaksoft.bot.trade.model.Liquidity;
import com.lavaksoft.bot.trade.model.POI;

import java.util.ArrayList;
import java.util.List;

public class POIUtils {
    public List<POI> findPOI(List<Candle> candles) {
        List<POI> POIs = new ArrayList<>();

        candles.stream()
                .filter(x -> x.getFractal() != null && x.getFractal().getLiquidity() != null && !x.getFractal().getLiquidity().isFulfilled())
                .forEach(x -> {
                    POI poi;
                    if (x.getFractal().isHigh()) {
                        poi = new POI(ActionType.SHORT, null, x.getFractal().getLiquidity());
                    } else {
                        poi = new POI(ActionType.LONG, null, x.getFractal().getLiquidity());
                    }
                    POIs.add(poi);
                });
        candles.stream()
                .filter(x -> x.getFvg() != null && !x.getFvg().isFulfilled())
                .forEach(x -> {
                    POI poi = new POI(x.getFvg().getType(), x.getFvg(), null);
                    POIs.add(poi);
                });

        return POIs;
    }

    public POI checkIsInPOI(Candle checkedCandle, List<Candle> candles) {
        FVG fvg = candles.stream()
                .map(Candle::getFvg)
                .filter(x -> x != null && !x.isFulfilled() && checkInRange(x.getHigh(), x.getLow(), checkedCandle)).findFirst()
                .orElse(null);
        if (fvg != null) {
            if ((fvg.getType().equals(ActionType.LONG) && fvg.getLow() > checkedCandle.getLow())
                    || (fvg.getType().equals(ActionType.SHORT) && fvg.getHigh() < checkedCandle.getHigh())) {
                fvg.setFulfilled(true);
            }
            fvg.setTested(true);
            return fvg.getPoi();
        }

        Liquidity liquidity = candles.stream()
                .filter(x -> x.getFractal() != null && x.getFractal().getLiquidity() != null && !x.getFractal().getLiquidity().isFulfilled())
                .map(x -> x.getFractal().getLiquidity())
                .filter(x -> (x.getFractal().isHigh() && x.getFractal().getCandle().getHigh() < checkedCandle.getHigh() && x.getFractal().getCandle().getHigh() > checkedCandle.getLow())
                        || (x.getFractal().isLow() && x.getFractal().getCandle().getLow() > checkedCandle.getLow() && x.getFractal().getCandle().getLow() < checkedCandle.getHigh()))
                .findFirst()
                .orElse(null);
        if (liquidity != null) {
            liquidity.setFulfilled(true);
            return liquidity.getPoi();
        }

        return null;
    }

    private boolean checkInRange(double high, double low, Candle lastCandle4H) {
        return high > lastCandle4H.getLow() && lastCandle4H.getHigh() > low;
    }
}
