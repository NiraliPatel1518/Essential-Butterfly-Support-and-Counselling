package ca.sheridancollege.shtirthb.dto;

import jakarta.validation.constraints.NotBlank;

public class IntakeSubmissionRequest {

    @NotBlank(message = "Client full name is required")
    private String clientFullName;

    private String parentGuardianName;

    @NotBlank(message = "Contact information is required")
    private String contactInfo;

    @NotBlank(message = "Preferred contact method is required")
    private String preferredContactMethod;

    @NotBlank(message = "Overview is required")
    private String overview;

    @NotBlank(message = "Support needs are required")
    private String supportNeeds;

    private String respiteGoals;

    private String subjectFocus;

    private String additionalNotes;

    public IntakeSubmissionRequest() {
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
}