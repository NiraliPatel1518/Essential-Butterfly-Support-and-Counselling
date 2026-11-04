
package ca.sheridancollege.shtirthb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.NewsletterSubscriber;

public interface NewsletterSubscriberRepository
        extends JpaRepository<NewsletterSubscriber, Long> {

    Optional<NewsletterSubscriber> findByEmailIgnoreCase(String email);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String token);

    List<NewsletterSubscriber> findAllByActiveTrueOrderByCreatedAtDesc();

    long countByActiveTrue();
}
