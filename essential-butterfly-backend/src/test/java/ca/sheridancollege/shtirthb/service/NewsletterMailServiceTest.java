package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.sheridancollege.shtirthb.config.AppProperties;

@ExtendWith(MockitoExtension.class)
class NewsletterMailServiceTest {
    @Mock JavaMailSender mailSender;

    private static AppProperties propertiesWith(String fromAddress, String publicBaseUrl) {
        AppProperties properties = new AppProperties();
        properties.getMail().setFromAddress(fromAddress);
        properties.setPublicBaseUrl(publicBaseUrl);
        return properties;
    }

    @Test void failsClearlyWhenSenderAddressIsNotConfigured() {
        NewsletterMailService service = new NewsletterMailService(mailSender, propertiesWith(" ", "http://localhost:5173"));
        assertThrows(IllegalStateException.class, () -> service.send(null, null));
        verifyNoInteractions(mailSender);
    }
}
