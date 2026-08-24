package com.healthcare.management.dto;

import com.healthcare.management.model.Frequency;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PrescriptionDto {
    private String medicationName;
    private String dosage;
    private Frequency frequency;
    private int durationDays;
}
