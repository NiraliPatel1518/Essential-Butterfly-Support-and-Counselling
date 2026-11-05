package ca.sheridancollege.shtirthb.dto;

public class FeatureVisibilityResponse {

    private final String featureKey;
    private final String label;
    private final boolean enabled;
    private final int displayOrder;

    public FeatureVisibilityResponse(
            String featureKey,
            String label,
            boolean enabled,
            int displayOrder) {

        this.featureKey = featureKey;
        this.label = label;
        this.enabled = enabled;
        this.displayOrder = displayOrder;
    }

    public String getFeatureKey() { return featureKey; }
    public String getLabel() { return label; }
    public boolean isEnabled() { return enabled; }
    public int getDisplayOrder() { return displayOrder; }
}