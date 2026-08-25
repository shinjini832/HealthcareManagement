package com.healthcare.management.repository;

import com.healthcare.management.model.SlotHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface SlotHoldRepository extends JpaRepository<SlotHold, Long> {
    List<SlotHold> findByDoctorIdAndSlotDateAndExpiresAtAfter(
            Long doctorId,
            LocalDate slotDate,
            LocalDateTime now
    );

    // Native query used instead of @Lock(PESSIMISTIC_WRITE) because Hibernate 7
    // generates "FOR UPDATE OF alias" syntax which is not supported by TiDB/MySQL.
    // Standard "FOR UPDATE" achieves the same row-level pessimistic lock.
    @Query(value = "SELECT * FROM slot_holds WHERE doctor_id = :doctorId AND slot_date = :slotDate AND start_time = :startTime AND expires_at > :now FOR UPDATE",
           nativeQuery = true)
    List<SlotHold> findActiveHoldsForUpdate(
            @Param("doctorId") Long doctorId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("now") LocalDateTime now
    );
}
