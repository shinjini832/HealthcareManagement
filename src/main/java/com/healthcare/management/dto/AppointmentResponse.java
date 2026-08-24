package com.healthcare.management.dto;

import com.healthcare.management.model.AppointmentStatus;
import com.healthcare.management.model.UrgencyLevel;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentResponse {
    private Long appointmentId;
    private String patientEmail;
    private Long doctorId;
    private String doctorName;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private AppointmentStatus status;
    private UrgencyLevel urgencyLevel;
    private String patientSymptoms;
}
