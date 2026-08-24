package com.healthcare.management;

import com.healthcare.management.dto.AppointmentConfirmRequest;
import com.healthcare.management.dto.AppointmentResponse;
import com.healthcare.management.dto.DoctorLeaveRequest;
import com.healthcare.management.model.Appointment;
import com.healthcare.management.dto.AppointmentCompleteRequest;
import com.healthcare.management.dto.PrescriptionDto;
import com.healthcare.management.dto.SlotHoldRequest;
import com.healthcare.management.dto.SlotHoldResponse;
import com.healthcare.management.model.Frequency;
import com.healthcare.management.model.Prescription;
import com.healthcare.management.repository.PrescriptionRepository;
import com.healthcare.management.repository.NotificationQueueRepository;
import com.healthcare.management.scheduler.PrescriptionScheduler;
import com.healthcare.management.model.AppointmentStatus;
import com.healthcare.management.model.DoctorProfile;
import com.healthcare.management.model.Role;
import com.healthcare.management.model.User;
import com.healthcare.management.repository.AppointmentRepository;
import com.healthcare.management.repository.DoctorLeaveRepository;
import com.healthcare.management.repository.DoctorProfileRepository;
import com.healthcare.management.repository.SlotHoldRepository;
import com.healthcare.management.repository.UserRepository;
import com.healthcare.management.service.AppointmentService;
import com.healthcare.management.service.DoctorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AppointmentConfirmTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorProfileRepository doctorProfileRepository;

    @Autowired
    private SlotHoldRepository slotHoldRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorLeaveRepository doctorLeaveRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private NotificationQueueRepository notificationQueueRepository;

    @Autowired
    private PrescriptionScheduler prescriptionScheduler;

    private User patient;
    private User doctorUser;
    private DoctorProfile doctorProfile;

    @BeforeEach
    void setUp() {
        // Cleanup existing data
        cleanup();

        // Create doctor user & profile
        doctorUser = User.builder()
                .email("doctor@healthcare.com")
                .password("password")
                .fullName("Dr. John Watson")
                .role(Role.DOCTOR)
                .build();
        doctorUser = userRepository.save(doctorUser);

        doctorProfile = DoctorProfile.builder()
                .user(doctorUser)
                .specialization("General")
                .workingHoursStart("09:00")
                .workingHoursEnd("17:00")
                .slotDurationMinutes(30)
                .build();
        doctorProfile = doctorProfileRepository.save(doctorProfile);

        // Create patient user
        patient = User.builder()
                .email("patient@healthcare.com")
                .password("password")
                .fullName("Sherlock Holmes")
                .role(Role.PATIENT)
                .build();
        patient = userRepository.save(patient);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        prescriptionRepository.deleteAll();
        appointmentRepository.deleteAll();
        slotHoldRepository.deleteAll();
        doctorLeaveRepository.deleteAll();
        doctorProfileRepository.deleteAll();
        notificationQueueRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testConfirmAppointmentFlow() {
        LocalDate date = LocalDate.now().plusDays(2);
        LocalTime time = LocalTime.of(10, 0);

        // 1. Place a slot hold
        SlotHoldRequest holdRequest = SlotHoldRequest.builder()
                .doctorId(doctorProfile.getId())
                .slotDate(date)
                .startTime(time)
                .build();

        SlotHoldResponse holdResponse = appointmentService.placeSlotHold(holdRequest, patient.getEmail());
        assertNotNull(holdResponse);
        assertNotNull(holdResponse.getHoldId());

        // 2. Confirm the hold
        AppointmentConfirmRequest confirmRequest = AppointmentConfirmRequest.builder()
                .slotHoldId(holdResponse.getHoldId())
                .patientSymptoms("Frequent cough")
                .build();

        AppointmentResponse confirmResponse = appointmentService.confirmAppointment(confirmRequest, patient.getEmail());
        assertNotNull(confirmResponse);
        assertEquals(holdResponse.getDoctorId(), confirmResponse.getDoctorId());
        assertEquals(holdResponse.getSlotDate(), confirmResponse.getAppointmentDate());
        assertEquals(holdResponse.getStartTime(), confirmResponse.getStartTime());
        assertEquals(AppointmentStatus.CONFIRMED, confirmResponse.getStatus());

        // 3. Verify hold is removed
        assertFalse(slotHoldRepository.findById(holdResponse.getHoldId()).isPresent());

        // 4. Attempt to place hold or book the same slot again -> Should fail
        assertThrows(IllegalStateException.class, () -> {
            appointmentService.placeSlotHold(holdRequest, patient.getEmail());
        });
    }

    @Test
    void testCompleteAppointmentAndPrescriptionScheduler() {
        LocalDate date = LocalDate.now().plusDays(2);
        LocalTime time = LocalTime.of(11, 0);

        // 1. Create a confirmed appointment
        SlotHoldRequest holdRequest = SlotHoldRequest.builder()
                .doctorId(doctorProfile.getId())
                .slotDate(date)
                .startTime(time)
                .build();
        SlotHoldResponse holdResponse = appointmentService.placeSlotHold(holdRequest, patient.getEmail());

        AppointmentConfirmRequest confirmRequest = AppointmentConfirmRequest.builder()
                .slotHoldId(holdResponse.getHoldId())
                .patientSymptoms("Heavy migraine headache")
                .build();
        AppointmentResponse confirmResponse = appointmentService.confirmAppointment(confirmRequest, patient.getEmail());
        assertNotNull(confirmResponse);

        // 2. Doctor completes the appointment with notes and a prescription
        PrescriptionDto prescriptionDto = PrescriptionDto.builder()
                .medicationName("Ibuprofen 400mg")
                .dosage("1 tablet")
                .frequency(Frequency.TWICE_DAILY)
                .durationDays(5)
                .build();

        AppointmentCompleteRequest completeRequest = AppointmentCompleteRequest.builder()
                .postVisitNotes("Patient diagnosed with acute migraine. Prescribed ibuprofen and rest.")
                .prescriptions(List.of(prescriptionDto))
                .build();

        AppointmentResponse completeResponse = appointmentService.completeAppointment(
                confirmResponse.getAppointmentId(), completeRequest, doctorUser.getEmail()
        );

        assertNotNull(completeResponse);
        // Verify LLM fallback to notes
        assertEquals("Patient diagnosed with acute migraine. Prescribed ibuprofen and rest.", 
                appointmentRepository.findById(confirmResponse.getAppointmentId()).get().getPostVisitSummary());

        // Verify prescription saved
        List<Prescription> prescriptions = prescriptionRepository.findByActive(true);
        assertEquals(1, prescriptions.size());
        assertEquals("Ibuprofen 400mg", prescriptions.get(0).getMedicationName());

        // 3. Verify clinical summary email is enqueued
        long countClinical = notificationQueueRepository.findAll().stream()
                .filter(n -> n.getSubject().contains("Clinical Summary Ready"))
                .count();
        assertEquals(1, countClinical);

        // 4. Run prescription scheduler to enqueue medication reminder
        prescriptionScheduler.processActivePrescriptions();

        long countReminders = notificationQueueRepository.findAll().stream()
                .filter(n -> n.getSubject().contains("Medication Reminder"))
                .count();
        assertEquals(1, countReminders);

        // 5. Run prescription scheduler again and verify no duplicate reminder is enqueued today
        prescriptionScheduler.processActivePrescriptions();

        long countRemindersSecondRun = notificationQueueRepository.findAll().stream()
                .filter(n -> n.getSubject().contains("Medication Reminder"))
                .count();
        assertEquals(1, countRemindersSecondRun); // Should remain 1 (no duplicates!)
    }

    @Test
    void testAddDoctorLeaveConflictResolution() {
        LocalDate date = LocalDate.now().plusDays(5);
        LocalTime time = LocalTime.of(14, 0);

        // 1. Create a confirmed appointment
        SlotHoldRequest holdRequest = SlotHoldRequest.builder()
                .doctorId(doctorProfile.getId())
                .slotDate(date)
                .startTime(time)
                .build();
        SlotHoldResponse holdResponse = appointmentService.placeSlotHold(holdRequest, patient.getEmail());

        AppointmentConfirmRequest confirmRequest = AppointmentConfirmRequest.builder()
                .slotHoldId(holdResponse.getHoldId())
                .patientSymptoms("Chronic headache")
                .build();
        AppointmentResponse confirmResponse = appointmentService.confirmAppointment(confirmRequest, patient.getEmail());
        assertNotNull(confirmResponse);
        assertEquals(AppointmentStatus.CONFIRMED, confirmResponse.getStatus());

        // 2. Register doctor leave on the same date (conflict!)
        DoctorLeaveRequest leaveRequest = DoctorLeaveRequest.builder()
                .leaveDate(date)
                .reason("Medical Conference")
                .build();

        doctorService.addDoctorLeave(doctorProfile.getId(), leaveRequest);

        // 3. Verify the appointment was automatically cancelled with CANCELLED_BY_DOCTOR_LEAVE status
        Appointment appointment = appointmentRepository.findById(confirmResponse.getAppointmentId()).orElse(null);
        assertNotNull(appointment);
        assertEquals(AppointmentStatus.CANCELLED_BY_DOCTOR_LEAVE, appointment.getStatus());
        assertNull(appointment.getActiveBookingMarker());

        // 4. Verify leave cancellation notification email is enqueued
        long countCancellationEmails = notificationQueueRepository.findAll().stream()
                .filter(n -> n.getSubject().contains("Appointment Cancelled: Doctor on Leave"))
                .count();
        assertEquals(1, countCancellationEmails);
    }
}
