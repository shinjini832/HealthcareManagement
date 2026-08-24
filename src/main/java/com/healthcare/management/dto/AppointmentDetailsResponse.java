package com.healthcare.management.dto;

import com.healthcare.management.model.AppointmentStatus;
import com.healthcare.management.model.UrgencyLevel;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentDetailsResponse {
    private Long appointmentId;
    private String patientEmail;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private String doctorSpecialization;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private AppointmentStatus status;
    private UrgencyLevel urgencyLevel;
    private String patientSymptoms;
    private String preVisitSummary;
    private String postVisitNotes;
    private String postVisitSummary;
    private List<PrescriptionDto> prescriptions;
}
