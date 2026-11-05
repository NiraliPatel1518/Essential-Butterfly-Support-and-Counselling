package ca.sheridancollege.shtirthb.controller;

import ca.sheridancollege.shtirthb.dto.*;
import ca.sheridancollege.shtirthb.service.CustomerTestimonialService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/testimonials")
public class CustomerTestimonialController {
    private final CustomerTestimonialService service;
    public CustomerTestimonialController(CustomerTestimonialService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestimonialResponse submit(@RequestBody TestimonialRequest request, Authentication auth) {
        return service.submit(auth.getName(), request);
    }

    @GetMapping("/mine")
    public List<TestimonialResponse> mine(Authentication auth) { return service.mine(auth.getName()); }

    @GetMapping("/public")
    public List<TestimonialResponse> publicList() { return service.publicList(); }

    @GetMapping("/featured")
    public List<TestimonialResponse> featured() { return service.featured(); }
}
