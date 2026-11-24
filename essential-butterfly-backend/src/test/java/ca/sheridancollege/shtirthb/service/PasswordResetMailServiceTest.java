package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import ca.sheridancollege.shtirthb.config.AppProperties;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/*
 * The password reset email. The mail sender is replaced by a mock, so no real
 * email is sent. The service answers yes or no and never throws, because the
 * reset request must look the same to the visitor whether or not the mail
 * could be sent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetMailServiceTest {

    @Mock JavaMailSender mailSender;

    private static final String LINK = "http://localhost:5173/reset-password?token=abc-123";

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private PasswordResetMailService service(String from) {
        AppProperties properties = new AppProperties();
        properties.getMail().setFromAddress(from);
        return new PasswordResetMailService(mailSender, properties);
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

    // -------------------------------------------------------------- sending

    /*
     * Checks: The reset email is sent and the service answers true.
     * How:    Mockito fake mail sender. sendResetLink returns true and send() is called once.
     */
    @Test void sendsTheEmailAndSaysYes() {
        boolean sent = service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertTrue(sent);
        verify(mailSender).send(any(MimeMessage.class));
    }

    /*
     * Checks: The email goes to the right person from the clinic address.
     * How:    The captured message has one recipient (jane@mail.com) and the configured sender
     *   address.
     */
    @Test void theEmailGoesToTheRightPersonFromTheClinicAddress() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        MimeMessage message = sentMessage();
        assertEquals(1, message.getRecipients(Message.RecipientType.TO).length);
        assertEquals("jane@mail.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("noreply@example.org", message.getFrom()[0].toString());
    }

    /*
     * Checks: The subject names the password reset.
     * How:    The subject must be "Reset your Essential Butterfly password".
     */
    @Test void theSubjectNamesThePasswordReset() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertEquals("Reset your Essential Butterfly password", sentMessage().getSubject());
    }

    /*
     * Checks: The email contains the reset link.
     * How:    The text contains "Reset it here: " followed by the link.
     */
    @Test void theEmailContainsTheResetLink() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertTrue(textOf(sentMessage()).contains("Reset it here: " + LINK));
    }

    /*
     * Checks: The HTML email has a button that opens the link.
     * How:    The HTML contains an <a href> with the link and the text "Reset password".
     */
    @Test void theEmailHasAResetButtonThatOpensTheLink() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        String text = textOf(sentMessage());

        assertTrue(text.contains("<a href=\"" + LINK + "\""));
        assertTrue(text.contains("Reset password</a>"));
    }

    /*
     * Checks: The email says how long the link works.
     * How:    We pass 45 minutes. The text says "expires in 45 minutes", shows 45 minutes in bold, and
     *   says the link can be used once.
     */
    @Test void theEmailSaysHowLongTheLinkWorks() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 45);

        String text = textOf(sentMessage());

        assertTrue(text.contains("expires in 45 minutes"));
        assertTrue(text.contains("<strong>45 minutes</strong>"));
        assertTrue(text.contains("can be used once"));
    }

    /*
     * Checks: The email tells people who did not ask for it to ignore it.
     * How:    The text contains the "you can ignore this email" sentence.
     */
    @Test void theEmailTellsSomeoneWhoDidNotAskToIgnoreIt() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertTrue(textOf(sentMessage()).contains("you can ignore this email and your password will stay the same"));
    }

    /*
     * Checks: Special characters in the link are escaped in the HTML.
     * How:    A link with &, quotes, < and > must show as &amp; &quot; &lt; &gt;, and must not appear
     *   raw inside href.
     */
    @Test void specialCharactersInTheLinkAreEscapedInTheHtmlPart() throws Exception {
        service("noreply@example.org")
                .sendResetLink("jane@mail.com", "https://x.org/r?a=1&b=\"2\"<3>", 30);

        String text = textOf(sentMessage());

        assertTrue(text.contains("https://x.org/r?a=1&amp;b=&quot;2&quot;&lt;3&gt;"));
        assertFalse(text.contains("href=\"https://x.org/r?a=1&b="));
    }

    // ------------------------------------------------------ nothing is sent

    /*
     * Checks: Nothing is sent when the sender is empty.
     * How:    An empty from-address returns false and send() is never called.
     */
    @Test void sendsNothingWhenEmailIsNotConfigured() {
        boolean sent = service("").sendResetLink("jane@mail.com", LINK, 30);

        assertFalse(sent);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: Nothing is sent when the sender is null or blank.
     * How:    null and spaces both return false and send() is never called.
     */
    @Test void sendsNothingWhenTheFromAddressIsMissingOrBlank() {
        assertFalse(service(null).sendResetLink("jane@mail.com", LINK, 30));
        assertFalse(service("   ").sendResetLink("jane@mail.com", LINK, 30));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    /*
     * Checks: Nothing is sent without a recipient.
     * How:    A null and a blank recipient both return false and send() is never called.
     */
    @Test void sendsNothingWithoutARecipient() {
        assertFalse(service("noreply@example.org").sendResetLink(null, LINK, 30));
        assertFalse(service("noreply@example.org").sendResetLink("  ", LINK, 30));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ---------------------------------------------------------- failures

    /*
     * Checks: A mail server failure returns false and does not throw.
     * How:    The fake sender throws MailSendException.
     */
    @Test void saysNoAndDoesNotThrowWhenTheMailServerFails() {
        doThrow(new MailSendException("mail server is down")).when(mailSender).send(any(MimeMessage.class));

        boolean sent = service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertFalse(sent);
    }

    /*
     * Checks: If the message cannot be built, false is returned.
     * How:    createMimeMessage throws, the result is false and send() is never called.
     */
    @Test void saysNoAndDoesNotThrowWhenTheMessageCannotBeBuilt() {
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("no session"));

        boolean sent = service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertFalse(sent);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}