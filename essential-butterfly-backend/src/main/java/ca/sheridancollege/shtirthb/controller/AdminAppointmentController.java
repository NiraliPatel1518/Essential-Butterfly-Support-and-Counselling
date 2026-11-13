package ca.sheridancollege.shtirthb.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.AdminAppointmentResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentActionResponse;
import ca.sheridancollege.shtirthb.dto.AppointmentFeedbackRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentReasonRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentSettingsRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentSettingsResponse;
import ca.sheridancollege.shtirthb.dto.AvailabilityRuleRequest;
import ca.sheridancollege.shtirthb.dto.AvailabilityRuleResponse;
import ca.sheridancollege.shtirthb.dto.MeetingInvitationRequest;
import ca.sheridancollege.shtirthb.service.AppointmentScheduleService;
import ca.sheridancollege.shtirthb.service.AppointmentService;
import ca.sheridancollege.shtirthb.service.AppointmentWorkflowService;

// Everything under /api/admin/** already requires the ADMIN role.
@RestController
@RequestMapping("/api/admin/appointments")
public class AdminAppointmentController {

    private final AppointmentScheduleService schedule;
    private final AppointmentService appointments;
    private final AppointmentWorkflowService workflow;

    public AdminAppointmentController(
            AppointmentScheduleService schedule,
            AppointmentService appointments,
            AppointmentWorkflowService workflow) {

        this.schedule = schedule;
        this.appointments = appointments;
        this.workflow = workflow;
    }

    // ------------------------------------------------- weekly availability

    @GetMapping("/availability")
    public ResponseEntity<List<AvailabilityRuleResponse>> getRules() {
        return ResponseEntity.ok(schedule.getRules());
    }

    @PostMapping("/availability")
    public ResponseEntity<AvailabilityRuleResponse> addRule(
            @RequestBody AvailabilityRuleRequest request,
            Authentication authentication) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(schedule.addRule(request, authentication.getName()));
    }

    @PutMapping("/availability/{id}")
    public ResponseEntity<AvailabilityRuleResponse> changeRule(
            @PathVariable Long id,
            @RequestBody AvailabilityRuleRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                schedule.changeRule(id, request, authentication.getName()));
    }

    @DeleteMapping("/availability/{id}")
    public ResponseEntity<Void> removeRule(
            @PathVariable Long id,
            Authentication authentication) {

        schedule.removeRule(id, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------- settings

    @GetMapping("/settings")
    public ResponseEntity<AppointmentSettingsResponse> getSettings() {
        return ResponseEntity.ok(schedule.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<AppointmentSettingsResponse> changeSettings(
            @RequestBody AppointmentSettingsRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                schedule.changeSettings(request, authentication.getName()));
    }

    // ------------------------------------------------------- appointments

    // All appointments; "search" matches the client's name, e-mail or phone.
    @GetMapping
    public ResponseEntity<List<AdminAppointmentResponse>> list(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status) {

        return ResponseEntity.ok(appointments.adminList(search, status));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<AppointmentActionResponse> confirm(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
                workflow.confirm(id, authentication.getName()));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<AppointmentActionResponse> decline(
            @PathVariable Long id,
            @RequestBody AppointmentReasonRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                workflow.decline(id, request.reason(), authentication.getName()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AppointmentActionResponse> cancel(
            @PathVariable Long id,
            @RequestBody AppointmentReasonRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                workflow.cancel(id, request.reason(), authentication.getName()));
    }

    @PostMapping("/{id}/calendar")
    public ResponseEntity<AppointmentActionResponse> addToCalendar(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
                workflow.addToCalendar(id, authentication.getName()));
    }

    // The invitation form: saves the meeting link and e-mails it to the client.
    @PutMapping("/{id}/invitation")
    public ResponseEntity<AppointmentActionResponse> sendInvitation(
            @PathVariable Long id,
            @RequestBody MeetingInvitationRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                workflow.sendInvitation(
                        id, request.meetingLink(), authentication.getName()));
    }

    @PostMapping("/{id}/invitation/resend")
    public ResponseEntity<AppointmentActionResponse> resendInvitation(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
                workflow.resendInvitation(id, authentication.getName()));
    }

    @PutMapping("/{id}/feedback")
    public ResponseEntity<AdminAppointmentResponse> saveFeedback(
            @PathVariable Long id,
            @RequestBody AppointmentFeedbackRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                appointments.saveFeedback(id, request, authentication.getName()));
    }
}
