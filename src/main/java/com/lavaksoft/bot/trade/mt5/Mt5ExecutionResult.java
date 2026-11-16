package com.lavaksoft.bot.trade.mt5;

public class Mt5ExecutionResult {
    private final String time;
    private final String status;
    private final String ticket;
    private final String message;

    public Mt5ExecutionResult(String time, String status, String ticket, String message) {
        this.time = time;
        this.status = status;
        this.ticket = ticket;
        this.message = message;
    }

    public String getTime() {
        return time;
    }

    public String getStatus() {
        return status;
    }

    public String getTicket() {
        return ticket;
    }

    public String getMessage() {
        return message;
    }
}
