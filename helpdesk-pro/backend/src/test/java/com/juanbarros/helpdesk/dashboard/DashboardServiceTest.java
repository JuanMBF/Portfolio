package com.juanbarros.helpdesk.dashboard;

import com.juanbarros.helpdesk.ticket.TicketRepository.ResolutionTimes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-02T09:00:00Z");

    @Test
    void metricsAreNullWhenNothingWasResolved() {
        assertThat(DashboardService.averageResolutionHours(List.of())).isNull();
        assertThat(DashboardService.slaComplianceRate(List.of())).isNull();
    }

    @Test
    void computesAverageResolutionHours() {
        List<ResolutionTimes> resolved = List.of(
                times(Duration.ofHours(2), Duration.ofHours(8)),
                times(Duration.ofHours(5), Duration.ofHours(8)));

        assertThat(DashboardService.averageResolutionHours(resolved)).isEqualTo(3.5);
    }

    @Test
    void computesSlaComplianceRate() {
        List<ResolutionTimes> resolved = List.of(
                times(Duration.ofHours(2), Duration.ofHours(8)),
                times(Duration.ofHours(8), Duration.ofHours(8)),
                times(Duration.ofHours(9), Duration.ofHours(8)));

        assertThat(DashboardService.slaComplianceRate(resolved)).isEqualTo(66.7);
    }

    private static ResolutionTimes times(Duration resolvedAfter, Duration sla) {
        return new ResolutionTimes() {
            @Override
            public Instant getCreatedAt() {
                return T0;
            }

            @Override
            public Instant getResolvedAt() {
                return T0.plus(resolvedAfter);
            }

            @Override
            public Instant getSlaDueAt() {
                return T0.plus(sla);
            }
        };
    }
}
