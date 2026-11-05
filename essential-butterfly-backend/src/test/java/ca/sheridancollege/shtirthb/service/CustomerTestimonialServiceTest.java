package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ca.sheridancollege.shtirthb.dto.TestimonialRequest;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.CustomerTestimonial;
import ca.sheridancollege.shtirthb.repository.*;

@ExtendWith(MockitoExtension.class)
class CustomerTestimonialServiceTest {
    @Mock CustomerTestimonialRepository testimonials;
    @Mock ClientUserRepository clients;
    @Mock FeatureVisibilityRepository features;
    CustomerTestimonialService service;
    @BeforeEach void setUp() { service = new CustomerTestimonialService(testimonials, clients, features); }

    @Test void submitCreatesPendingTestimonialForAuthenticatedClient() {
        ClientUser client = new ClientUser("Jane", "jane@mail.com", "HASH");
        when(clients.findByEmail("jane@mail.com")).thenReturn(Optional.of(client));
        when(testimonials.save(any(CustomerTestimonial.class))).thenAnswer(i -> i.getArgument(0));
        var response = service.submit("jane@mail.com", request(" Jane ", "This is a helpful testimonial.", true));
        assertNotNull(response);
        verify(testimonials).save(argThat(t -> t.getDisplayName().equals("Jane") && t.getContent().equals("This is a helpful testimonial.") && "PENDING".equals(t.getStatus())));
    }

    @Test void submitRequiresPublicationConsent() {
        assertThrows(IllegalArgumentException.class, () -> service.submit("jane@mail.com", request("Jane", "A sufficiently long testimonial.", false)));
        verifyNoInteractions(clients, testimonials);
    }

    @Test void submitRejectsShortContentAndMoreThanThreePhotos() {
        assertThrows(IllegalArgumentException.class, () -> service.submit("jane@mail.com", request("Jane", "Too short", true)));
        TestimonialRequest r = request("Jane", "This testimonial is long enough.", true); r.setPhotos(List.of("a", "b", "c", "d"));
        assertThrows(IllegalArgumentException.class, () -> service.submit("jane@mail.com", r));
        verifyNoInteractions(clients, testimonials);
    }

    @Test void submitRejectsMissingClientAccount() {
        when(clients.findByEmail("missing@mail.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.submit("missing@mail.com", request("Jane", "This testimonial is long enough.", true)));
        verify(testimonials, never()).save(any());
    }

    @Test void publicTestimonialsAreHiddenWhenFeatureDisabled() {
        when(features.findByFeatureKey("TESTIMONIALS")).thenReturn(Optional.empty());
        assertTrue(service.publicList().isEmpty());
        assertTrue(service.featured().isEmpty());
        verifyNoInteractions(testimonials);
    }

    private TestimonialRequest request(String name, String content, boolean consent) {
        TestimonialRequest r = new TestimonialRequest(); r.setDisplayName(name); r.setContent(content); r.setPublicationConsent(consent); return r;
    }
}
