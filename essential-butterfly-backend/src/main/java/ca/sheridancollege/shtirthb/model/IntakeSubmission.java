
package ca.sheridancollege.shtirthb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "intake_submissions")
public class IntakeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * The account that submitted this intake. It becomes null if the
     * account is later deleted (ON DELETE SET NULL): the submission, and
     * the submitter details copied below, are history and are kept.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id")
    private ClientUser clientUser;

    /*
     * The submitter as they were when the intake was made. Filled by a
     * database trigger on insert (read-only here), so later changes to the
     * account never rewrite the original submission.
     */
    @Column(name = "submitted_by_name", insertable = false, updatable = false)
    private String submittedByName;

    @Column(name = "submitted_by_email", insertable = false, updatable = false)
    private String submittedByEmail;

    @Column(name = "submitted_by_phone", insertable = false, updatable = false)
    private String submittedByPhone;

    /*
     * Everything the family wrote is immutable once submitted
     * (updatable = false here, and enforced by a database trigger).
     * Only the review status and rejection reason change afterwards.
     */

    // Who the support is for.
    @Column(nullable = false, updatable = false)
    private String clientFullName;

    @Column(updatable = false)
    private String parentGuardianName;

    @Column(nullable = false, updatable = false)
    private String contactInfo;

    @Column(nullable = false, updatable = false)
    private String preferredContactMethod;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String overview;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String supportNeeds;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String respiteGoals;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String subjectFocus;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String additionalNotes;

    @Column(nullable = false)
    private String status = "SUBMITTED";

    // Private administrative note; never include this in client responses.
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
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
        this.parentGuardianName = parentGuardianName == null || parentGuardianName.isBlank()
                ? null
                : parentGuardianName.trim();
        this.contactInfo = contactInfo;
        this.preferredContactMethod = preferredContactMethod;
        this.overview = overview;
        this.supportNeeds = supportNeeds;
        this.respiteGoals = respiteGoals;
        this.subjectFocus = subjectFocus;
        this.additionalNotes = additionalNotes;
        this.status = "SUBMITTED";
        this.rejectionReason = null;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ClientUser getClientUser() {
        return clientUser;
    }

    public String getSubmittedByName() {
        return submittedByName;
    }

    public String getSubmittedByEmail() {
        return submittedByEmail;
    }

    public String getSubmittedByPhone() {
        return submittedByPhone;
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

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
