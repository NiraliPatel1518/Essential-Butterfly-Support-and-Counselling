package ca.sheridancollege.shtirthb.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.config.AppProperties;

/*
 * Sends the password reset link by e-mail. The result is a yes/no: the
 * reset request must look identical to the visitor whether or not an account
 * exists (or the mail failed), so a problem here is only logged, never
 * thrown. The link itself is never written to the log.
 */
@Service
public class PasswordResetMailService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public PasswordResetMailService(
            JavaMailSender mailSender,
            AppProperties properties) {

        this.mailSender = mailSender;
        this.fromAddress = properties.getMail().getFromAddress();
    }

    public boolean sendResetLink(
            String toEmail,
            String resetLink,
            int validMinutes) {

        if (fromAddress == null || fromAddress.isBlank()
                || toEmail == null || toEmail.isBlank()) {

            log.warn("Password reset e-mail not sent: e-mail delivery is "
                    + "not configured.");

            return false;
        }

        String plain = "We received a request to reset your Essential "
                + "Butterfly password.\n\n"
                + "Reset it here: " + resetLink + "\n\n"
                + "This link expires in " + validMinutes + " minutes and "
                + "can be used once. If you did not ask for this, you can "
                + "ignore this email and your password will stay the same.";

        String html = """
                <!doctype html>
                <html>
                <body style="font-family:Arial,sans-serif;color:#352047;line-height:1.6">
                  <div style="max-width:560px;margin:auto;padding:24px">
                    <h2 style="color:#51247b">Reset your password</h2>
                    <p>We received a request to reset your Essential Butterfly password.</p>
                    <p><a href="%s" style="display:inline-block;background:#51247b;color:#ffffff;padding:12px 22px;border-radius:6px;text-decoration:none;font-weight:700">Reset password</a></p>
                    <p>If the button does not work, copy this link into your browser:<br>
                       <a href="%s">%s</a></p>
                    <p>This link expires in <strong>%d minutes</strong> and can be used once.</p>
                    <p>If you did not ask for this, you can ignore this email and your password will stay the same.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(resetLink),
                escapeHtml(resetLink),
                escapeHtml(resetLink),
                validMinutes);

        try {

            var message = mailSender.createMimeMessage();

            var helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Reset your Essential Butterfly password");
            helper.setText(plain, html);

            mailSender.send(message);

            return true;

        } catch (Exception ex) {

            log.error("Password reset e-mail could not be sent.", ex);

            return false;
        }
    }

    private String escapeHtml(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
