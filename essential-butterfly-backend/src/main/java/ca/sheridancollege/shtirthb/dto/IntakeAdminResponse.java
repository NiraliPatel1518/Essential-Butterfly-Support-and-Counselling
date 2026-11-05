package ca.sheridancollege.shtirthb.dto;

import java.time.LocalDateTime;

public class IntakeAdminResponse {

    private Long id;
    private String clientFullName;
    private String parentGuardianName;
    private String contactInfo;
    private String preferredContactMethod;
    private String overview;
    private String supportNeeds;
    private String respiteGoals;
    private String subjectFocus;
    private String additionalNotes;
    private String status;
    private String rejectionReason;
    private LocalDateTime createdAt;

    public IntakeAdminResponse(
            Long id,
            String clientFullName,
            String parentGuardianName,
            String contactInfo,
            String preferredContactMethod,
            String overview,
            String supportNeeds,
            String respiteGoals,
            String subjectFocus,
            String additionalNotes,
            String status,
            String rejectionReason,
            LocalDateTime createdAt) {

        this.id = id;
        this.clientFullName = clientFullName;
        this.parentGuardianName = parentGuardianName;
        this.contactInfo = contactInfo;
        this.preferredContactMethod = preferredContactMethod;
        this.overview = overview;
        this.supportNeeds = supportNeeds;
        this.respiteGoals = respiteGoals;
        this.subjectFocus = subjectFocus;
        this.additionalNotes = additionalNotes;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getClientFullName() {
        return clientFullName;
    }

    public String getParentGuardianName() {
        return parentGuardianName;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public String getPreferredContactMethod() {
        return preferredContactMethod;
    }

    public String getOverview() {
        return overview;
    }

    public String getSupportNeeds() {
        return supportNeeds;
    }

    public String getRespiteGoals() {
        return respiteGoals;
    }

    public String getSubjectFocus() {
        return subjectFocus;
    }

    public String getAdditionalNotes() {
        return additionalNotes;
    }

    public String getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}