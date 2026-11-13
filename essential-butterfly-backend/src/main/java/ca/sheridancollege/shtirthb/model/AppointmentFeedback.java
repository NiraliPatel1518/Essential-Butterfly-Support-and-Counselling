package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * The administrator's words about one appointment.
 *   feedback      - shown to the client and the administrator
 *   privateNotes  - the administrator only; never sent to a client
 * Both are kept permanently.
 */
@Entity
@Table(name = "appointment_feedback")
public class AppointmentFeedback {

    @Id
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(columnDefinition = "text")
    private String privateNotes;

    // When the feedback text was last written or changed (null if none).
    private LocalDateTime feedbackUpdatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AppointmentFeedback() {
    }

    public AppointmentFeedback(
            Long appointmentId,
            String feedback,
            String privateNotes,
            LocalDateTime now) {

        this.appointmentId = appointmentId;
        this.feedback = feedback;
        this.privateNotes = privateNotes;
        this.feedbackUpdatedAt = feedback == null ? null : now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void change(String feedback, String privateNotes, LocalDateTime now) {

        if (feedback != null && !feedback.equals(this.feedback)) {
            this.feedbackUpdatedAt = now;
        }

        this.feedback = feedback;
        this.privateNotes = privateNotes;
        this.updatedAt = now;
    }

    public LocalDateTime getFeedbackUpdatedAt() {
        return feedbackUpdatedAt;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public String getFeedback() {
        return feedback;
    }

    public String getPrivateNotes() {
        return privateNotes;
    }
}
