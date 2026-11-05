package ca.sheridancollege.shtirthb.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import ca.sheridancollege.shtirthb.dto.ContactMessageRequest;
import ca.sheridancollege.shtirthb.service.ContactMessageService;

@ExtendWith(MockitoExtension.class)
class ContactMessageControllerTest {
    @Mock ContactMessageService service;
    @InjectMocks ContactMessageController controller;

    @Test void submitsContactMessage() {
        ContactMessageRequest request = mock(ContactMessageRequest.class);
        var response = controller.submitMessage(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).submitMessage(request);
    }
}
