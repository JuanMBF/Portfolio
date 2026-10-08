package com.juanbarros.helpdesk.ticket;

import com.juanbarros.helpdesk.common.BusinessRuleException;
import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    private static final Instant CREATED = Instant.parse("2026-03-02T09:00:00Z");

    private final User requester = new User("Ana", "ana@test.dev", "hash", Role.USER);
    private final User agent = new User("Leo", "leo@test.dev", "hash", Role.AGENT);

    @Test
    void newTicketStartsOpenWithSlaFromPriority() {
        Ticket ticket = ticket(TicketPriority.HIGH);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getSlaDueAt()).isEqualTo(CREATED.plus(Duration.ofHours(8)));
    }

    @Test
    void resolvingSetsResolvedAtAndReopeningClearsIt() {
        Ticket ticket = ticket(TicketPriority.MEDIUM);
        Instant resolvedAt = CREATED.plus(Duration.ofHours(3));

        ticket.changeStatus(TicketStatus.IN_PROGRESS, CREATED.plus(Duration.ofHours(1)));
        ticket.changeStatus(TicketStatus.RESOLVED, resolvedAt);
        assertThat(ticket.getResolvedAt()).isEqualTo(resolvedAt);

        ticket.changeStatus(TicketStatus.IN_PROGRESS, resolvedAt.plus(Duration.ofHours(1)));
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void closingKeepsResolvedAt() {
        Ticket ticket = ticket(TicketPriority.MEDIUM);
        Instant resolvedAt = CREATED.plus(Duration.ofHours(3));
        ticket.changeStatus(TicketStatus.IN_PROGRESS, CREATED);
        ticket.changeStatus(TicketStatus.RESOLVED, resolvedAt);

        ticket.changeStatus(TicketStatus.CLOSED, resolvedAt.plus(Duration.ofDays(1)));

        assertThat(ticket.getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void rejectsInvalidTransition() {
        Ticket ticket = ticket(TicketPriority.LOW);

        assertThatThrownBy(() -> ticket.changeStatus(TicketStatus.RESOLVED, CREATED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("OPEN a RESOLVED");
    }

    @Test
    void rejectsTransitionToSameStatus() {
        Ticket ticket = ticket(TicketPriority.LOW);

        assertThatThrownBy(() -> ticket.changeStatus(TicketStatus.OPEN, CREATED))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void activeTicketBreachesSlaOnlyAfterDueDate() {
        Ticket ticket = ticket(TicketPriority.CRITICAL);

        assertThat(ticket.isSlaBreached(CREATED.plus(Duration.ofHours(4)))).isFalse();
        assertThat(ticket.isSlaBreached(CREATED.plus(Duration.ofHours(4)).plusSeconds(1))).isTrue();
    }

    @Test
    void resolvedTicketIsJudgedByResolutionTimeNotByNow() {
        Ticket onTime = ticket(TicketPriority.CRITICAL);
        onTime.changeStatus(TicketStatus.IN_PROGRESS, CREATED);
        onTime.changeStatus(TicketStatus.RESOLVED, CREATED.plus(Duration.ofHours(2)));

        Ticket late = ticket(TicketPriority.CRITICAL);
        late.changeStatus(TicketStatus.IN_PROGRESS, CREATED);
        late.changeStatus(TicketStatus.RESOLVED, CREATED.plus(Duration.ofHours(5)));

        Instant muchLater = CREATED.plus(Duration.ofDays(30));
        assertThat(onTime.isSlaBreached(muchLater)).isFalse();
        assertThat(late.isSlaBreached(muchLater)).isTrue();
    }

    @Test
    void cancelledTicketDoesNotCountAsBreach() {
        Ticket ticket = ticket(TicketPriority.CRITICAL);
        ticket.changeStatus(TicketStatus.CLOSED, CREATED.plus(Duration.ofHours(10)));

        assertThat(ticket.isSlaBreached(CREATED.plus(Duration.ofDays(2)))).isFalse();
    }

    @Test
    void changingPriorityRecalculatesSlaFromCreation() {
        Ticket ticket = ticket(TicketPriority.LOW);

        ticket.changePriority(TicketPriority.CRITICAL, CREATED.plus(Duration.ofHours(1)));

        assertThat(ticket.getSlaDueAt()).isEqualTo(CREATED.plus(Duration.ofHours(4)));
    }

    @Test
    void canOnlyBeAssignedToStaff() {
        Ticket ticket = ticket(TicketPriority.LOW);

        ticket.assignTo(agent, CREATED);
        assertThat(ticket.getAssignee()).isEqualTo(agent);

        assertThatThrownBy(() -> ticket.assignTo(requester, CREATED))
                .isInstanceOf(BusinessRuleException.class);
    }

    private Ticket ticket(TicketPriority priority) {
        return new Ticket("Título", "Descripción", TicketCategory.SOFTWARE, priority, requester, CREATED);
    }
}
