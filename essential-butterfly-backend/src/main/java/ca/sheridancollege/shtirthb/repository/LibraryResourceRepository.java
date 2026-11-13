package ca.sheridancollege.shtirthb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.LibraryResource;

public interface LibraryResourceRepository
        extends JpaRepository<LibraryResource, Long> {

    // Administrator list: everything that has not been removed.
    List<LibraryResource> findAllByStatusNotOrderByUpdatedAtDesc(String status);

    // Public list: published resources only.
    List<LibraryResource> findAllByStatusOrderByTitleAsc(String status);

    Optional<LibraryResource> findByIdAndStatus(Long id, String status);

    Optional<LibraryResource> findByIdAndStatusNot(Long id, String status);

    // Duplicate checks (removed resources do not count).
    List<LibraryResource> findAllByTitleIgnoreCaseAndStatusNot(
            String title, String status);

    List<LibraryResource> findAllByLinkUrlAndStatusNot(
            String linkUrl, String status);
}
