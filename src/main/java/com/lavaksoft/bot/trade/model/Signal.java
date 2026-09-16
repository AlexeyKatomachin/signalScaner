package com.lavaksoft.bot.trade.model;

public class Signal {
    private Order order;

    public Signal(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}
