package com.juanbarros.helpdesk.ticket;

/** assigneeId en null significa "quitar asignación". */
public record AssignTicketRequest(Long assigneeId) {
}
