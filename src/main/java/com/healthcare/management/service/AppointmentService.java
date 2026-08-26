package com.healthcare.management.service;

import com.healthcare.management.dto.AppointmentConfirmRequest;
import com.healthcare.management.dto.AppointmentResponse;
import com.healthcare.management.dto.AppointmentCompleteRequest;
import com.healthcare.management.dto.AppointmentDetailsResponse;
import com.healthcare.management.dto.DoctorResponse;
import com.healthcare.management.model.Frequency;
import java.util.stream.Collectors;
import com.healthcare.management.dto.PrescriptionDto;
import com.healthcare.management.model.Prescription;
import com.healthcare.management.model.UrgencyLevel;
import com.healthcare.management.repository.PrescriptionRepository;
import com.healthcare.management.dto.SlotDto;
import com.healthcare.management.dto.SlotHoldRequest;
import com.healthcare.management.dto.SlotHoldResponse;
import org.springframework.transaction.annotation.Isolation;
import com.healthcare.management.model.Appointment;
import com.healthcare.management.model.AppointmentStatus;
import com.healthcare.management.model.DoctorLeave;
import com.healthcare.management.model.DoctorProfile;
import com.healthcare.management.model.SlotHold;
import com.healthcare.management.model.User;
import com.healthcare.management.repository.AppointmentRepository;
import com.healthcare.management.repository.DoctorLeaveRepository;
import com.healthcare.management.repository.DoctorProfileRepository;
import com.healthcare.management.repository.SlotHoldRepository;
import com.healthcare.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotHoldRepository slotHoldRepository;
    private final UserRepository userRepository;
    private final GoogleCalendarService googleCalendarService;
    private final NotificationService notificationService;
    private final LlmService llmService;
    private final PrescriptionRepository prescriptionRepository;

    public List<SlotDto> getAvailableSlots(Long doctorId, LocalDate date) {
        // 1. Checks if the doctor has a leave registered for the date
        List<DoctorLeave> leaves = doctorLeaveRepository.findByDoctorId(doctorId);
        boolean isOnLeave = leaves.stream()
                .anyMatch(leave -> leave.getLeaveDate().equals(date));
        if (isOnLeave) {
            return Collections.emptyList();
        }

        // 2. Fetch Doctor Profile Details
        DoctorProfile profile = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor profile not found with ID: " + doctorId));

        LocalTime start = parseLocalTime(profile.getWorkingHoursStart(), LocalTime.of(9, 0));
        LocalTime end = parseLocalTime(profile.getWorkingHoursEnd(), LocalTime.of(17, 0));
        int duration = profile.getSlotDurationMinutes();
        if (duration <= 0) {
            throw new IllegalArgumentException("Doctor slot duration must be greater than 0 minutes");
        }

        // 3. Generates base slots
        List<SlotDto> baseSlots = new ArrayList<>();
        LocalTime currentSlotStart = start;
        while (currentSlotStart.plusMinutes(duration).isBefore(end) || currentSlotStart.plusMinutes(duration).equals(end)) {
            LocalTime currentSlotEnd = currentSlotStart.plusMinutes(duration);
            baseSlots.add(SlotDto.builder()
                    .startTime(currentSlotStart)
                    .endTime(currentSlotEnd)
                    .available(true)
                    .build());
            currentSlotStart = currentSlotEnd;
        }

        // 4. Fetches active appointments and active holds
        List<AppointmentStatus> activeStatuses = List.of(AppointmentStatus.HELD, AppointmentStatus.CONFIRMED);
        List<Appointment> activeAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusIn(doctorId, date, activeStatuses);

        List<SlotHold> activeHolds = slotHoldRepository
                .findByDoctorIdAndSlotDateAndExpiresAtAfter(doctorId, date, LocalDateTime.now());

        // 5. Cross-reference availability
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalTime nowTime = LocalTime.now(ist);
        LocalDate today = LocalDate.now(ist);

        for (SlotDto slot : baseSlots) {
            LocalTime slotStart = slot.getStartTime();
            LocalTime slotEnd = slot.getEndTime();

            // Check if the slot is in the past for today's date
            if (date.equals(today) && slotStart.isBefore(nowTime)) {
                slot.setAvailable(false);
                continue;
            }

            // Check if slot overlaps with any active appointment
            boolean overlapsAppointment = activeAppointments.stream().anyMatch(app -> 
                !(slotEnd.isBefore(app.getStartTime()) || slotEnd.equals(app.getStartTime()) || 
                  slotStart.isAfter(app.getEndTime()) || slotStart.equals(app.getEndTime()))
            );

            if (overlapsAppointment) {
                slot.setAvailable(false);
                continue;
            }

            // Check if slot overlaps with any active hold
            boolean overlapsHold = activeHolds.stream().anyMatch(hold -> {
                LocalTime holdEnd = hold.getStartTime().plusMinutes(profile.getSlotDurationMinutes());
                return !(slotEnd.isBefore(hold.getStartTime()) || slotEnd.equals(hold.getStartTime()) || 
                         slotStart.isAfter(holdEnd) || slotStart.equals(holdEnd));
            });

            if (overlapsHold) {
                slot.setAvailable(false);
            }
        }

        return baseSlots;
    }

    @Transactional
    public SlotHoldResponse placeSlotHold(SlotHoldRequest request, String patientEmail) {
        // 1. Fetch current patient user and doctor profile
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new IllegalArgumentException("Patient user not found with email: " + patientEmail));

        DoctorProfile doctor = doctorProfileRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor profile not found with ID: " + request.getDoctorId()));

        LocalDate slotDate = request.getSlotDate();
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = startTime.plusMinutes(doctor.getSlotDurationMinutes());

        // 2. Verify that the doctor is not on leave
        List<DoctorLeave> leaves = doctorLeaveRepository.findByDoctorId(doctor.getId());
        boolean isOnLeave = leaves.stream()
                .anyMatch(leave -> leave.getLeaveDate().equals(slotDate));
        if (isOnLeave) {
            throw new IllegalArgumentException("Doctor is on leave on the selected date: " + slotDate);
        }

        // Verify slot falls within working hours
        LocalTime workingStart = parseLocalTime(doctor.getWorkingHoursStart(), LocalTime.of(9, 0));
        LocalTime workingEnd = parseLocalTime(doctor.getWorkingHoursEnd(), LocalTime.of(17, 0));
        if (startTime.isBefore(workingStart) || endTime.isAfter(workingEnd)) {
            throw new IllegalArgumentException("Slot time falls outside the doctor's working hours");
        }

        // Verify slot is not in the past
        LocalDateTime slotDateTime = LocalDateTime.of(slotDate, startTime);
        if (slotDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot place a hold on a past slot");
        }

        // 3. Acquire a pessimistic write lock on active holds for this slot to check if it's already held
        List<SlotHold> activeHolds = slotHoldRepository
                .findActiveHoldsForUpdate(doctor.getId(), slotDate, startTime, LocalDateTime.now());

        if (!activeHolds.isEmpty()) {
            throw new IllegalStateException("Slot is already temporarily held by another patient");
        }

        // 4. Query active appointments for this slot
        List<AppointmentStatus> activeStatuses = List.of(AppointmentStatus.HELD, AppointmentStatus.CONFIRMED);
        List<Appointment> activeAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusIn(doctor.getId(), slotDate, activeStatuses);

        boolean overlapsAppointment = activeAppointments.stream().anyMatch(app -> 
            !(endTime.isBefore(app.getStartTime()) || endTime.equals(app.getStartTime()) || 
              startTime.isAfter(app.getEndTime()) || startTime.equals(app.getEndTime()))
        );

        if (overlapsAppointment) {
            throw new IllegalStateException("Slot is already booked");
        }

        // 5. Place the hold
        SlotHold hold = SlotHold.builder()
                .doctor(doctor)
                .slotDate(slotDate)
                .startTime(startTime)
                .heldByPatient(patient)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        SlotHold savedHold = slotHoldRepository.save(hold);

        return SlotHoldResponse.builder()
                .holdId(savedHold.getId())
                .doctorId(doctor.getId())
                .slotDate(slotDate)
                .startTime(startTime)
                .expiresAt(savedHold.getExpiresAt())
                .heldByPatientEmail(patient.getEmail())
                .build();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AppointmentResponse confirmAppointment(AppointmentConfirmRequest request, String patientEmail) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new IllegalArgumentException("Patient user not found with email: " + patientEmail));

        DoctorProfile doctor;
        LocalDate slotDate;
        LocalTime startTime;
        SlotHold activeHold = null;

        // 1. Resolve details and validate hold if slotHoldId is provided
        if (request.getSlotHoldId() != null) {
            activeHold = slotHoldRepository.findById(request.getSlotHoldId())
                    .orElseThrow(() -> new IllegalArgumentException("Slot hold not found with ID: " + request.getSlotHoldId()));

            // Verify hold belongs to the requesting patient
            if (!activeHold.getHeldByPatient().getEmail().equals(patientEmail)) {
                throw new IllegalArgumentException("This slot hold belongs to a different patient");
            }

            // Verify hold is not expired
            if (activeHold.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new IllegalStateException("Slot hold has expired");
            }

            doctor = activeHold.getDoctor();
            slotDate = activeHold.getSlotDate();
            startTime = activeHold.getStartTime();
        } else {
            // Direct confirmation flow (checking availability from scratch)
            if (request.getDoctorId() == null || request.getSlotDate() == null || request.getStartTime() == null) {
                throw new IllegalArgumentException("Doctor ID, date, and start time must be provided when slotHoldId is null");
            }

            doctor = doctorProfileRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new IllegalArgumentException("Doctor profile not found with ID: " + request.getDoctorId()));
            slotDate = request.getSlotDate();
            startTime = request.getStartTime();

            // Verify doctor leave
            List<DoctorLeave> leaves = doctorLeaveRepository.findByDoctorId(doctor.getId());
            boolean isOnLeave = leaves.stream().anyMatch(leave -> leave.getLeaveDate().equals(slotDate));
            if (isOnLeave) {
                throw new IllegalArgumentException("Doctor is on leave on the selected date: " + slotDate);
            }

            // Verify working hours
            LocalTime workingStart = LocalTime.parse(doctor.getWorkingHoursStart());
            LocalTime workingEnd = LocalTime.parse(doctor.getWorkingHoursEnd());
            LocalTime endTime = startTime.plusMinutes(doctor.getSlotDurationMinutes());
            if (startTime.isBefore(workingStart) || endTime.isAfter(workingEnd)) {
                throw new IllegalArgumentException("Slot time falls outside the doctor's working hours");
            }

            // Verify slot is not in the past
            if (LocalDateTime.of(slotDate, startTime).isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Cannot book a past slot");
            }

            // Verify no other active holds exist on this slot (by other patients)
            List<SlotHold> otherHolds = slotHoldRepository.findByDoctorIdAndSlotDateAndExpiresAtAfter(doctor.getId(), slotDate, LocalDateTime.now());
            boolean slotIsHeldByOther = otherHolds.stream().anyMatch(hold -> 
                hold.getStartTime().equals(startTime) && !hold.getHeldByPatient().getEmail().equals(patientEmail)
            );
            if (slotIsHeldByOther) {
                throw new IllegalStateException("Slot is temporarily held by another patient");
            }
        }

        LocalTime endTime = startTime.plusMinutes(doctor.getSlotDurationMinutes());

        // 2. Query existing active appointments for the slot
        List<AppointmentStatus> activeStatuses = List.of(AppointmentStatus.HELD, AppointmentStatus.CONFIRMED);
        List<Appointment> activeAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusIn(doctor.getId(), slotDate, activeStatuses);

        boolean overlapsAppointment = activeAppointments.stream().anyMatch(app -> 
            !(endTime.isBefore(app.getStartTime()) || endTime.equals(app.getStartTime()) || 
              startTime.isAfter(app.getEndTime()) || startTime.equals(app.getEndTime()))
        );

        if (overlapsAppointment) {
            throw new IllegalStateException("Slot is already booked");
        }

        // 3. Determine urgency level and pre-visit summary
        String symptoms = request.getPatientSymptoms();
        String preVisitSummary = null;
        UrgencyLevel urgencyLevel = request.getUrgencyLevel();

        if (symptoms != null && !symptoms.trim().isEmpty()) {
            preVisitSummary = llmService.generatePreVisitSummary(symptoms);
            if (urgencyLevel == null) {
                urgencyLevel = parseUrgency(preVisitSummary);
            }
        }
        if (urgencyLevel == null) {
            urgencyLevel = UrgencyLevel.MEDIUM; // default fallback
        }

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .appointmentDate(slotDate)
                .startTime(startTime)
                .endTime(endTime)
                .status(AppointmentStatus.CONFIRMED)
                .activeBookingMarker(1L)
                .patientSymptoms(symptoms)
                .preVisitSummary(preVisitSummary)
                .urgencyLevel(urgencyLevel)
                .build();

        Appointment savedAppointment = appointmentRepository.save(appointment);

        // 4. Delete the slot hold if one was used
        if (activeHold != null) {
            slotHoldRepository.delete(activeHold);
        }

        // 5. Integrate Google Calendar Event and Queue Notification
        googleCalendarService.createEvent(savedAppointment);
        savedAppointment = appointmentRepository.save(savedAppointment);
        notificationService.queueAppointmentConfirmation(savedAppointment);

        return AppointmentResponse.builder()
                .appointmentId(savedAppointment.getId())
                .patientEmail(patient.getEmail())
                .doctorId(doctor.getId())
                .doctorName(doctor.getUser().getFullName())
                .appointmentDate(savedAppointment.getAppointmentDate())
                .startTime(savedAppointment.getStartTime())
                .endTime(savedAppointment.getEndTime())
                .status(savedAppointment.getStatus())
                .urgencyLevel(savedAppointment.getUrgencyLevel())
                .patientSymptoms(savedAppointment.getPatientSymptoms())
                .build();
    }

    private UrgencyLevel parseUrgency(String summaryText) {
        if (summaryText == null) return UrgencyLevel.MEDIUM;
        String upper = summaryText.toUpperCase();
        if (upper.contains("HIGH")) {
            return UrgencyLevel.HIGH;
        } else if (upper.contains("LOW")) {
            return UrgencyLevel.LOW;
        }
        return UrgencyLevel.MEDIUM;
    }

    @Transactional
    public AppointmentResponse completeAppointment(Long appointmentId, AppointmentCompleteRequest request, String doctorEmail) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found with ID: " + appointmentId));

        // Verify the logged-in doctor is the one assigned
        if (!appointment.getDoctor().getUser().getEmail().equalsIgnoreCase(doctorEmail)) {
            throw new IllegalArgumentException("You are not authorized to complete this appointment");
        }

        // 1. Update clinical notes and call LLM for post-visit summary
        String notes = request.getPostVisitNotes();
        appointment.setPostVisitNotes(notes);
        if (notes != null && !notes.trim().isEmpty()) {
            String postVisitSummary = llmService.generatePostVisitSummary(notes);
            appointment.setPostVisitSummary(postVisitSummary);
        }

        Appointment savedAppointment = appointmentRepository.save(appointment);

        // 2. Create prescriptions if provided
        if (request.getPrescriptions() != null) {
            for (PrescriptionDto dto : request.getPrescriptions()) {
                Prescription prescription = Prescription.builder()
                        .appointment(savedAppointment)
                        .medicationName(dto.getMedicationName())
                        .dosage(dto.getDosage())
                        .frequency(dto.getFrequency())
                        .durationDays(dto.getDurationDays())
                        .startDate(LocalDate.now())
                        .active(true)
                        .build();
                prescriptionRepository.save(prescription);
            }
        }

        // 3. Enqueue appointment complete notification
        notificationService.queuePostVisitSummary(savedAppointment);

        return AppointmentResponse.builder()
                .appointmentId(savedAppointment.getId())
                .patientEmail(savedAppointment.getPatient().getEmail())
                .doctorId(savedAppointment.getDoctor().getId())
                .doctorName(savedAppointment.getDoctor().getUser().getFullName())
                .appointmentDate(savedAppointment.getAppointmentDate())
                .startTime(savedAppointment.getStartTime())
                .endTime(savedAppointment.getEndTime())
                .status(savedAppointment.getStatus())
                .urgencyLevel(savedAppointment.getUrgencyLevel())
                .patientSymptoms(savedAppointment.getPatientSymptoms())
                .build();
    }

    public List<AppointmentDetailsResponse> getPatientAppointments(String patientEmail) {
        return appointmentRepository.findByPatientEmailOrderByAppointmentDateDescStartTimeDesc(patientEmail).stream()
                .map(this::mapToAppointmentDetailsResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentDetailsResponse> getDoctorAppointments(String doctorEmail) {
        return appointmentRepository.findByDoctorUserEmailOrderByAppointmentDateAscStartTimeAsc(doctorEmail).stream()
                .map(this::mapToAppointmentDetailsResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentDetailsResponse> getCancelledConflicts() {
        return appointmentRepository.findByStatusOrderByAppointmentDateDesc(AppointmentStatus.CANCELLED_BY_DOCTOR_LEAVE).stream()
                .map(this::mapToAppointmentDetailsResponse)
                .collect(Collectors.toList());
    }

    public List<DoctorResponse> searchDoctors(String specialization) {
        List<DoctorProfile> profiles;
        if (specialization != null && !specialization.trim().isEmpty()) {
            profiles = doctorProfileRepository.findBySpecializationContainingIgnoreCase(specialization);
        } else {
            profiles = doctorProfileRepository.findAll();
        }
        return profiles.stream()
                .map(profile -> DoctorResponse.builder()
                        .id(profile.getId())
                        .userId(profile.getUser().getId())
                        .fullName(profile.getUser().getFullName())
                        .email(profile.getUser().getEmail())
                        .specialization(profile.getSpecialization())
                        .workingHoursStart(profile.getWorkingHoursStart())
                        .workingHoursEnd(profile.getWorkingHoursEnd())
                        .slotDurationMinutes(profile.getSlotDurationMinutes())
                        .build())
                .collect(Collectors.toList());
    }

    private AppointmentDetailsResponse mapToAppointmentDetailsResponse(Appointment app) {
        List<PrescriptionDto> prescriptions = prescriptionRepository.findByAppointmentId(app.getId()).stream()
                .map(p -> PrescriptionDto.builder()
                        .medicationName(p.getMedicationName())
                        .dosage(p.getDosage())
                        .frequency(p.getFrequency())
                        .durationDays(p.getDurationDays())
                        .build())
                .collect(Collectors.toList());

        return AppointmentDetailsResponse.builder()
                .appointmentId(app.getId())
                .patientEmail(app.getPatient().getEmail())
                .patientName(app.getPatient().getFullName())
                .doctorId(app.getDoctor().getId())
                .doctorName(app.getDoctor().getUser().getFullName())
                .doctorSpecialization(app.getDoctor().getSpecialization())
                .appointmentDate(app.getAppointmentDate())
                .startTime(app.getStartTime())
                .endTime(app.getEndTime())
                .status(app.getStatus())
                .urgencyLevel(app.getUrgencyLevel())
                .patientSymptoms(app.getPatientSymptoms())
                .preVisitSummary(app.getPreVisitSummary())
                .postVisitNotes(app.getPostVisitNotes())
                .postVisitSummary(app.getPostVisitSummary())
                .prescriptions(prescriptions)
                .build();
    }

    private LocalTime parseLocalTime(String timeStr, LocalTime fallback) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return fallback;
        }
        String trimmed = timeStr.trim();
        try {
            return LocalTime.parse(trimmed);
        } catch (Exception e) {
            try {
                return LocalTime.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("H:mm[:ss]"));
            } catch (Exception ex) {
                return fallback;
            }
        }
    }
}
