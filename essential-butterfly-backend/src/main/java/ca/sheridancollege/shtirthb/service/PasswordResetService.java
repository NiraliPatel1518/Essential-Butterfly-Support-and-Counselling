package ca.sheridancollege.shtirthb.service;


import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import ca.sheridancollege.shtirthb.repository.ClientUserRepository;
import ca.sheridancollege.shtirthb.repository.PasswordResetTokenRepository;

@Service
public class PasswordResetService {

private final ClientUserRepository clientUserRepository;
private final AdminUserRepository adminUserRepository;
private final PasswordResetTokenRepository passwordResetTokenRepository;
private final PasswordEncoder passwordEncoder;
private final String frontendBaseUrl;

public PasswordResetService(
ClientUserRepository clientUserRepository,
AdminUserRepository adminUserRepository,
PasswordResetTokenRepository passwordResetTokenRepository,
PasswordEncoder passwordEncoder,
@Value("${app.frontend-base-url:http://localhost:5173}")
String frontendBaseUrl) {

this.clientUserRepository = clientUserRepository;
this.adminUserRepository = adminUserRepository;
this.passwordResetTokenRepository = passwordResetTokenRepository;
this.passwordEncoder = passwordEncoder;
this.frontendBaseUrl = frontendBaseUrl;
}

@Transactional
public void requestPasswordReset(String email) {

String normalizedEmail = email.trim().toLowerCase();

ClientUser client = clientUserRepository
.findByEmail(normalizedEmail)
.orElse(null);

AdminUser admin = adminUserRepository
.findByEmail(normalizedEmail)
.orElse(null);

if (client != null && admin != null) {
return;
}

if (client != null) {
createResetToken("CLIENT", client.getId());

} else if (admin != null) {
createResetToken("ADMIN", admin.getId());
}
}

private void createResetToken(
String userType,
Long userId) {

String token = UUID.randomUUID().toString();

LocalDateTime expiresAt =
LocalDateTime.now().plusMinutes(30);

PasswordResetToken resetToken = new PasswordResetToken(
token,
userType,
userId,
expiresAt
);

passwordResetTokenRepository.save(resetToken);

String resetLink = frontendBaseUrl
+ "/reset-password?token="
+ token;

// Development only: replace console output with email delivery
// before deploying the application.
System.out.println();
System.out.println("======================================");
System.out.println("PASSWORD RESET REQUEST");
System.out.println("User type: " + userType);
System.out.println("Reset token: " + token);
System.out.println("Reset link: " + resetLink);
System.out.println("Expires in: 30 minutes");
System.out.println("======================================");
System.out.println();
}

@Transactional
public boolean resetPassword(
String token,
String newPassword) {

if (token == null
|| token.isBlank()
|| newPassword == null
|| newPassword.length() < 8) {

return false;
}

PasswordResetToken resetToken = passwordResetTokenRepository
.findByToken(token)
.orElse(null);

if (resetToken == null || resetToken.isUsed()) {
return false;
}

if (!resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
return false;
}

String encodedPassword =
passwordEncoder.encode(newPassword);

if ("CLIENT".equals(resetToken.getUserType())) {

ClientUser user = clientUserRepository
.findById(resetToken.getUserId())
.orElse(null);

if (user == null) {
return false;
}

user.setPassword(encodedPassword);
clientUserRepository.save(user);

} else if ("ADMIN".equals(resetToken.getUserType())) {

AdminUser admin = adminUserRepository
.findById(resetToken.getUserId())
.orElse(null);

if (admin == null) {
return false;
}

admin.setPassword(encodedPassword);
admin.setFailedLoginAttempts(0);
admin.setLockedUntil(null);

adminUserRepository.save(admin);

} else {
return false;
}

resetToken.setUsed(true);
passwordResetTokenRepository.save(resetToken);

return true;
}
}