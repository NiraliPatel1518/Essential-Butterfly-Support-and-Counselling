package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;

@Entity
@Table(name = "public_feature_visibility")
public class FeatureVisibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_key", nullable = false, unique = true, length = 40)
    private String featureKey;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected FeatureVisibility() {}

    public FeatureVisibility(String key, String label, boolean enabled, int order) {
        this.featureKey = key;
        this.label = label;
        this.enabled = enabled;
        this.displayOrder = order;
    }

    public Long getId() { return id; }
    public String getFeatureKey() { return featureKey; }
    public String getLabel() { return label; }
    public boolean isEnabled() { return enabled; }
    public int getDisplayOrder() { return displayOrder; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}