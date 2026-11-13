package ca.sheridancollege.shtirthb.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.config.AppProperties;

/*
 * Sends the meeting invitation to the client of an appointment. The result is
 * a yes/no: a mail problem must never undo a confirmed appointment, so the
 * caller tells the administrator when it did not go out and the link can be
 * sent again later.
 */
@Service
public class AppointmentMailService {

    private static final Logger log =
            LoggerFactory.getLogger(AppointmentMailService.class);

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final AppointmentClock clock;

    public AppointmentMailService(
            JavaMailSender mailSender,
            AppProperties properties,
            AppointmentClock clock) {

        this.mailSender = mailSender;
        this.fromAddress = properties.getMail().getFromAddress();
        this.clock = clock;
    }

    public boolean sendInvitation(
            String clientName,
            String clientEmail,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String meetingLink) {

        if (fromAddress == null || fromAddress.isBlank()
                || clientEmail == null || clientEmail.isBlank()) {

            log.warn("Meeting invitation not sent: e-mail is not configured "
                    + "or the client has no address.");

            return false;
        }

        String when = DAY.format(startsAt) + ", "
                + TIME.format(startsAt) + " to " + TIME.format(endsAt)
                + " (" + clock.zone().getId() + ")";

        String plain = "Hello " + clientName + ",\n\n"
                + "Your appointment with Essential Butterfly is confirmed.\n\n"
                + "When: " + when + "\n"
                + "Join here: " + meetingLink + "\n\n"
                + "If you cannot attend, please contact us.";

        String html = """
                <!doctype html>
                <html>
                <body style="font-family:Arial,sans-serif;color:#352047;line-height:1.6">
                  <div style="max-width:560px;margin:auto;padding:24px">
                    <h2 style="color:#51247b">Your appointment is confirmed</h2>
                    <p>Hello %s,</p>
                    <p>Your appointment with Essential Butterfly is confirmed.</p>
                    <p><strong>When:</strong> %s</p>
                    <p><strong>Join here:</strong>
                       <a href="%s">%s</a></p>
                    <p>If you cannot attend, please contact us.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(clientName),
                escapeHtml(when),
                escapeHtml(meetingLink),
                escapeHtml(meetingLink));

        try {

            var message = mailSender.createMimeMessage();

            var helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(clientEmail);
            helper.setSubject("Your Essential Butterfly appointment");
            helper.setText(plain, html);

            mailSender.send(message);

            return true;

        } catch (Exception ex) {

            log.error("Meeting invitation e-mail could not be sent.", ex);

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
