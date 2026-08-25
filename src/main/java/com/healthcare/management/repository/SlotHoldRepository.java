package com.healthcare.management.repository;

import com.healthcare.management.model.SlotHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SlotHold s WHERE s.doctor.id = :doctorId AND s.slotDate = :slotDate AND s.startTime = :startTime AND s.expiresAt > :now")
    List<SlotHold> findActiveHoldsForUpdate(
            @Param("doctorId") Long doctorId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime,
            @Param("now") LocalDateTime now
    );
}
