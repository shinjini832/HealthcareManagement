package com.healthcare.management.scheduler;

import com.healthcare.management.model.Prescription;
import com.healthcare.management.repository.NotificationQueueRepository;
import com.healthcare.management.repository.PrescriptionRepository;
import com.healthcare.management.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrescriptionScheduler {

    private final PrescriptionRepository prescriptionRepository;
    private final NotificationQueueRepository notificationQueueRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processActivePrescriptions() {
        log.info("Triggered prescription scheduler job.");
        List<Prescription> activePrescriptions = prescriptionRepository.findByActive(true);

        LocalDate today = LocalDate.now();

        for (Prescription prescription : activePrescriptions) {
            LocalDate endDate = prescription.getStartDate().plusDays(prescription.getDurationDays());

            // 1. If prescription has expired, mark it as inactive
            if (today.isAfter(endDate)) {
                prescription.setActive(false);
                prescriptionRepository.save(prescription);
                log.info("Marked prescription ID {} as inactive (expired).", prescription.getId());
                continue;
            }

            // 2. Otherwise, check if a reminder has already been enqueued today for this prescription
            String subject = "Medication Reminder: " + prescription.getMedicationName();
            String recipientEmail = prescription.getAppointment().getPatient().getEmail();
            LocalDateTime startOfToday = today.atStartOfDay();

            boolean reminderExistsToday = notificationQueueRepository
                    .existsByRecipientEmailAndSubjectAndScheduledAtAfter(recipientEmail, subject, startOfToday);

            if (!reminderExistsToday) {
                notificationService.queuePrescriptionReminder(prescription);
            } else {
                log.debug("Prescription ID {} reminder already enqueued today for user: {}.", prescription.getId(), recipientEmail);
            }
        }
    }
}
