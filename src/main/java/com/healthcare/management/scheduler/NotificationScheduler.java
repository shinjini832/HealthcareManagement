package com.healthcare.management.scheduler;

import com.healthcare.management.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 60000)
    public void processNotifications() {
        log.info("Triggered notification queue processing task.");
        try {
            notificationService.processPendingNotifications();
        } catch (Exception e) {
            log.error("Failed executing scheduled notification processing task", e);
        }
    }
}
