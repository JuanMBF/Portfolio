package com.juanbarros.helpdesk.dashboard;

import com.juanbarros.helpdesk.IntegrationTestSupport;
import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIntegrationTest extends IntegrationTestSupport {

    @Test
    void regularUsersCannotSeeDashboard() throws Exception {
        User user = createUser("Sofia Ramirez", Role.USER);

        getAs("/api/dashboard", user).andExpect(status().isForbidden());
    }

    @Test
    void dashboardAggregatesTickets() throws Exception {
        User user = createUser("Sofia Ramirez", Role.USER);
        User agent = createUser("Lucia Fernandez", Role.AGENT);

        createTicket(user, "Abierto sin asignar", "HIGH");
        long inProgress = createTicket(user, "En curso", "LOW");
        long resolved = createTicket(user, "Resuelto", "CRITICAL");
        patchAs("/api/tickets/" + inProgress + "/status", Map.of("status", "IN_PROGRESS"), agent);
        patchAs("/api/tickets/" + resolved + "/status", Map.of("status", "IN_PROGRESS"), agent);
        patchAs("/api/tickets/" + resolved + "/status", Map.of("status", "RESOLVED"), agent);

        getAs("/api/dashboard", agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(3))
                .andExpect(jsonPath("$.activeTickets").value(2))
                .andExpect(jsonPath("$.unassignedTickets").value(1))
                .andExpect(jsonPath("$.slaBreachedActive").value(0))
                .andExpect(jsonPath("$.slaComplianceRate").value(100.0))
                .andExpect(jsonPath("$.byStatus.OPEN").value(1))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.byStatus.RESOLVED").value(1))
                .andExpect(jsonPath("$.byStatus.CLOSED").value(0))
                .andExpect(jsonPath("$.activeByPriority.HIGH").value(1))
                .andExpect(jsonPath("$.activeByPriority.CRITICAL").value(0))
                .andExpect(jsonPath("$.byCategory.SOFTWARE").value(3))
                .andExpect(jsonPath("$.agentWorkload", hasSize(1)))
                .andExpect(jsonPath("$.agentWorkload[0].agentName").value("Lucia Fernandez"))
                .andExpect(jsonPath("$.agentWorkload[0].activeTickets").value(1))
                .andExpect(jsonPath("$.createdLast7Days", hasSize(7)))
                .andExpect(jsonPath("$.createdLast7Days[6].count").value(3));
    }
}
