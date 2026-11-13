package ca.sheridancollege.shtirthb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.sheridancollege.shtirthb.model.AdminUser;

public interface AdminUserRepository
        extends JpaRepository<AdminUser, Long> {

    // Finds the administrator whose SIGN-IN (primary) address this is.
    @Query("""
            select a
              from AdminUser a
              join a.emails e
             where e.primaryEmail = true
               and e.email = lower(:email)
            """)
    Optional<AdminUser> findByEmail(@Param("email") String email);

    /*
     * True if any administrator holds this address, as the sign-in
     * address OR as a pending (not yet verified) change.
     */
    @Query("""
            select case when count(e) > 0 then true else false end
              from AccountEmail e
             where e.adminUser is not null
               and e.email = lower(:email)
            """)
    boolean existsByEmail(@Param("email") String email);
}
