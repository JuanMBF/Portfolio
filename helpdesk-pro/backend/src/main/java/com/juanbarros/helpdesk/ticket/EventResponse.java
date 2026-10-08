package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.user.UserSummary;

import java.time.Instant;

public record EventResponse(Long id, UserSummary actor, TicketEventType type, String detail, Instant createdAt) {

    public static EventResponse from(TicketEvent event) {
        return new EventResponse(event.getId(), UserSummary.from(event.getActor()),
                event.getType(), event.getDetail(), event.getCreatedAt());
    }
}
