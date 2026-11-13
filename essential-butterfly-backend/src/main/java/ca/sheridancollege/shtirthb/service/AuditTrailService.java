package ca.sheridancollege.shtirthb.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ca.sheridancollege.shtirthb.dto.AuditLogResponse;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;

@Service
public class AuditTrailService {

    private static final Logger logger =
            LoggerFactory.getLogger(AuditTrailService.class);

    private static final int ACTOR_MAX_LENGTH = 80;
    private static final int ACTION_MAX_LENGTH = 60;
    private static final int REASON_MAX_LENGTH = 300;
    private static final int MAX_ROWS = 200;

    private final EntityManager entityManager;
    private final AdminUserRepository adminUsers;

    public AuditTrailService(
            EntityManager entityManager,
            AdminUserRepository adminUsers) {

        this.entityManager = entityManager;
        this.adminUsers = adminUsers;
    }

    // Must run inside the transaction that changes the content.
    @Transactional(propagation = Propagation.MANDATORY)
    public void actAs(String adminEmail) {

        entityManager
                .createNativeQuery("select set_config('app.actor', :actor, true)")
                .setParameter("actor", actorLabel(adminEmail))
                .getSingleResult();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(
            String adminEmail,
            String action,
            String entityType,
            String reason) {

        entityManager
                .createNativeQuery(
                        "insert into audit_log "
                        + "(action, entity_type, entity_id, actor, details) "
                        + "values (:action, :entityType, null, :actor, "
                        + "jsonb_build_object('reason', cast(:reason as text)))")
                .setParameter("action", limit(action, ACTION_MAX_LENGTH))
                .setParameter("entityType", limit(entityType, ACTION_MAX_LENGTH))
                .setParameter("actor", actorLabel(adminEmail))
                .setParameter("reason", limit(reason, REASON_MAX_LENGTH))
                .executeUpdate();

        logger.warn(
                "Security event recorded | action={} | entityType={}",
                action,
                entityType
        );
    }

    // Recent content-management activity for the administrator dashboard.
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<AuditLogResponse> recentContentActivity(int requestedLimit) {

        int limit = Math.max(1, Math.min(requestedLimit, MAX_ROWS));

        List<Object[]> rows = entityManager
                .createNativeQuery(
                        "select a.id, cast(a.occurred_at as text), a.action, "
                        + "a.entity_type, a.entity_id, a.actor, "
                        + "cast(a.details as text) "
                        + "from audit_log a "
                        + "where a.entity_type in "
                        + "('website_content_pages', 'library_resources') "
                        + "order by a.occurred_at desc, a.id desc "
                        + "limit :limit")
                .setParameter("limit", limit)
                .getResultList();

        return rows.stream()
                .map(row -> new AuditLogResponse(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        row[4] == null ? null : ((Number) row[4]).longValue(),
                        (String) row[5],
                        (String) row[6]
                ))
                .toList();
    }

    // "ADMIN:42" when the administrator is known, otherwise "ADMIN".
    private String actorLabel(String adminEmail) {

        String label = adminEmail == null
                ? "ADMIN"
                : adminUsers.findByEmail(adminEmail)
                        .map(admin -> "ADMIN:" + admin.getId())
                        .orElse("ADMIN");

        return limit(label, ACTOR_MAX_LENGTH);
    }

    private String limit(String value, int maxLength) {

        if (value == null) {
            return "";
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
