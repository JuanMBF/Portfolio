package com.juanbarros.helpdesk.auth;

import com.juanbarros.helpdesk.IntegrationTestSupport;
import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    void registerCreatesUserWithUserRoleAndReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Nueva Persona",
                                "email", "Nueva@Test.dev",
                                "password", "Password123!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("nueva@test.dev"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void registerRejectsDuplicatedEmail() throws Exception {
        createUser("Ana Gomez", Role.USER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Otra Ana",
                                "email", "ana.gomez@test.dev",
                                "password", "Password123!"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con ese email"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "", "email", "no-es-email", "password", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerRejectsPasswordLongerThanBcryptAccepts() throws Exception {
        // 37 caracteres pero 74 bytes: BCrypt ignoraba lo que pasaba de 72 y entraba cualquier
        // contraseña que empezara con las mismas 36 eñes.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Nueva Persona",
                                "email", "nueva@test.dev",
                                "password", "ñ".repeat(37)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(
                        "La contraseña es demasiado larga (la ñ y las letras con acento ocupan el doble)"));
    }

    @Test
    void registerAcceptsPasswordOfExactlyBcryptLimit() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Nueva Persona",
                                "email", "nueva@test.dev",
                                "password", "ñ".repeat(36)))))
                .andExpect(status().isCreated());
    }

    @Test
    void loginReturnsTokenThatAuthenticatesRequests() throws Exception {
        createUser("Ana Gomez", Role.AGENT);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana.gomez@test.dev", "password", "Password123!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = readJson(body).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Gomez"))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        createUser("Ana Gomez", Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana.gomez@test.dev", "password", "incorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Credenciales inválidas"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer token.invalido.123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserIsNoLongerValid() throws Exception {
        User user = createUser("Ana Gomez", Role.USER);
        String token = tokenFor(user);
        userRepository.delete(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
