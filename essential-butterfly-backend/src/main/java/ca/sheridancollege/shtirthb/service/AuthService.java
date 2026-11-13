package ca.sheridancollege.shtirthb.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.LoginRequest;
import ca.sheridancollege.shtirthb.dto.SignupRequest;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
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
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            ClientUserRepository clientUserRepository,
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            AdminAuthService adminAuthService,
            EmailVerificationService emailVerificationService) {

        this.clientUserRepository = clientUserRepository;
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAuthService = adminAuthService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public void signup(SignupRequest request) {

        String email = normalizeEmail(request.getEmail());

        /*
         * One address can belong to only one account across the whole
         * system: administrators, clients and pending e-mail changes all
         * share a single uniqueness rule (account_emails.email).
         */
        if (clientUserRepository.existsByEmail(email)
                || adminUserRepository.existsByEmail(email)) {
            throw new AppExceptions.DuplicateEmailException(email);
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new AppExceptions.ValidationException(
                    "Passwords do not match."
            );
        }

        String encodedPassword =
                passwordEncoder.encode(request.getPassword());

        ClientUser user = new ClientUser(
                request.getFullName().trim(),
                email,
                encodedPassword
        );

        user.setEmailVerified(false);
        clientUserRepository.save(user);
        emailVerificationService.sendVerificationCode(user);
    }

    public AuthenticatedAccount authenticate(LoginRequest request) {

        String email = normalizeEmail(request.getEmail());

        Optional<ClientUser> client =
                clientUserRepository.findByEmail(email);

        Optional<AdminUser> admin =
                adminUserRepository.findByEmail(email);

        if (client.isPresent() && admin.isPresent()) {
            throw new AppExceptions.ConflictException(
                    "This email is associated with multiple account types. "
                    + "Please contact the administrator."
            );
        }

        if (admin.isPresent()) {

            AdminUser authenticatedAdmin =
                    adminAuthService.authenticate(
                            email,
                            request.getPassword()
                    );

            if (authenticatedAdmin == null) {
                return null;
            }

            return new AuthenticatedAccount(
                    authenticatedAdmin.getEmail(),
                    "ADMIN"
            );
        }

        if (client.isPresent()) {

            ClientUser clientUser = client.get();

            if (!clientUser.isEmailVerified()) {
                throw new AppExceptions.EmailNotVerifiedException();
            }

            if (!passwordEncoder.matches(
                    request.getPassword(),
                    clientUser.getPassword())) {

                return null;
            }

            return new AuthenticatedAccount(
                    clientUser.getEmail(),
                    "CLIENT"
            );
        }

        return null;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthenticatedAccount(String email, String role) {
    }
}