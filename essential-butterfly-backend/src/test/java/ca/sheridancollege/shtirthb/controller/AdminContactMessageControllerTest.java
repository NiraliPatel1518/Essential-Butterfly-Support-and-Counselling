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

import java.util.List;
import ca.sheridancollege.shtirthb.dto.ContactMessageResponse;
import ca.sheridancollege.shtirthb.service.ContactMessageService;

@ExtendWith(MockitoExtension.class)
class AdminContactMessageControllerTest {
    @Mock ContactMessageService service;
    @InjectMocks AdminContactMessageController controller;

    @Test void listsAllMessages() {
        when(service.getAllMessages()).thenReturn(List.of());
        var response = controller.getAllMessages();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
        verify(service).getAllMessages();
    }

    @Test void getsMessageById() {
        ContactMessageResponse expected = mock(ContactMessageResponse.class);
        when(service.getMessageById(12L)).thenReturn(expected);
        var response = controller.getMessageById(12L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(service).getMessageById(12L);
    }
}
