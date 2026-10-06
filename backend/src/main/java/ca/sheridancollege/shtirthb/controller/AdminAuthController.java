package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.service.AdminAuthService;
import ca.sheridancollege.shtirthb.service.JwtService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final JwtService jwtService;

    public AdminAuthController(
            AdminAuthService adminAuthService,
            JwtService jwtService) {

        this.adminAuthService = adminAuthService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request) {

        AdminUser admin = adminAuthService.authenticate(
                request.getEmail(),
                request.getPassword()
        );

        if (admin == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid admin email or password");
        }

        String token = jwtService.generateToken(admin.getEmail());

        return ResponseEntity.ok(token);
    }
}