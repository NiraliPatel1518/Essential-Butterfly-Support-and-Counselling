package ca.sheridancollege.shtirthb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.AdminUser;

public interface AdminUserRepository
        extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByEmail(String email);

    boolean existsByEmail(String email);
}