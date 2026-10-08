package com.juanbarros.helpdesk.ticket;

import java.time.Duration;

/** Prioridad del ticket y su tiempo máximo de resolución (SLA). */
public enum TicketPriority {
    LOW(Duration.ofHours(72)),
    MEDIUM(Duration.ofHours(24)),
    HIGH(Duration.ofHours(8)),
    CRITICAL(Duration.ofHours(4));

    private final Duration sla;

    TicketPriority(Duration sla) {
        this.sla = sla;
    }

    public Duration getSla() {
        return sla;
    }
}
