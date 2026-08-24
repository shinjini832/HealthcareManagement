package com.healthcare.management.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotHoldRequest {
    private Long doctorId;
    private LocalDate slotDate;
    private LocalTime startTime;
}
