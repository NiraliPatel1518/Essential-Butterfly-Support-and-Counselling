package ca.sheridancollege.shtirthb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.FeatureVisibility;

public interface FeatureVisibilityRepository
        extends JpaRepository<FeatureVisibility, Long> {

    List<FeatureVisibility> findAllByOrderByDisplayOrderAsc();

    Optional<FeatureVisibility> findByFeatureKey(String featureKey);
}