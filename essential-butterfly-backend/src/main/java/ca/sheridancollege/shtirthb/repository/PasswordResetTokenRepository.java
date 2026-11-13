package ca.sheridancollege.shtirthb.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.model.ClientUser;
import ca.sheridancollege.shtirthb.model.PasswordResetToken;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    // Tokens are looked up by the SHA-256 hash of the secret in the link.
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /*
     * Revoke the account's unused token(s) before issuing a new one.
     * The database allows only one unused token per account.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :now
             where t.adminUser = :admin
               and t.usedAt is null
            """)
    int revokeActiveForAdmin(
            @Param("admin") AdminUser admin,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :now
             where t.clientUser = :client
               and t.usedAt is null
            """)
    int revokeActiveForClient(
            @Param("client") ClientUser client,
            @Param("now") LocalDateTime now);
}
