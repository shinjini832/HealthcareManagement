package com.healthcare.management.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotHoldResponse {
    private Long holdId;
    private Long doctorId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalDateTime expiresAt;
    private String heldByPatientEmail;
}
