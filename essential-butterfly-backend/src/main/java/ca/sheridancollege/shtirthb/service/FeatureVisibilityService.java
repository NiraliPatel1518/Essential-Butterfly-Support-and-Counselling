
package ca.sheridancollege.shtirthb.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.FeatureVisibilityRequest;
import ca.sheridancollege.shtirthb.dto.FeatureVisibilityResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;
import ca.sheridancollege.shtirthb.model.FeatureVisibility;
import ca.sheridancollege.shtirthb.repository.FeatureVisibilityRepository;

@Service
public class FeatureVisibilityService {

    private static final Map<String, String> FEATURES = Map.of(
            "NEWSLETTER", "Newsletter Signup",
            "TESTIMONIALS", "Testimonials",
            "UPDATES", "Updates",
            "APPOINTMENTS", "Appointments",
            "MEMORY_WALL", "Memory Wall"
    );

    private final FeatureVisibilityRepository repository;

    public FeatureVisibilityService(
            FeatureVisibilityRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public List<FeatureVisibilityResponse> getFeatures() {

        seedDefaults();

        return repository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .filter(feature ->
                        FEATURES.containsKey(feature.getFeatureKey()))
                .sorted((a, b) ->
                        Integer.compare(
                                orderOf(a.getFeatureKey()),
                                orderOf(b.getFeatureKey())
                        )
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<FeatureVisibilityResponse> updateFeatures(
            List<FeatureVisibilityRequest> requests) {

        if (requests == null || requests.size() != FEATURES.size()) {
            throw new AppExceptions.ValidationException(
                    "Submit exactly the five website features."
            );
        }

        Set<String> submittedKeys = requests.stream()
                .map(request -> request.getFeatureKey() == null
                        ? ""
                        : request.getFeatureKey().trim().toUpperCase())
                .collect(Collectors.toSet());

        if (submittedKeys.size() != FEATURES.size()
                || !submittedKeys.equals(FEATURES.keySet())) {

            throw new AppExceptions.ValidationException(
                    "Only Newsletter Signup, Testimonials, Updates, Appointments and Memory Wall can be changed."
            );
        }

        if (requests.stream().anyMatch(
                request -> request.getEnabled() == null)) {

            throw new AppExceptions.ValidationException(
                    "Every feature must have a visibility value."
            );
        }

        seedDefaults();

        for (FeatureVisibilityRequest request : requests) {

            String key = request.getFeatureKey()
                    .trim()
                    .toUpperCase();

            FeatureVisibility feature = repository
                    .findByFeatureKey(key)
                    .orElseThrow(() ->
                            new AppExceptions.ResourceNotFoundException(
                                    "Feature setting"
                            )
                    );

            feature.setEnabled(request.getEnabled());
        }

        repository.flush();

        return getFeatures();
    }

    private void seedDefaults() {

        String[] keys = {
                "NEWSLETTER",
                "TESTIMONIALS",
                "UPDATES",
                "APPOINTMENTS",
                "MEMORY_WALL"
        };

        for (String key : keys) {

            if (repository.findByFeatureKey(key).isEmpty()) {

                repository.save(
                        new FeatureVisibility(
                                key,
                                FEATURES.get(key),
                                true,
                                orderOf(key)
                        )
                );
            }
        }
    }

    private int orderOf(String key) {

        return switch (key) {
            case "NEWSLETTER" -> 1;
            case "TESTIMONIALS" -> 2;
            case "UPDATES" -> 3;
            case "APPOINTMENTS" -> 4;
            case "MEMORY_WALL" -> 5;
            default -> 99;
        };
    }

    private FeatureVisibilityResponse toResponse(
            FeatureVisibility feature) {

        return new FeatureVisibilityResponse(
                feature.getFeatureKey(),
                FEATURES.get(feature.getFeatureKey()),
                feature.isEnabled(),
                orderOf(feature.getFeatureKey())
        );
    }
}
