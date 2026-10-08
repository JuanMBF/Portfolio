package com.juanbarros.helpdesk.dashboard;

import com.juanbarros.helpdesk.ticket.TicketCategory;
import com.juanbarros.helpdesk.ticket.TicketPriority;
import com.juanbarros.helpdesk.ticket.TicketStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        long totalTickets,
        long activeTickets,
        long unassignedTickets,
        long slaBreachedActive,
        Double averageResolutionHours,
        Double slaComplianceRate,
        Map<TicketStatus, Long> byStatus,
        Map<TicketPriority, Long> activeByPriority,
        Map<TicketCategory, Long> byCategory,
        List<AgentWorkload> agentWorkload,
        List<DailyCount> createdLast7Days) {

    public record AgentWorkload(Long agentId, String agentName, long activeTickets) {
    }

    public record DailyCount(LocalDate date, long count) {
    }
}
