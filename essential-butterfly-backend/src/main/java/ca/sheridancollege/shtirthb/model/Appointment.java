package ca.sheridancollege.shtirthb.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/*
 * A client's booking request for one slot.
 *
 * REQUESTED  waiting for the administrator
 * CONFIRMED  accepted (a meeting link and calendar event may be attached)
 * DECLINED   refused, with the administrator's reason
 * CANCELLED  withdrawn by the administrator before it started
 * COMPLETED  the appointment time has passed
 *
 * The client's name, e-mail address and phone number are never copied here;
 * they are read from the client's account.
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    public static final String REQUESTED = "REQUESTED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";
    public static final String COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private ClientUser client;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 500)
    private String statusReason;

    @Column(length = 2000)
    private String meetingLink;

    private LocalDateTime meetingLinkSentAt;

    @Column(length = 255)
    private String googleEventId;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Appointment() {
    }

    public Appointment(
            ClientUser client,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            LocalDateTime now) {

        this.client = client;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = REQUESTED;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    public void confirm(LocalDateTime now) {
        this.status = CONFIRMED;
        this.updatedAt = now;
    }

    public void decline(String reason, LocalDateTime now) {
        this.status = DECLINED;
        this.statusReason = reason;
        this.updatedAt = now;
    }

    // A cancelled appointment has no meeting link or calendar event any more.
    public void cancel(String reason, LocalDateTime now) {
        this.status = CANCELLED;
        this.statusReason = reason;
        this.meetingLink = null;
        this.meetingLinkSentAt = null;
        this.googleEventId = null;
        this.updatedAt = now;
    }

    // A new link has not been e-mailed yet.
    public void saveMeetingLink(String link, LocalDateTime now) {
        this.meetingLink = link;
        this.meetingLinkSentAt = null;
        this.updatedAt = now;
    }

    public void markInvitationSent(LocalDateTime now) {
        this.meetingLinkSentAt = now;
        this.updatedAt = now;
    }

    public void saveGoogleEventId(String eventId, LocalDateTime now) {
        this.googleEventId = eventId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public ClientUser getClient() {
        return client;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public String getMeetingLink() {
        return meetingLink;
    }

    public LocalDateTime getMeetingLinkSentAt() {
        return meetingLinkSentAt;
    }

    public String getGoogleEventId() {
        return googleEventId;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }
}
