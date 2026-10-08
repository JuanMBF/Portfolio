package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.user.UserSummary;

import java.time.Instant;

public record CommentResponse(Long id, UserSummary author, String body, boolean internal, Instant createdAt) {

    public static CommentResponse from(TicketComment comment) {
        return new CommentResponse(comment.getId(), UserSummary.from(comment.getAuthor()),
                comment.getBody(), comment.isInternal(), comment.getCreatedAt());
    }
}
