package com.juanbarros.helpdesk.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {

    @EntityGraph(attributePaths = "actor")
    List<TicketEvent> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
