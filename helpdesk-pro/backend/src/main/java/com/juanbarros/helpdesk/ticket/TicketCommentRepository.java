package com.juanbarros.helpdesk.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    @EntityGraph(attributePaths = "author")
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    @EntityGraph(attributePaths = "author")
    List<TicketComment> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);
}
