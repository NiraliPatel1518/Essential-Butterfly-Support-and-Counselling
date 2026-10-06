package ca.sheridancollege.shtirthb.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.dto.*;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@Service
public class AuthService {

    private final ClientUserRepository clientUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            ClientUserRepository clientUserRepository,
            PasswordEncoder passwordEncoder) {

        this.clientUserRepository = clientUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void signup(SignupRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        if (clientUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String encodedPassword =
                passwordEncoder.encode(request.getPassword());

        ClientUser user = new ClientUser(
                request.getFullName().trim(),
                email,
                encodedPassword
        );

        clientUserRepository.save(user);
    }
    
    public boolean login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        ClientUser user = clientUserRepository
                .findByEmail(email)
                .orElse(null);

        if (user == null) {
            return false;
        }

        return passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        );
    }
}