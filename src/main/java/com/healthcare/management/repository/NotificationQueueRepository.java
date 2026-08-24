package com.healthcare.management.repository;

import com.healthcare.management.model.NotificationQueue;
import com.healthcare.management.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Long> {
    List<NotificationQueue> findByStatusInAndRetryCountLessThanAndScheduledAtLessThanEqual(
            List<NotificationStatus> statuses,
            int maxRetryCount,
            LocalDateTime now
    );

    boolean existsByRecipientEmailAndSubjectAndScheduledAtAfter(
            String recipientEmail,
            String subject,
            LocalDateTime dateTime
    );
}
