
package ca.sheridancollege.shtirthb.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;

@Service
public class AuthService {

    private final ClientUserRepository clientUserRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthService adminAuthService;

    public AuthService(
            ClientUserRepository clientUserRepository,
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            AdminAuthService adminAuthService) {

        this.clientUserRepository = clientUserRepository;
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAuthService = adminAuthService;
    }

    public void signup(SignupRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (clientUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        ClientUser user = new ClientUser(
                request.getFullName().trim(),
                email,
                encodedPassword);

        clientUserRepository.save(user);
    }

    public AuthenticatedAccount authenticate(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        Optional<ClientUser> client = clientUserRepository.findByEmail(email);
        Optional<AdminUser> admin = adminUserRepository.findByEmail(email);

        if (client.isPresent() && admin.isPresent()) {
            throw new IllegalStateException(
                    "This email is associated with multiple account types. "
                    + "Please contact the administrator.");
        }

        if (admin.isPresent()) {
            AdminUser authenticatedAdmin = adminAuthService.authenticate(
                    email, request.getPassword());

            if (authenticatedAdmin == null) {
                return null;
            }

            return new AuthenticatedAccount(
                    authenticatedAdmin.getEmail(), "ADMIN");
        }

        if (client.isPresent()) {
            ClientUser clientUser = client.get();

            if (!passwordEncoder.matches(
                    request.getPassword(), clientUser.getPassword())) {
                return null;
            }

            return new AuthenticatedAccount(clientUser.getEmail(), "CLIENT");
        }

        return null;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthenticatedAccount(String email, String role) {
    }
}
