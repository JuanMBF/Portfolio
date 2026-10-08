package com.juanbarros.helpdesk.user;

import com.juanbarros.helpdesk.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserIntegrationTest extends IntegrationTestSupport {

    @Test
    void onlyAdminsCanListUsers() throws Exception {
        User admin = createUser("Carla Mendez", Role.ADMIN);
        User agent = createUser("Lucia Fernandez", Role.AGENT);

        getAs("/api/users", agent).andExpect(status().isForbidden());
        getAs("/api/users", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void agentsEndpointListsOnlyStaff() throws Exception {
        createUser("Carla Mendez", Role.ADMIN);
        User agent = createUser("Lucia Fernandez", Role.AGENT);
        createUser("Sofia Ramirez", Role.USER);

        getAs("/api/users/agents", agent)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void adminPromotesUserToAgent() throws Exception {
        User admin = createUser("Carla Mendez", Role.ADMIN);
        User user = createUser("Sofia Ramirez", Role.USER);

        patchAs("/api/users/" + user.getId() + "/role", Map.of("role", "AGENT"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    @Test
    void adminCannotChangeOwnRole() throws Exception {
        User admin = createUser("Carla Mendez", Role.ADMIN);

        patchAs("/api/users/" + admin.getId() + "/role", Map.of("role", "USER"), admin)
                .andExpect(status().isConflict());
    }
}
