package com.healthcare.management.controller;

import com.healthcare.management.dto.*;
import com.healthcare.management.service.DoctorService;
import com.healthcare.management.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/doctors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDoctorController {

    private final DoctorService doctorService;
    private final AppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<?> registerDoctor(@RequestBody DoctorRegisterRequest request) {
        try {
            DoctorResponse response = doctorService.registerDoctor(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during doctor registration: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<DoctorResponse>> getAllDoctors() {
        return ResponseEntity.ok(doctorService.getAllDoctors());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDoctorById(@PathVariable Long id) {
        try {
            DoctorResponse response = doctorService.getDoctorById(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDoctorProfile(
            @PathVariable Long id,
            @RequestBody DoctorProfileUpdateRequest request
    ) {
        try {
            DoctorResponse response = doctorService.updateDoctorProfile(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during profile update: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/leaves")
    public ResponseEntity<?> addDoctorLeave(
            @PathVariable Long id,
            @RequestBody DoctorLeaveRequest request
    ) {
        try {
            DoctorLeaveResponse response = doctorService.addDoctorLeave(id, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during scheduling leave: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/leaves")
    public ResponseEntity<?> getDoctorLeaves(@PathVariable Long id) {
        try {
            List<DoctorLeaveResponse> leaves = doctorService.getDoctorLeaves(id);
            return ResponseEntity.ok(leaves);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/leaves/{leaveId}")
    public ResponseEntity<?> deleteDoctorLeave(@PathVariable Long id, @PathVariable Long leaveId) {
        try {
            doctorService.deleteDoctorLeave(id, leaveId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/conflicts")
    public ResponseEntity<List<AppointmentDetailsResponse>> getCancelledConflicts() {
        return ResponseEntity.ok(appointmentService.getCancelledConflicts());
    }
}
