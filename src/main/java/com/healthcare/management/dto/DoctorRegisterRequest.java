package com.healthcare.management.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DoctorRegisterRequest {
    private String fullName;
    private String email;
    private String password;
    private String specialization;
    private String workingHoursStart; // format "HH:mm"
    private String workingHoursEnd;   // format "HH:mm"
    private int slotDurationMinutes;
}
