
package ca.sheridancollege.shtirthb.service;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import ca.sheridancollege.shtirthb.model.NewsletterCampaign;
import ca.sheridancollege.shtirthb.model.NewsletterSubscriber;

@Service
public class NewsletterMailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String publicBaseUrl;

    public NewsletterMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromAddress,
            @Value("${app.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void send(
            NewsletterCampaign campaign,
            NewsletterSubscriber subscriber) {

        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException(
                "Gmail is not configured. Set GMAIL_USERNAME."
            );
        }

        String unsubscribeUrl = publicBaseUrl
            + "/newsletter/unsubscribe?token="
            + subscriber.getUnsubscribeToken();

        String safeContent = escapeHtml(campaign.getContent())
            .replace("\n", "<br>");

        String html = """
            <!doctype html>
            <html>
            <body style="font-family:Arial,sans-serif;color:#352047;line-height:1.6">
              <div style="max-width:640px;margin:auto;padding:24px">
                <h1 style="color:#51247b">%s</h1>
                <div>%s</div>
                <hr style="margin-top:32px;border:0;border-top:1px solid #ddd">
                <p style="font-size:12px;color:#666">
                  You received this email because you subscribed to
                  Essential Butterfly newsletters.
                </p>
                <p style="font-size:12px">
                  <a href="%s">Unsubscribe from future newsletters</a>
                </p>
              </div>
            </body>
            </html>
            """.formatted(
                escapeHtml(campaign.getSubject()),
                safeContent,
                escapeHtml(unsubscribeUrl)
            );

        var message = mailSender.createMimeMessage();

        try {
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(
                message, true, StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);
            helper.setTo(subscriber.getEmail());
            helper.setSubject(campaign.getSubject());
            helper.setText(
                campaign.getContent() + "\n\nUnsubscribe: " + unsubscribeUrl,
                html
            );

            mailSender.send(message);
        } catch (Exception ex) {
            throw new IllegalStateException(
                ex.getMessage() == null
                    ? "Unable to send email."
                    : ex.getMessage(),
                ex
            );
        }
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
