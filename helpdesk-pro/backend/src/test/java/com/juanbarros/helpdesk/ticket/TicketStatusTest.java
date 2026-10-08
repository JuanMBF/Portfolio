package com.juanbarros.helpdesk.ticket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusTest {

    @ParameterizedTest(name = "{0} → {1} permitido: {2}")
    @CsvSource({
            "OPEN, IN_PROGRESS, true",
            "OPEN, CLOSED, true",
            "OPEN, RESOLVED, false",
            "OPEN, WAITING_ON_USER, false",
            "IN_PROGRESS, WAITING_ON_USER, true",
            "IN_PROGRESS, RESOLVED, true",
            "IN_PROGRESS, OPEN, true",
            "IN_PROGRESS, CLOSED, false",
            "WAITING_ON_USER, IN_PROGRESS, true",
            "WAITING_ON_USER, RESOLVED, true",
            "WAITING_ON_USER, CLOSED, false",
            "RESOLVED, CLOSED, true",
            "RESOLVED, IN_PROGRESS, true",
            "RESOLVED, OPEN, false",
            "CLOSED, OPEN, false",
            "CLOSED, IN_PROGRESS, false"
    })
    void validatesTransitions(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void closedIsTerminal() {
        assertThat(TicketStatus.CLOSED.allowedTransitions()).isEmpty();
    }

    @Test
    void onlyOpenInProgressAndWaitingAreActive() {
        assertThat(TicketStatus.ACTIVE)
                .containsExactlyInAnyOrder(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_ON_USER);
    }

    @Test
    void allowedTransitionsReturnsDefensiveCopy() {
        TicketStatus.OPEN.allowedTransitions().clear();
        assertThat(TicketStatus.OPEN.allowedTransitions()).isNotEmpty();
    }
}
