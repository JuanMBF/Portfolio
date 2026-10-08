package com.juanbarros.helpdesk.security;

import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtZm9yLXVuaXQtdGVzdHMtb25seS0xMjM0NTY3ODkw";

    @Test
    void generatedTokenCanBeReadBack() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

        String token = jwtService.generateToken(userWithId(42L));

        assertThat(jwtService.extractUserId(token)).contains(42L);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));
        String token = jwtService.generateToken(userWithId(42L));
        int index = token.lastIndexOf('.') + 5;
        char replacement = token.charAt(index) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, index) + replacement + token.substring(index + 1);

        assertThat(jwtService.extractUserId(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService issuer = new JwtService(new JwtProperties(SECRET, 60));
        JwtService other = new JwtService(new JwtProperties(
                "b3RyYS1jbGF2ZS1jb21wbGV0YW1lbnRlLWRpc3RpbnRhLXBhcmEtZmlybWFy", 60));

        assertThat(other.extractUserId(issuer.generateToken(userWithId(1L)))).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, -1));

        assertThat(jwtService.extractUserId(jwtService.generateToken(userWithId(7L)))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

        assertThat(jwtService.extractUserId("esto-no-es-un-jwt")).isEmpty();
    }

    private static User userWithId(Long id) {
        User user = new User("Test", "test@test.dev", "hash", Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
