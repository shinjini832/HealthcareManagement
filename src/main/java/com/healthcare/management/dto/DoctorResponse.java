package com.healthcare.management.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DoctorResponse {
    private Long id; // DoctorProfile ID
    private Long userId;
    private String fullName;
    private String email;
    private String specialization;
    private String workingHoursStart;
    private String workingHoursEnd;
    private int slotDurationMinutes;
}
