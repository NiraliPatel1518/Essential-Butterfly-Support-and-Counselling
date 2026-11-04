package ca.sheridancollege.shtirthb.controller;

import ca.sheridancollege.shtirthb.dto.*;
import ca.sheridancollege.shtirthb.service.CustomerTestimonialService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/testimonials")
public class AdminTestimonialController {
    private final CustomerTestimonialService service;
    public AdminTestimonialController(CustomerTestimonialService service) { this.service = service; }

    @GetMapping
    public List<TestimonialResponse> all() { return service.adminList(); }

    @PostMapping("/{id}/approve")
    public TestimonialResponse approve(@PathVariable Long id) { return service.review(id, "APPROVED"); }

    @PostMapping("/{id}/reject")
    public TestimonialResponse reject(@PathVariable Long id) { return service.review(id, "REJECTED"); }

    @PutMapping("/{id}/display")
    public TestimonialResponse settings(@PathVariable Long id, @RequestBody TestimonialDecisionRequest request) {
        return service.settings(id, request);
    }

    @PostMapping("/{id}/remove")
    public TestimonialResponse remove(@PathVariable Long id) { return service.remove(id); }
}
