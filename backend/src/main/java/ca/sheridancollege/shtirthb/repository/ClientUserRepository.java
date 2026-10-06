package ca.sheridancollege.shtirthb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.ClientUser;

public interface ClientUserRepository extends JpaRepository<ClientUser, Long> {

    Optional<ClientUser> findByEmail(String email);

    boolean existsByEmail(String email);
}