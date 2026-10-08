package com.juanbarros.helpdesk.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Trae solicitante y asignado en la misma consulta para evitar el problema N+1 en el listado. */
    @Override
    @EntityGraph(attributePaths = {"requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"requester", "assignee"})
    Optional<Ticket> findWithPeopleById(Long id);

    /** Cada fila es [TicketStatus, Long]. */
    @Query("select t.status, count(t) from Ticket t group by t.status")
    List<Object[]> countGroupedByStatus();

    /** Cada fila es [TicketPriority, Long]. */
    @Query("select t.priority, count(t) from Ticket t where t.status in :statuses group by t.priority")
    List<Object[]> countGroupedByPriority(@Param("statuses") Collection<TicketStatus> statuses);

    /** Cada fila es [TicketCategory, Long]. */
    @Query("select t.category, count(t) from Ticket t group by t.category")
    List<Object[]> countGroupedByCategory();

    long countByStatusIn(Collection<TicketStatus> statuses);

    long countByStatusInAndAssigneeIsNull(Collection<TicketStatus> statuses);

    long countByStatusInAndSlaDueAtBefore(Collection<TicketStatus> statuses, Instant instant);

    @Query("select t.createdAt as createdAt, t.resolvedAt as resolvedAt, t.slaDueAt as slaDueAt "
            + "from Ticket t where t.resolvedAt is not null")
    List<ResolutionTimes> findResolutionTimes();

    @Query("select t.createdAt from Ticket t where t.createdAt >= :since")
    List<Instant> findCreatedAtSince(@Param("since") Instant since);

    @Query("select a.id as agentId, a.fullName as agentName, count(t) as total "
            + "from Ticket t join t.assignee a where t.status in :statuses "
            + "group by a.id, a.fullName order by count(t) desc")
    List<AgentCount> countActiveByAgent(@Param("statuses") Collection<TicketStatus> statuses);

    interface ResolutionTimes {
        Instant getCreatedAt();

        Instant getResolvedAt();

        Instant getSlaDueAt();
    }

    interface AgentCount {
        Long getAgentId();

        String getAgentName();

        long getTotal();
    }
}
