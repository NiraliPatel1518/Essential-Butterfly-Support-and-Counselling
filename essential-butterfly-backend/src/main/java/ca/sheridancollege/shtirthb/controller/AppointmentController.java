package ca.sheridancollege.shtirthb.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ca.sheridancollege.shtirthb.dto.AppointmentBookingRequest;
import ca.sheridancollege.shtirthb.dto.AppointmentSlotResponse;
import ca.sheridancollege.shtirthb.dto.ClientAppointmentResponse;
import ca.sheridancollege.shtirthb.service.AppointmentScheduleService;
import ca.sheridancollege.shtirthb.service.AppointmentService;

@RestController
public class AppointmentController {

    private final AppointmentScheduleService schedule;
    private final AppointmentService appointments;

    public AppointmentController(
            AppointmentScheduleService schedule,
            AppointmentService appointments) {

        this.schedule = schedule;
        this.appointments = appointments;
    }

    // ------------------------------------------------------------- public

    // Visitors: the open slots only. No booking or client information.
    @GetMapping("/api/appointments/availability")
    public ResponseEntity<List<AppointmentSlotResponse>> getOpenSlots() {
        return ResponseEntity.ok(schedule.getOpenSlots());
    }

    // ------------------------------------------------------------- client

    @PostMapping("/api/appointments")
    public ResponseEntity<ClientAppointmentResponse> request(
            @RequestBody AppointmentBookingRequest request,
            Authentication authentication) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(appointments.request(authentication.getName(), request));
    }

    @GetMapping("/api/appointments/mine")
    public ResponseEntity<List<ClientAppointmentResponse>> mine(
            Authentication authentication) {

        return ResponseEntity.ok(appointments.mine(authentication.getName()));
    }
}
