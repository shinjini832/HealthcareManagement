package com.healthcare.management.dto;

import lombok.*;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotDto {
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean available;
}
