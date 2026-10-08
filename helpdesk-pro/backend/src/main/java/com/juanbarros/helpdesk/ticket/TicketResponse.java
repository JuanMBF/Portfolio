package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.user.UserSummary;

import java.time.Instant;
import java.util.Set;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        UserSummary requester,
        UserSummary assignee,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant slaDueAt,
        boolean slaBreached,
        Set<TicketStatus> allowedTransitions) {

    public static TicketResponse from(Ticket ticket, Instant now) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                UserSummary.from(ticket.getRequester()),
                UserSummary.from(ticket.getAssignee()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getSlaDueAt(),
                ticket.isSlaBreached(now),
                ticket.getStatus().allowedTransitions());
    }
}
