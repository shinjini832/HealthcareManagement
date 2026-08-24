package com.healthcare.management.service;

import com.healthcare.management.model.Appointment;
import com.healthcare.management.model.Prescription;
import com.healthcare.management.model.NotificationQueue;
import com.healthcare.management.model.NotificationStatus;
import com.healthcare.management.model.User;
import com.healthcare.management.repository.NotificationQueueRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationQueueRepository notificationQueueRepository;
    private final JavaMailSender mailSender;

    @Transactional
    public void queueWelcomeEmail(User user) {
        String subject = "Welcome to Healthcare Management System!";
        String body = "<h1>Welcome, " + user.getFullName() + "!</h1>" +
                "<p>Thank you for registering. Your account (" + user.getEmail() + ") is now active.</p>";

        NotificationQueue notification = NotificationQueue.builder()
                .recipientEmail(user.getEmail())
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledAt(LocalDateTime.now())
                .build();

        notificationQueueRepository.save(notification);
        log.info("Queued welcome email for user: {}", user.getEmail());
    }

    @Transactional
    public void queueAppointmentConfirmation(Appointment appointment) {
        String patientEmail = appointment.getPatient().getEmail();
        String subject = "Appointment Confirmation - Dr. " + appointment.getDoctor().getUser().getFullName();
        String body = "<h1>Appointment Confirmed!</h1>" +
                "<p>Dear " + appointment.getPatient().getFullName() + ",</p>" +
                "<p>Your appointment with <strong>Dr. " + appointment.getDoctor().getUser().getFullName() + "</strong> has been confirmed.</p>" +
                "<p><strong>Date:</strong> " + appointment.getAppointmentDate() + "</p>" +
                "<p><strong>Time:</strong> " + appointment.getStartTime() + " - " + appointment.getEndTime() + "</p>" +
                "<p><strong>Urgency Level:</strong> " + appointment.getUrgencyLevel() + "</p>" +
                "<p><strong>Symptoms Described:</strong> " + appointment.getPatientSymptoms() + "</p>";

        NotificationQueue notification = NotificationQueue.builder()
                .recipientEmail(patientEmail)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledAt(LocalDateTime.now())
                .build();

        notificationQueueRepository.save(notification);
        log.info("Queued appointment confirmation email for patient: {}", patientEmail);
    }

    public void processPendingNotifications() {
        List<NotificationStatus> eligibleStatuses = List.of(NotificationStatus.PENDING, NotificationStatus.FAILED);
        LocalDateTime now = LocalDateTime.now();

        List<NotificationQueue> pendingItems = notificationQueueRepository
                .findByStatusInAndRetryCountLessThanAndScheduledAtLessThanEqual(eligibleStatuses, 3, now);

        if (pendingItems.isEmpty()) {
            return;
        }

        log.info("Found {} pending or failed notification items to process.", pendingItems.size());

        for (NotificationQueue item : pendingItems) {
            item.setRetryCount(item.getRetryCount() + 1);
            try {
                sendEmail(item.getRecipientEmail(), item.getSubject(), item.getBody());
                item.setStatus(NotificationStatus.SENT);
                item.setSentAt(LocalDateTime.now());
                item.setErrorMessage(null);
                log.info("Successfully sent notification to: {}", item.getRecipientEmail());
            } catch (Exception e) {
                log.error("Failed to send notification to: {}, attempt: {}", item.getRecipientEmail(), item.getRetryCount(), e);
                item.setStatus(NotificationStatus.FAILED);
                item.setErrorMessage(e.getMessage());
            }
            notificationQueueRepository.save(item);
        }
    }

    @Transactional
    public void queuePrescriptionReminder(Prescription prescription) {
        String patientEmail = prescription.getAppointment().getPatient().getEmail();
        String medicationName = prescription.getMedicationName();
        String subject = "Medication Reminder: " + medicationName;
        String body = "<h1>Prescription Medication Reminder</h1>" +
                "<p>Dear " + prescription.getAppointment().getPatient().getFullName() + ",</p>" +
                "<p>This is your daily reminder to take your medication: <strong>" + medicationName + "</strong> (" + prescription.getDosage() + ").</p>" +
                "<p><strong>Instructions / Frequency:</strong> " + prescription.getFrequency() + "</p>" +
                "<p><strong>Prescription Start Date:</strong> " + prescription.getStartDate() + "</p>" +
                "<p><strong>Duration:</strong> " + prescription.getDurationDays() + " days</p>";

        NotificationQueue notification = NotificationQueue.builder()
                .recipientEmail(patientEmail)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledAt(LocalDateTime.now())
                .build();

        notificationQueueRepository.save(notification);
        log.info("Queued medication reminder email for patient: {}, medication: {}", patientEmail, medicationName);
    }

    @Transactional
    public void queuePostVisitSummary(Appointment appointment) {
        String patientEmail = appointment.getPatient().getEmail();
        String subject = "Clinical Summary Ready - Dr. " + appointment.getDoctor().getUser().getFullName();
        String body = "<h1>Visit Summary & Prescription</h1>" +
                "<p>Dear " + appointment.getPatient().getFullName() + ",</p>" +
                "<p>Your visit summary for your appointment with Dr. " + appointment.getDoctor().getUser().getFullName() + " is now available.</p>" +
                "<h3>Doctor's Summary:</h3>" +
                "<p>" + (appointment.getPostVisitSummary() != null ? appointment.getPostVisitSummary() : "No summary generated.") + "</p>";

        NotificationQueue notification = NotificationQueue.builder()
                .recipientEmail(patientEmail)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledAt(LocalDateTime.now())
                .build();

        notificationQueueRepository.save(notification);
        log.info("Queued clinical summary email for patient: {}", patientEmail);
    }

    @Transactional
    public void queueLeaveCancellationEmail(Appointment appointment) {
        String patientEmail = appointment.getPatient().getEmail();
        String doctorName = appointment.getDoctor().getUser().getFullName();
        String subject = "Appointment Cancelled: Doctor on Leave";
        String body = "<h1>Appointment Cancellation Notice</h1>" +
                "<p>Dear " + appointment.getPatient().getFullName() + ",</p>" +
                "<p>We regret to inform you that your appointment with <strong>Dr. " + doctorName + "</strong> scheduled for " +
                "<strong>" + appointment.getAppointmentDate() + "</strong> at <strong>" + appointment.getStartTime() + "</strong> " +
                "has been cancelled because the doctor is on leave on that day.</p>" +
                "<p>You are welcome to log in to the portal and schedule a new appointment at your convenience.</p>";

        NotificationQueue notification = NotificationQueue.builder()
                .recipientEmail(patientEmail)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledAt(LocalDateTime.now())
                .build();

        notificationQueueRepository.save(notification);
        log.info("Queued leave cancellation email for patient: {}", patientEmail);
    }

    private void sendEmail(String to, String subject, String body) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        mailSender.send(message);
    }
}
