package com.healthcare.management.repository;

import com.healthcare.management.model.UserOAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserOAuthTokenRepository extends JpaRepository<UserOAuthToken, Long> {
    Optional<UserOAuthToken> findByUserEmail(String email);
}
