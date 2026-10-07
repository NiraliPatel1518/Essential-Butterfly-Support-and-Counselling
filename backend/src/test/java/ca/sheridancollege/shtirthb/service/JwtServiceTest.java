package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import ca.sheridancollege.shtirthb.config.SecurityConfig;

/**
 * UC-01 / UC-03: Session/token handling
 * Checks that the token created at login can be read back by the
 * JwtDecoder in SecurityConfig. If someone changes the secret or the
 * algorithm in only one place, this test will fail.
 *
 * This file has 4 tests:
 *   Test 1 - A new token is accepted and holds the user's email
 *   Test 2 - A new token is valid for exactly 24 hours
 *   Test 3 - A changed (tampered) token is rejected
 *   Test 4 - A random string is rejected as a token
 */
class JwtServiceTest {

    // The real service that creates tokens at login
    private final JwtService jwtService = new JwtService();

    // The real decoder that checks tokens on every protected request
    private final JwtDecoder decoder = new SecurityConfig().jwtDecoder();

    /*
     * TEST 1: Token is accepted
     * Checks that:
     *   - A token made by JwtService can be read by the decoder
     *   - The token holds the correct email
     */
    @Test
    void generatedToken_isAcceptedByDecoder_andHoldsEmail() {
        // Arrange + Act: create a token and read it back
        String token = jwtService.generateToken("jane@mail.com");

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
        Jwt jwt = decoder.decode(jwtService.generateToken("jane@mail.com"));

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
        String token = jwtService.generateToken("jane@mail.com");
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
}