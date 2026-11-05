
package ca.sheridancollege.shtirthb.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.ContactMessageResponse;
import ca.sheridancollege.shtirthb.service.ContactMessageService;

@RestController
@RequestMapping("/api/admin/contact-messages")
public class AdminContactMessageController {

    private final ContactMessageService contactMessageService;

    public AdminContactMessageController(
            ContactMessageService contactMessageService) {
        this.contactMessageService = contactMessageService;
    }

    // Admin: retrieve all contact messages.
    @GetMapping
    public ResponseEntity<List<ContactMessageResponse>> getAllMessages() {

        return ResponseEntity.ok(
                contactMessageService.getAllMessages()
        );
    }

    // Admin: open a message and mark it as VIEWED.
    @GetMapping("/{id}")
    public ResponseEntity<ContactMessageResponse> getMessageById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                contactMessageService.getMessageById(id)
        );
    }
}
