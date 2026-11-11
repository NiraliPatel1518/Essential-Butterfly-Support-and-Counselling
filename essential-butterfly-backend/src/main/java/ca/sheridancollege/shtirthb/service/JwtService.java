
package ca.sheridancollege.shtirthb.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.config.AppProperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(AppProperties properties) {

        String secret = properties.getJwt().getSecret();

        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 48) {

            throw new IllegalStateException(
                    "JWT secret must contain at least 48 UTF-8 bytes."
            );
        }

        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(String email, String role) {

        Date now = new Date();

        Date expiration = new Date(
                now.getTime() + 1000L * 60 * 60 * 24
        );

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS384)
                .compact();
    }
}
