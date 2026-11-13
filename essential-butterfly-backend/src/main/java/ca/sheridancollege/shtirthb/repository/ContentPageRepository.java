package ca.sheridancollege.shtirthb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.ContentPage;

public interface ContentPageRepository
        extends JpaRepository<ContentPage, Long> {

    Optional<ContentPage> findByPageKey(String pageKey);

    List<ContentPage> findAllByOrderByIdAsc();
}
