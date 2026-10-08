package com.juanbarros.helpdesk.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import com.juanbarros.helpdesk.IntegrationTestSupport;
import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketIntegrationTest extends IntegrationTestSupport {

    private User requester;
    private User otherUser;
    private User agent;

    @BeforeEach
    void setUpUsers() {
        requester = createUser("Sofia Ramirez", Role.USER);
        otherUser = createUser("Diego Perez", Role.USER);
        agent = createUser("Lucia Fernandez", Role.AGENT);
    }

    @Test
    void userCreatesTicketWithSlaBasedOnPriority() throws Exception {
        String body = postAs("/api/tickets", Map.of(
                "title", "No anda el WiFi",
                "description", "En la biblioteca no hay conexión",
                "category", "NETWORK",
                "priority", "HIGH"), requester)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requester.fullName").value("Sofia Ramirez"))
                .andExpect(jsonPath("$.assignee").isEmpty())
                .andExpect(jsonPath("$.slaBreached").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode ticket = readJson(body);
        Instant createdAt = Instant.parse(ticket.get("createdAt").asText());
        Instant slaDueAt = Instant.parse(ticket.get("slaDueAt").asText());
        assertThat(Duration.between(createdAt, slaDueAt)).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void createValidatesRequiredFields() throws Exception {
        postAs("/api/tickets", Map.of("title", ""), requester)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.category").exists())
                .andExpect(jsonPath("$.errors.priority").exists());
    }

    @Test
    void usersOnlySeeTheirOwnTickets() throws Exception {
        createTicket(requester, "Ticket de Sofia", "LOW");
        long othersTicket = createTicket(otherUser, "Ticket de Diego", "LOW");

        getAs("/api/tickets", requester)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Ticket de Sofia"));

        getAs("/api/tickets/" + othersTicket, requester)
                .andExpect(status().isNotFound());

        getAs("/api/tickets", agent)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchFiltersByTextStatusAndId() throws Exception {
        createTicket(requester, "Impresora sin tóner", "LOW");
        long vpnTicket = createTicket(requester, "VPN no conecta", "HIGH");
        patchAs("/api/tickets/" + vpnTicket + "/status", Map.of("status", "IN_PROGRESS"), agent);

        getAs("/api/tickets?q=IMPRESORA", agent)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Impresora sin tóner"));

        getAs("/api/tickets?status=IN_PROGRESS", agent)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(vpnTicket));

        perform(get("/api/tickets").param("q", "#" + vpnTicket), agent)
                .andExpect(jsonPath("$.content[0].id").value(vpnTicket));

        getAs("/api/tickets?mine=true", agent)
                .andExpect(jsonPath("$.content", hasSize(1)));

        getAs("/api/tickets?unassigned=true", agent)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Impresora sin tóner"));
    }

    @Test
    void listIsPaginated() throws Exception {
        for (int i = 1; i <= 3; i++) {
            createTicket(requester, "Ticket " + i, "LOW");
        }

        getAs("/api/tickets?size=2&page=1", requester)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void invalidFilterValueReturns400() throws Exception {
        getAs("/api/tickets?status=INVENTADO", agent)
                .andExpect(status().isBadRequest());
    }

    @Test
    void regularUsersCannotChangeStatus() throws Exception {
        long id = createTicket(requester, "Ticket", "LOW");

        patchAs("/api/tickets/" + id + "/status", Map.of("status", "IN_PROGRESS"), requester)
                .andExpect(status().isForbidden());
    }

    @Test
    void startingWorkAutoAssignsTheAgent() throws Exception {
        long id = createTicket(requester, "Ticket", "MEDIUM");

        patchAs("/api/tickets/" + id + "/status", Map.of("status", "IN_PROGRESS"), agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignee.fullName").value("Lucia Fernandez"));

        getAs("/api/tickets/" + id + "/events", requester)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("CREATED"))
                .andExpect(jsonPath("$[1].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[2].type").value("ASSIGNED"));
    }

    @Test
    void invalidTransitionReturns409() throws Exception {
        long id = createTicket(requester, "Ticket", "MEDIUM");

        patchAs("/api/tickets/" + id + "/status", Map.of("status", "RESOLVED"), agent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No se puede pasar de OPEN a RESOLVED"));
    }

    @Test
    void fullLifecycleUntilClosed() throws Exception {
        long id = createTicket(requester, "Ticket", "MEDIUM");

        for (String next : new String[]{"IN_PROGRESS", "RESOLVED", "CLOSED"}) {
            patchAs("/api/tickets/" + id + "/status", Map.of("status", next), agent)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next));
        }

        getAs("/api/tickets/" + id, requester)
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.allowedTransitions", hasSize(0)));

        postAs("/api/tickets/" + id + "/comments", Map.of("body", "¿Sigue abierto?"), requester)
                .andExpect(status().isConflict());
    }

    @Test
    void assignOnlyAcceptsStaff() throws Exception {
        long id = createTicket(requester, "Ticket", "LOW");

        patchAs("/api/tickets/" + id + "/assignee", Map.of("assigneeId", agent.getId()), agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(agent.getId()));

        patchAs("/api/tickets/" + id + "/assignee", Map.of("assigneeId", otherUser.getId()), agent)
                .andExpect(status().isConflict());

        Map<String, Object> unassign = new HashMap<>();
        unassign.put("assigneeId", null);
        patchAs("/api/tickets/" + id + "/assignee", unassign, agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").isEmpty());
    }

    @Test
    void changingPriorityRecalculatesSla() throws Exception {
        long id = createTicket(requester, "Ticket", "LOW");

        String body = patchAs("/api/tickets/" + id + "/priority", Map.of("priority", "CRITICAL"), agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andReturn().getResponse().getContentAsString();

        JsonNode ticket = readJson(body);
        assertThat(Duration.between(Instant.parse(ticket.get("createdAt").asText()),
                Instant.parse(ticket.get("slaDueAt").asText()))).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void internalNotesAreHiddenFromRequester() throws Exception {
        long id = createTicket(requester, "Ticket", "LOW");
        postAs("/api/tickets/" + id + "/comments", Map.of("body", "Respuesta pública"), agent)
                .andExpect(status().isCreated());
        postAs("/api/tickets/" + id + "/comments", Map.of("body", "Nota interna", "internal", true), agent)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true));

        getAs("/api/tickets/" + id + "/comments", requester)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("Respuesta pública"));

        getAs("/api/tickets/" + id + "/comments", agent)
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void requesterCannotWriteInternalNotes() throws Exception {
        long id = createTicket(requester, "Ticket", "LOW");

        postAs("/api/tickets/" + id + "/comments", Map.of("body", "Hola", "internal", true), requester)
                .andExpect(status().isForbidden());
    }

    @Test
    void usersCannotCommentOnOthersTickets() throws Exception {
        long id = createTicket(requester, "Ticket", "LOW");

        postAs("/api/tickets/" + id + "/comments", Map.of("body", "Hola"), otherUser)
                .andExpect(status().isNotFound());
    }

    @Test
    void requesterReplyMovesTicketBackToInProgress() throws Exception {
        long id = createTicket(requester, "Ticket", "MEDIUM");
        patchAs("/api/tickets/" + id + "/status", Map.of("status", "IN_PROGRESS"), agent);
        patchAs("/api/tickets/" + id + "/status", Map.of("status", "WAITING_ON_USER"), agent)
                .andExpect(jsonPath("$.status").value("WAITING_ON_USER"));

        postAs("/api/tickets/" + id + "/comments", Map.of("body", "Uso Chrome"), requester)
                .andExpect(status().isCreated());

        getAs("/api/tickets/" + id, requester)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        getAs("/api/tickets/999999", agent)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el ticket #999999"));
    }
}
