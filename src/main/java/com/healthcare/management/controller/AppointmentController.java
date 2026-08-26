package com.healthcare.management.controller;

import com.healthcare.management.dto.AppointmentConfirmRequest;
import com.healthcare.management.dto.AppointmentResponse;
import com.healthcare.management.dto.AppointmentCompleteRequest;
import com.healthcare.management.dto.AppointmentDetailsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import com.healthcare.management.dto.SlotDto;
import com.healthcare.management.dto.SlotHoldRequest;
import com.healthcare.management.dto.SlotHoldResponse;
import com.healthcare.management.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Slf4j
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping("/slots")
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        try {
            List<SlotDto> slots = appointmentService.getAvailableSlots(doctorId, date);
            return ResponseEntity.ok(slots);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid slot request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to fetch slots for doctorId: {} on date: {}", doctorId, date, e);
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/hold")
    public ResponseEntity<?> placeSlotHold(
            @RequestBody SlotHoldRequest request,
            Principal principal
    ) {
        try {
            SlotHoldResponse response = appointmentService.placeSlotHold(request, principal.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmAppointment(
            @RequestBody AppointmentConfirmRequest request,
            Principal principal
    ) {
        try {
            AppointmentResponse response = appointmentService.confirmAppointment(request, principal.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> completeAppointment(
            @PathVariable Long id,
            @RequestBody AppointmentCompleteRequest request,
            Principal principal
    ) {
        try {
            AppointmentResponse response = appointmentService.completeAppointment(id, request, principal.getName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }

    @GetMapping("/patient")
    public ResponseEntity<?> getPatientAppointments(Principal principal) {
        try {
            List<AppointmentDetailsResponse> list = appointmentService.getPatientAppointments(principal.getName());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }

    @GetMapping("/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getDoctorAppointments(Principal principal) {
        try {
            List<AppointmentDetailsResponse> list = appointmentService.getDoctorAppointments(principal.getName());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }
}
