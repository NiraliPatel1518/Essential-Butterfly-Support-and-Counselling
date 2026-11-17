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

    @Test void sendsTheEmailAndSaysYes() {
        boolean sent = service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertTrue(sent);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test void theEmailGoesToTheRightPersonFromTheClinicAddress() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        MimeMessage message = sentMessage();
        assertEquals(1, message.getRecipients(Message.RecipientType.TO).length);
        assertEquals("jane@mail.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("noreply@example.org", message.getFrom()[0].toString());
    }

    @Test void theSubjectNamesThePasswordReset() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertEquals("Reset your Essential Butterfly password", sentMessage().getSubject());
    }

    @Test void theEmailContainsTheResetLink() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertTrue(textOf(sentMessage()).contains("Reset it here: " + LINK));
    }

    @Test void theEmailHasAResetButtonThatOpensTheLink() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        String text = textOf(sentMessage());

        assertTrue(text.contains("<a href=\"" + LINK + "\""));
        assertTrue(text.contains("Reset password</a>"));
    }

    @Test void theEmailSaysHowLongTheLinkWorks() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 45);

        String text = textOf(sentMessage());

        assertTrue(text.contains("expires in 45 minutes"));
        assertTrue(text.contains("<strong>45 minutes</strong>"));
        assertTrue(text.contains("can be used once"));
    }

    @Test void theEmailTellsSomeoneWhoDidNotAskToIgnoreIt() throws Exception {
        service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertTrue(textOf(sentMessage()).contains("you can ignore this email and your password will stay the same"));
    }

    @Test void specialCharactersInTheLinkAreEscapedInTheHtmlPart() throws Exception {
        service("noreply@example.org")
                .sendResetLink("jane@mail.com", "https://x.org/r?a=1&b=\"2\"<3>", 30);

        String text = textOf(sentMessage());

        assertTrue(text.contains("https://x.org/r?a=1&amp;b=&quot;2&quot;&lt;3&gt;"));
        assertFalse(text.contains("href=\"https://x.org/r?a=1&b="));
    }

    // ------------------------------------------------------ nothing is sent

    @Test void sendsNothingWhenEmailIsNotConfigured() {
        boolean sent = service("").sendResetLink("jane@mail.com", LINK, 30);

        assertFalse(sent);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test void sendsNothingWhenTheFromAddressIsMissingOrBlank() {
        assertFalse(service(null).sendResetLink("jane@mail.com", LINK, 30));
        assertFalse(service("   ").sendResetLink("jane@mail.com", LINK, 30));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test void sendsNothingWithoutARecipient() {
        assertFalse(service("noreply@example.org").sendResetLink(null, LINK, 30));
        assertFalse(service("noreply@example.org").sendResetLink("  ", LINK, 30));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ---------------------------------------------------------- failures

    @Test void saysNoAndDoesNotThrowWhenTheMailServerFails() {
        doThrow(new MailSendException("mail server is down")).when(mailSender).send(any(MimeMessage.class));

        boolean sent = service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertFalse(sent);
    }

    @Test void saysNoAndDoesNotThrowWhenTheMessageCannotBeBuilt() {
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("no session"));

        boolean sent = service("noreply@example.org").sendResetLink("jane@mail.com", LINK, 30);

        assertFalse(sent);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}