package com.healthcare.management.repository;

import com.healthcare.management.model.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {
    Optional<DoctorProfile> findByUserEmail(String email);

    List<DoctorProfile> findBySpecializationContainingIgnoreCase(String specialization);
}
