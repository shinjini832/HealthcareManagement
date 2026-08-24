package com.healthcare.management.dto;

import com.healthcare.management.model.UrgencyLevel;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentConfirmRequest {
    private Long slotHoldId;
    private Long doctorId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private String patientSymptoms;
    private UrgencyLevel urgencyLevel;
}
