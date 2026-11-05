package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsletterMailServiceTest {
    @Mock JavaMailSender mailSender;

    @Test void failsClearlyWhenSenderAddressIsNotConfigured() {
        NewsletterMailService service = new NewsletterMailService(mailSender, " ", "http://localhost:5173");
        assertThrows(IllegalStateException.class, () -> service.send(null, null));
        verifyNoInteractions(mailSender);
    }
}
