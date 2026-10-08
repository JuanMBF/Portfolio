package com.juanbarros.helpdesk.ticket;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull(message = "El estado es obligatorio") TicketStatus status) {
}
