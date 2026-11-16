package com.lavaksoft.bot.trade.model;

public class FvgConfluence {
    private final FVG fvg;
    private final boolean overlapsOrderBlock;

    public FvgConfluence(FVG fvg, boolean overlapsOrderBlock) {
        this.fvg = fvg;
        this.overlapsOrderBlock = overlapsOrderBlock;
    }

    public FVG getFvg() {
        return fvg;
    }

    public boolean isOverlapsOrderBlock() {
        return overlapsOrderBlock;
    }
}
