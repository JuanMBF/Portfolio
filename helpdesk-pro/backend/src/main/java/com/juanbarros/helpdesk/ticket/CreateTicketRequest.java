package com.juanbarros.helpdesk.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título no puede superar los 150 caracteres") String title,
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 4000, message = "La descripción no puede superar los 4000 caracteres") String description,
        @NotNull(message = "La categoría es obligatoria") TicketCategory category,
        @NotNull(message = "La prioridad es obligatoria") TicketPriority priority) {
}
