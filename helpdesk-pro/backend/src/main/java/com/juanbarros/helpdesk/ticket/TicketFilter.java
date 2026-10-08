package com.juanbarros.helpdesk.ticket;

/**
 * Filtros opcionales del listado. Spring los completa a partir de los query params
 * (?status=OPEN&amp;priority=HIGH&amp;q=impresora...). Los flags ausentes se toman como false.
 */
public record TicketFilter(
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        Long assigneeId,
        Boolean unassigned,
        Boolean mine,
        String q) {

    public TicketFilter {
        unassigned = Boolean.TRUE.equals(unassigned);
        mine = Boolean.TRUE.equals(mine);
    }
}
