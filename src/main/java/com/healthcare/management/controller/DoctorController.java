package com.healthcare.management.controller;

import com.healthcare.management.dto.DoctorResponse;
import com.healthcare.management.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<List<DoctorResponse>> searchDoctors(
            @RequestParam(required = false) String specialization
    ) {
        List<DoctorResponse> doctors = appointmentService.searchDoctors(specialization);
        return ResponseEntity.ok(doctors);
    }
}
