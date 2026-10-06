package ca.sheridancollege.shtirthb.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ca.sheridancollege.shtirthb.dto.ContactMessageRequest;
import ca.sheridancollege.shtirthb.service.ContactMessageService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/contact")
public class ContactMessageController {

    private final ContactMessageService contactMessageService;

    public ContactMessageController(
            ContactMessageService contactMessageService
    ) {
        this.contactMessageService = contactMessageService;
    }

    @PostMapping
    public ResponseEntity<String> submitMessage(
            @Valid @RequestBody ContactMessageRequest request
    ) {
        try {

            contactMessageService.submitMessage(request);

            return ResponseEntity.ok(
                    "Your message has been submitted successfully"
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
}