package com.juanbarros.helpdesk.ticket;

import jakarta.validation.constraints.NotNull;

public record UpdatePriorityRequest(@NotNull(message = "La prioridad es obligatoria") TicketPriority priority) {
}
