package com.healthcare.management.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DoctorLeaveResponse {
    private Long id;
    private Long doctorId;
    private LocalDate leaveDate;
    private String reason;
}
