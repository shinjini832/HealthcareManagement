package com.healthcare.management.repository;

import com.healthcare.management.model.Appointment;
import com.healthcare.management.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByDoctorIdAndAppointmentDateAndStatusIn(
            Long doctorId,
            LocalDate appointmentDate,
            List<AppointmentStatus> statuses
    );

    List<Appointment> findByPatientEmailOrderByAppointmentDateDescStartTimeDesc(String email);

    List<Appointment> findByDoctorUserEmailOrderByAppointmentDateAscStartTimeAsc(String email);

    List<Appointment> findByStatusOrderByAppointmentDateDesc(AppointmentStatus status);
}
