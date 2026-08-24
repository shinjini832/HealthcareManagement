package com.healthcare.management.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", uniqueConstraints = @UniqueConstraint(
        columnNames = {"doctor_id", "appointment_date", "start_time", "active_booking_marker"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorProfile doctor;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "patient_symptoms", columnDefinition = "TEXT")
    private String patientSymptoms;

    @Column(name = "pre_visit_summary", columnDefinition = "TEXT")
    private String preVisitSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level")
    private UrgencyLevel urgencyLevel;

    @Column(name = "post_visit_notes", columnDefinition = "TEXT")
    private String postVisitNotes;

    @Column(name = "post_visit_summary", columnDefinition = "TEXT")
    private String postVisitSummary;

    @Column(name = "google_event_id_patient")
    private String googleEventIdPatient;

    @Column(name = "google_event_id_doctor")
    private String googleEventIdDoctor;

    @Column(name = "active_booking_marker")
    private Long activeBookingMarker;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
