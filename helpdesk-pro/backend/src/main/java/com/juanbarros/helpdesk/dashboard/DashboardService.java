package com.juanbarros.helpdesk.dashboard;

import com.juanbarros.helpdesk.dashboard.DashboardResponse.AgentWorkload;
import com.juanbarros.helpdesk.dashboard.DashboardResponse.DailyCount;
import com.juanbarros.helpdesk.ticket.TicketCategory;
import com.juanbarros.helpdesk.ticket.TicketPriority;
import com.juanbarros.helpdesk.ticket.TicketRepository;
import com.juanbarros.helpdesk.ticket.TicketRepository.ResolutionTimes;
import com.juanbarros.helpdesk.ticket.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int TREND_DAYS = 7;

    private final TicketRepository ticketRepository;
    private final Clock clock;

    public DashboardService(TicketRepository ticketRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        Instant now = clock.instant();

        Map<TicketStatus, Long> byStatus = toEnumMap(TicketStatus.class, ticketRepository.countGroupedByStatus());
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        List<ResolutionTimes> resolved = ticketRepository.findResolutionTimes();

        return new DashboardResponse(
                total,
                ticketRepository.countByStatusIn(TicketStatus.ACTIVE),
                ticketRepository.countByStatusInAndAssigneeIsNull(TicketStatus.ACTIVE),
                ticketRepository.countByStatusInAndSlaDueAtBefore(TicketStatus.ACTIVE, now),
                averageResolutionHours(resolved),
                slaComplianceRate(resolved),
                byStatus,
                toEnumMap(TicketPriority.class, ticketRepository.countGroupedByPriority(TicketStatus.ACTIVE)),
                toEnumMap(TicketCategory.class, ticketRepository.countGroupedByCategory()),
                ticketRepository.countActiveByAgent(TicketStatus.ACTIVE).stream()
                        .map(row -> new AgentWorkload(row.getAgentId(), row.getAgentName(), row.getTotal()))
                        .toList(),
                createdPerDay(now));
    }

    /** Promedio de horas entre creación y resolución, redondeado a un decimal. */
    static Double averageResolutionHours(List<ResolutionTimes> resolved) {
        if (resolved.isEmpty()) {
            return null;
        }
        double avgMinutes = resolved.stream()
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getResolvedAt()).toMinutes())
                .average()
                .orElse(0);
        return Math.round(avgMinutes / 60.0 * 10) / 10.0;
    }

    /** Porcentaje de tickets resueltos dentro del SLA, redondeado a un decimal. */
    static Double slaComplianceRate(List<ResolutionTimes> resolved) {
        if (resolved.isEmpty()) {
            return null;
        }
        long onTime = resolved.stream().filter(r -> !r.getResolvedAt().isAfter(r.getSlaDueAt())).count();
        return Math.round(onTime * 1000.0 / resolved.size()) / 10.0;
    }

    private List<DailyCount> createdPerDay(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate firstDay = today.minusDays(TREND_DAYS - 1);

        Map<LocalDate, Long> counts = ticketRepository
                .findCreatedAtSince(firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)).stream()
                .collect(Collectors.groupingBy(createdAt -> LocalDate.ofInstant(createdAt, ZoneOffset.UTC),
                        TreeMap::new, Collectors.counting()));

        List<DailyCount> result = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(today); day = day.plusDays(1)) {
            result.add(new DailyCount(day, counts.getOrDefault(day, 0L)));
        }
        return result;
    }

    /** Arma un mapa con todas las constantes del enum (en 0 si no hay datos) para que el frontend no tenga huecos. */
    private static <E extends Enum<E>> Map<E, Long> toEnumMap(Class<E> type, List<Object[]> rows) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E constant : type.getEnumConstants()) {
            map.put(constant, 0L);
        }
        Map<E, Long> found = rows.stream()
                .collect(Collectors.toMap(row -> type.cast(row[0]), row -> (Long) row[1]));
        map.putAll(found);
        return map;
    }
}
