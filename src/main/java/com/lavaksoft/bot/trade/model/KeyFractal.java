package com.lavaksoft.bot.trade.model;

public class KeyFractal {
    private Fractal fractal;
    private KeyFractalType type;
    private boolean hasBos;

    public KeyFractal(Fractal fractal, KeyFractalType type) {
        this.fractal = fractal;
        this.type = type;
        fractal.setKeyFractal(this);
    }

    public Fractal getFractal() {
        return fractal;
    }

    public void setFractal(Fractal fractal) {
        this.fractal = fractal;
    }

    public KeyFractalType getType() {
        return type;
    }

    public void setType(KeyFractalType type) {
        this.type = type;
    }

    public boolean isHasBos() {
        return hasBos;
    }

    public void setHasBos(boolean hasBos) {
        this.hasBos = hasBos;
    }
}
