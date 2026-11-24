package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;

import ca.sheridancollege.shtirthb.config.AppProperties;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Part;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentMailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock AppointmentClock clock;

    private static final LocalDateTime START = LocalDateTime.parse("2026-11-16T10:00:00");
    private static final LocalDateTime END = LocalDateTime.parse("2026-11-16T11:30:00");

    @BeforeEach
    void setUp() {
        when(clock.zone()).thenReturn(ZoneId.of("America/Toronto"));
        when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private AppointmentMailService service(String from) {
        AppProperties properties = new AppProperties();
        properties.getMail().setFromAddress(from);
        return new AppointmentMailService(mailSender, properties, clock);
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    // Collects the text of every part, so a test can look for words in the mail.
    private static String textOf(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            StringBuilder all = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                all.append(textOf(multipart.getBodyPart(i))).append('\n');
            }
            return all.toString();
        }
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof java.io.InputStream stream) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        part.getDataHandler().writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /*
     * Checks: The invitation email goes to the client only.
     * How:    Mockito fake mail sender. sendInvitation returns true, and the captured message has one
     *   recipient (the client), the configured sender, and the subject "Your Essential Butterfly
     *   appointment".
     */
    @Test void sendsTheInvitationToTheClient() throws Exception {
        boolean sent = service("noreply@example.org")
                .sendInvitation("Ann", "ann@example.com", START, END, "https://meet.example.org/abc");

        assertTrue(sent);
        MimeMessage message = sentMessage();
        Address[] to = message.getRecipients(Message.RecipientType.TO);
        assertEquals(1, to.length);
        assertEquals("ann@example.com", to[0].toString());
        assertEquals("noreply@example.org", message.getFrom()[0].toString());
        assertEquals("Your Essential Butterfly appointment", message.getSubject());
    }

    /*
     * Checks: The email says when and where to join.
     * How:    The text contains the greeting, the date, the start and end time, the time zone and the
     *   meeting link.
     */
    @Test void theMailSaysWhenAndWhereToJoin() throws Exception {
        service("noreply@example.org")
                .sendInvitation("Ann", "ann@example.com", START, END, "https://meet.example.org/abc");

        String text = textOf(sentMessage());

        assertTrue(text.contains("Hello Ann"));
        assertTrue(text.contains("Monday, November 16, 2026"));
        assertTrue(text.contains("10:00"));
        assertTrue(text.contains("11:30"));
        assertTrue(text.contains("(America/Toronto)"));
        assertTrue(text.contains("https://meet.example.org/abc"));
        assertTrue(text.contains("Your appointment with Essential Butterfly is confirmed."));
    }

    /*
     * Checks: The email HTML cannot be used to inject code.
     * How:    We use a name with a script tag and a link with quotes. The HTML must show them escaped
     *   (&lt;script&gt;, &amp;, &quot;), not as real markup.
     */
    @Test void theHtmlPartCannotBeUsedToInjectMarkup() throws Exception {
        service("noreply@example.org")
                .sendInvitation("<script>alert(1)</script>", "ann@example.com", START, END,
                        "https://meet.example.org/a?x=1&y=\"2\"");

        String text = textOf(sentMessage());

        assertFalse(text.contains("<h2 style=\"color:#51247b\"><script>"));
        assertTrue(text.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(text.contains("x=1&amp;y=&quot;2&quot;"));
    }

    /*
     * Checks: Nothing is sent if the sender address is missing.
     * How:    A null and a blank sender address both return false, and send() is never called.
     */
    @Test void doesNotSendWhenTheSenderAddressIsNotConfigured() {
        assertFalse(service(null).sendInvitation("Ann", "ann@example.com", START, END, "https://x.org"));
        assertFalse(service("  ").sendInvitation("Ann", "ann@example.com", START, END, "https://x.org"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: Nothing is sent if the client has no email.
     * How:    A null and a blank email both return false, and send() is never called.
     */
    @Test void doesNotSendWhenTheClientHasNoEmailAddress() {
        assertFalse(service("noreply@example.org").sendInvitation("Ann", null, START, END, "https://x.org"));
        assertFalse(service("noreply@example.org").sendInvitation("Ann", " ", START, END, "https://x.org"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: A mail server failure returns false instead of crashing.
     * How:    The fake sender throws RuntimeException. sendInvitation returns false.
     */
    @Test void reportsFailureInsteadOfThrowingWhenTheMailServerIsDown() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        boolean sent = service("noreply@example.org")
                .sendInvitation("Ann", "ann@example.com", START, END, "https://x.org");

        assertFalse(sent);
    }

    /*
     * Checks: If the message cannot be built, false is returned.
     * How:    createMimeMessage throws. sendInvitation returns false.
     */
    @Test void reportsFailureWhenTheMessageCannotBeBuilt() {
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("no session"));

        assertFalse(service("noreply@example.org")
                .sendInvitation("Ann", "ann@example.com", START, END, "https://x.org"));
    }

    /*
     * Checks: A missing name or link does not break the email.
     * How:    We pass null for the name and the link. sendInvitation still returns true.
     */
    @Test void aMissingNameOrLinkDoesNotBreakTheHtml() {
        boolean sent = service("noreply@example.org")
                .sendInvitation(null, "ann@example.com", START, END, null);

        assertTrue(sent);
    }
}