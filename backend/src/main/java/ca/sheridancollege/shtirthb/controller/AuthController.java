package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.dto.*;
import ca.sheridancollege.shtirthb.service.AuthService;
import ca.sheridancollege.shtirthb.service.JwtService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            JwtService jwtService) {

        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(
            @Valid @RequestBody SignupRequest request) {

        try {
            authService.signup(request);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Account created successfully");

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request) {

        boolean authenticated = authService.login(request);

        if (!authenticated) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid email or password");
        }

        String token = jwtService.generateToken(request.getEmail());

        return ResponseEntity.ok(token);
    }
}