package ca.sheridancollege.shtirthb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.WebsiteUpdate;

public interface WebsiteUpdateRepository
        extends JpaRepository<WebsiteUpdate, Long> {

    List<WebsiteUpdate> findAllByOrderByUpdatedAtDesc();

    List<WebsiteUpdate> findAllByPublishedTrueOrderByUpdatedAtDesc();

    Optional<WebsiteUpdate> findByIdAndPublishedTrue(Long id);
}