package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "intake_submissions")
public class IntakeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The customer account that submitted this intake
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_user_id", nullable = false)
    private ClientUser clientUser;

    @Column(nullable = false)
    private String clientFullName;

    private String parentGuardianName;

    @Column(nullable = false)
    private String contactInfo;

    @Column(nullable = false)
    private String preferredContactMethod;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String overview;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String supportNeeds;

    @Column(columnDefinition = "TEXT")
    private String respiteGoals;

    @Column(columnDefinition = "TEXT")
    private String subjectFocus;

    @Column(columnDefinition = "TEXT")
    private String additionalNotes;

    @Column(nullable = false)
    private String status = "NEW";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public IntakeSubmission() {
    }

    public IntakeSubmission(
            ClientUser clientUser,
            String clientFullName,
            String parentGuardianName,
            String contactInfo,
            String preferredContactMethod,
            String overview,
            String supportNeeds,
            String respiteGoals,
            String subjectFocus,
            String additionalNotes) {

        this.clientUser = clientUser;
        this.clientFullName = clientFullName;
        this.parentGuardianName = parentGuardianName;
        this.contactInfo = contactInfo;
        this.preferredContactMethod = preferredContactMethod;
        this.overview = overview;
        this.supportNeeds = supportNeeds;
        this.respiteGoals = respiteGoals;
        this.subjectFocus = subjectFocus;
        this.additionalNotes = additionalNotes;
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ClientUser getClientUser() {
        return clientUser;
    }

    public void setClientUser(ClientUser clientUser) {
        this.clientUser = clientUser;
    }

    public String getClientFullName() {
        return clientFullName;
    }

    public void setClientFullName(String clientFullName) {
        this.clientFullName = clientFullName;
    }

    public String getParentGuardianName() {
        return parentGuardianName;
    }

    public void setParentGuardianName(String parentGuardianName) {
        this.parentGuardianName = parentGuardianName;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public String getPreferredContactMethod() {
        return preferredContactMethod;
    }

    public void setPreferredContactMethod(String preferredContactMethod) {
        this.preferredContactMethod = preferredContactMethod;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public String getSupportNeeds() {
        return supportNeeds;
    }

    public void setSupportNeeds(String supportNeeds) {
        this.supportNeeds = supportNeeds;
    }

    public String getRespiteGoals() {
        return respiteGoals;
    }

    public void setRespiteGoals(String respiteGoals) {
        this.respiteGoals = respiteGoals;
    }

    public String getSubjectFocus() {
        return subjectFocus;
    }

    public void setSubjectFocus(String subjectFocus) {
        this.subjectFocus = subjectFocus;
    }

    public String getAdditionalNotes() {
        return additionalNotes;
    }

    public void setAdditionalNotes(String additionalNotes) {
        this.additionalNotes = additionalNotes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}