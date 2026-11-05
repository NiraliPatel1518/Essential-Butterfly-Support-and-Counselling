package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import ca.sheridancollege.shtirthb.config.SecurityConfig;

/**
 * UC-01 / UC-03: Session/token handling
 * Checks that the token created at login can be read back by the
 * JwtDecoder in SecurityConfig. If someone changes the secret or the
 * algorithm in only one place, this test will fail.
 *
 * This file has 7 tests:
 *   Test 1 - A new token is accepted and holds the user's email
 *   Test 2 - A new token is valid for exactly 24 hours
 *   Test 3 - A changed (tampered) token is rejected
 *   Test 4 - A random string is rejected as a token
 *   Test 5 - A client token holds the role "CLIENT"
 *   Test 6 - An admin token holds the role "ADMIN"
 *   Test 7 - Spring Security reads the role as ROLE_ADMIN / ROLE_CLIENT
 */
class JwtServiceTest {

    // The real service that creates tokens at login
    private final JwtService jwtService = new JwtService();

    // The real security settings used by the app
    private final SecurityConfig securityConfig = new SecurityConfig();

    // The real decoder that checks tokens on every protected request
    private final JwtDecoder decoder = securityConfig.jwtDecoder();

    /*
     * TEST 1: Token is accepted
     * Checks that:
     *   - A token made by JwtService can be read by the decoder
     *   - The token holds the correct email
     */
    @Test
    void generatedToken_isAcceptedByDecoder_andHoldsEmail() {
        // Arrange + Act: create a token and read it back
        String token = jwtService.generateToken("jane@mail.com", "CLIENT");

        Jwt jwt = decoder.decode(token);

        // Assert: the email inside the token is correct
        assertEquals("jane@mail.com", jwt.getSubject());
    }

    /*
     * TEST 2: Token lifetime
     * Checks that:
     *   - The token is valid for exactly 24 hours
     *   - The token is not already expired
     */
    @Test
    void generatedToken_expiresIn24Hours() {
        // Arrange + Act: create a token and read it back
        Jwt jwt = decoder.decode(jwtService.generateToken("jane@mail.com", "CLIENT"));

        // Assert: time between "issued" and "expires" is 24 hours
        Duration life = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
        assertEquals(Duration.ofHours(24), life);
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }

    /*
     * TEST 3: Tampered token
     * Checks that:
     *   - If someone changes even the last 2 characters of a token,
     *     the decoder rejects it
     */
    @Test
    void tamperedToken_isRejected() {
        // Arrange: create a real token, then change its last 2 characters
        String token = jwtService.generateToken("jane@mail.com", "CLIENT");
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("A") ? "BB" : "AA");

        // Act + Assert: decoder throws an error
        assertThrows(JwtException.class, () -> decoder.decode(tampered));
    }

    /*
     * TEST 4: Fake token
     * Checks that:
     *   - A random string is not accepted as a token
     */
    @Test
    void randomString_isRejected() {
        // Act + Assert: decoder throws an error
        assertThrows(JwtException.class, () -> decoder.decode("not-a-real-token"));
    }

    /*
     * TEST 5: Client role
     * Checks that:
     *   - A token made for a client holds role = "CLIENT"
     */
    @Test
    void clientToken_holdsClientRole() {
        // Arrange + Act: create a client token and read it back
        Jwt jwt = decoder.decode(jwtService.generateToken("jane@mail.com", "CLIENT"));

        // Assert: the role is CLIENT
        assertEquals("CLIENT", jwt.getClaimAsString("role"));
    }

    /*
     * TEST 6: Admin role
     * Checks that:
     *   - A token made for an admin holds role = "ADMIN"
     */
    @Test
    void adminToken_holdsAdminRole() {
        // Arrange + Act: create an admin token and read it back
        Jwt jwt = decoder.decode(jwtService.generateToken("admin@test.com", "ADMIN"));

        // Assert: the role is ADMIN
        assertEquals("ADMIN", jwt.getClaimAsString("role"));
    }

    /*
     * TEST 7: Spring Security understands the role
     * Checks that:
     *   - An admin token becomes the permission "ROLE_ADMIN"
     *   - A client token becomes "ROLE_CLIENT" (and NOT "ROLE_ADMIN")
     * This is what lets SecurityConfig block clients from /api/admin/**
     */
    @Test
    void roleClaim_isConvertedToSpringRole() {
        // Arrange: the real converter from SecurityConfig
        JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

        Jwt adminJwt = decoder.decode(jwtService.generateToken("admin@test.com", "ADMIN"));
        Jwt clientJwt = decoder.decode(jwtService.generateToken("jane@mail.com", "CLIENT"));

        // Act: convert both tokens into logged-in users
        Authentication admin = converter.convert(adminJwt);
        Authentication client = converter.convert(clientJwt);

        List<String> adminRoles = admin.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        List<String> clientRoles = client.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        // Assert: each user gets the correct role only
        assertTrue(adminRoles.contains("ROLE_ADMIN"));
        assertTrue(clientRoles.contains("ROLE_CLIENT"));
        assertFalse(clientRoles.contains("ROLE_ADMIN"),
                "A client must never get admin permission");
    }
}
