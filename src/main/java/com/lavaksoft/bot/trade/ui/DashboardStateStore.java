package com.lavaksoft.bot.trade.ui;

import java.util.concurrent.atomic.AtomicReference;

public class DashboardStateStore {
    private final AtomicReference<DashboardState> state = new AtomicReference<>(new DashboardState());

    public DashboardState get() {
        return state.get();
    }

    public void set(DashboardState newState) {
        state.set(newState);
    }
}
