package com.healthcare.management.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DoctorLeaveRequest {
    private LocalDate leaveDate;
    private String reason;
}
