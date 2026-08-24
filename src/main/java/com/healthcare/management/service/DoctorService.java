package com.healthcare.management.service;

import com.healthcare.management.dto.*;
import com.healthcare.management.model.DoctorLeave;
import com.healthcare.management.model.DoctorProfile;
import com.healthcare.management.model.Role;
import com.healthcare.management.model.User;
import com.healthcare.management.model.Appointment;
import com.healthcare.management.model.AppointmentStatus;
import com.healthcare.management.repository.DoctorLeaveRepository;
import com.healthcare.management.repository.DoctorProfileRepository;
import com.healthcare.management.repository.UserRepository;
import com.healthcare.management.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    private final GoogleCalendarService googleCalendarService;

    @Transactional
    public DoctorResponse registerDoctor(DoctorRegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.DOCTOR)
                .build();

        User savedUser = userRepository.save(user);

        DoctorProfile profile = DoctorProfile.builder()
                .user(savedUser)
                .specialization(request.getSpecialization())
                .workingHoursStart(request.getWorkingHoursStart())
                .workingHoursEnd(request.getWorkingHoursEnd())
                .slotDurationMinutes(request.getSlotDurationMinutes())
                .build();

        DoctorProfile savedProfile = doctorProfileRepository.save(profile);

        return mapToDoctorResponse(savedProfile);
    }

    @Transactional
    public DoctorResponse updateDoctorProfile(Long id, DoctorProfileUpdateRequest request) {
        DoctorProfile profile = doctorProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor profile not found with ID: " + id));

        profile.setSpecialization(request.getSpecialization());
        profile.setWorkingHoursStart(request.getWorkingHoursStart());
        profile.setWorkingHoursEnd(request.getWorkingHoursEnd());
        profile.setSlotDurationMinutes(request.getSlotDurationMinutes());

        DoctorProfile updatedProfile = doctorProfileRepository.save(profile);
        return mapToDoctorResponse(updatedProfile);
    }

    public List<DoctorResponse> getAllDoctors() {
        return doctorProfileRepository.findAll().stream()
                .map(this::mapToDoctorResponse)
                .collect(Collectors.toList());
    }

    public DoctorResponse getDoctorById(Long id) {
        DoctorProfile profile = doctorProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor profile not found with ID: " + id));
        return mapToDoctorResponse(profile);
    }    @Transactional
    public DoctorLeaveResponse addDoctorLeave(Long doctorId, DoctorLeaveRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor profile not found with ID: " + doctorId));

        DoctorLeave leave = DoctorLeave.builder()
                .doctor(doctor)
                .leaveDate(request.getLeaveDate())
                .reason(request.getReason())
                .build();

        DoctorLeave savedLeave = doctorLeaveRepository.save(leave);

        // Conflict Resolution: Find all active appointments on leaveDate for this doctor
        List<AppointmentStatus> activeStatuses = List.of(AppointmentStatus.HELD, AppointmentStatus.CONFIRMED);
        List<Appointment> conflictingAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusIn(doctor.getId(), request.getLeaveDate(), activeStatuses);

        for (Appointment appointment : conflictingAppointments) {
            appointment.setStatus(AppointmentStatus.CANCELLED_BY_DOCTOR_LEAVE);
            appointment.setActiveBookingMarker(null); // Release hold/slot unique lock
            
            // Delete Google Calendar Events if connected
            try {
                googleCalendarService.cancelEvent(appointment);
            } catch (Exception e) {
                // Log warning and proceed so that DB transaction commits
                org.slf4j.LoggerFactory.getLogger(DoctorService.class)
                        .warn("Could not cancel Google Calendar event for appointment ID: {}. Error: {}", appointment.getId(), e.getMessage());
            }

            appointmentRepository.save(appointment);
            
            // Queue Notification email
            notificationService.queueLeaveCancellationEmail(appointment);
        }

        return mapToDoctorLeaveResponse(savedLeave);
    }
    public List<DoctorLeaveResponse> getDoctorLeaves(Long doctorId) {
        if (!doctorProfileRepository.existsById(doctorId)) {
            throw new IllegalArgumentException("Doctor profile not found with ID: " + doctorId);
        }
        return doctorLeaveRepository.findByDoctorId(doctorId).stream()
                .map(this::mapToDoctorLeaveResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDoctorLeave(Long doctorId, Long leaveId) {
        DoctorLeave leave = doctorLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor leave not found with ID: " + leaveId));

        if (!leave.getDoctor().getId().equals(doctorId)) {
            throw new IllegalArgumentException("Leave with ID " + leaveId + " does not belong to Doctor with ID " + doctorId);
        }

        doctorLeaveRepository.delete(leave);
    }

    private DoctorResponse mapToDoctorResponse(DoctorProfile profile) {
        return DoctorResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .fullName(profile.getUser().getFullName())
                .email(profile.getUser().getEmail())
                .specialization(profile.getSpecialization())
                .workingHoursStart(profile.getWorkingHoursStart())
                .workingHoursEnd(profile.getWorkingHoursEnd())
                .slotDurationMinutes(profile.getSlotDurationMinutes())
                .build();
    }

    private DoctorLeaveResponse mapToDoctorLeaveResponse(DoctorLeave leave) {
        return DoctorLeaveResponse.builder()
                .id(leave.getId())
                .doctorId(leave.getDoctor().getId())
                .leaveDate(leave.getLeaveDate())
                .reason(leave.getReason())
                .build();
    }
}
