package ca.sheridancollege.shtirthb.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.service.AuthService;
import ca.sheridancollege.shtirthb.service.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final ClientUserRepository clientUserRepository;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            ClientUserRepository clientUserRepository) {

        this.authService = authService;
        this.jwtService = jwtService;
        this.clientUserRepository = clientUserRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(
            @Valid @RequestBody SignupRequest request) {

        authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Account created successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request) {

        AuthService.AuthenticatedAccount account =
                authService.authenticate(request);

        if (account == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid email or password");
        }

        String token = jwtService.generateToken(
                account.email(),
                account.role());

        return ResponseEntity.ok(token);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getCurrentUser(
            Authentication authentication) {

        String email = authentication.getName();

        Optional<ClientUser> userOptional =
                clientUserRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ClientUser user = userOptional.get();

        return ResponseEntity.ok(
                Map.of("fullName", user.getFullName())
        );
    }
}