package com.healthcare.management.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "slot_holds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_by_patient_id", nullable = false)
    private User heldByPatient;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
