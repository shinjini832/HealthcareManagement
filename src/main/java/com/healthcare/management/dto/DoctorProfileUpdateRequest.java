package com.healthcare.management.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DoctorProfileUpdateRequest {
    private String specialization;
    private String workingHoursStart; // format "HH:mm"
    private String workingHoursEnd;   // format "HH:mm"
    private int slotDurationMinutes;
}
