package com.healthcare.management.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentCompleteRequest {
    private String postVisitNotes;
    private List<PrescriptionDto> prescriptions;
}
