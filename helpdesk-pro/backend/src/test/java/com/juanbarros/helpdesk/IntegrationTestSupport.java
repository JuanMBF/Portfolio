package com.juanbarros.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juanbarros.helpdesk.security.JwtService;
import com.juanbarros.helpdesk.ticket.TicketCommentRepository;
import com.juanbarros.helpdesk.ticket.TicketEventRepository;
import com.juanbarros.helpdesk.ticket.TicketRepository;
import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import com.juanbarros.helpdesk.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Base para tests de integración: levanta la app completa contra H2 en memoria, sin datos de demo. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.enabled=false")
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TicketRepository ticketRepository;
    @Autowired
    protected TicketCommentRepository commentRepository;
    @Autowired
    protected TicketEventRepository eventRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        commentRepository.deleteAll();
        eventRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String name, Role role) {
        String email = name.toLowerCase().replace(' ', '.') + "@test.dev";
        return userRepository.save(new User(name, email, passwordEncoder.encode("Password123!"), role));
    }

    protected String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, User as) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(as)));
    }

    protected ResultActions getAs(String url, User as) throws Exception {
        return perform(get(url), as);
    }

    protected ResultActions postAs(String url, Object body, User as) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json(body)), as);
    }

    protected ResultActions patchAs(String url, Object body, User as) throws Exception {
        return perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json(body)), as);
    }

    protected long createTicket(User requester, String title, String priority) throws Exception {
        String response = postAs("/api/tickets", Map.of(
                "title", title,
                "description", "Descripción de " + title,
                "category", "SOFTWARE",
                "priority", priority), requester)
                .andReturn().getResponse().getContentAsString();
        return readJson(response).get("id").asLong();
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode readJson(String content) throws Exception {
        return objectMapper.readTree(content);
    }
}
