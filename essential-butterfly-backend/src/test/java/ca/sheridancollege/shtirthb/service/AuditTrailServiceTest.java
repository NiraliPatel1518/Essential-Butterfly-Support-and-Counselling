package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ca.sheridancollege.shtirthb.dto.AuditLogResponse;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditTrailServiceTest {

    @Mock EntityManager entityManager;
    @Mock AdminUserRepository adminUsers;
    @Mock(answer = Answers.RETURNS_SELF) Query query;

    private AuditTrailService service;

    @BeforeEach
    void setUp() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        service = new AuditTrailService(entityManager, adminUsers);
    }

    private void adminKnown(String email, long id) {
        AdminUser admin = mock(AdminUser.class);
        when(admin.getId()).thenReturn(id);
        when(adminUsers.findByEmail(email)).thenReturn(Optional.of(admin));
    }

    // -------------------------------------------------------------- actAs

    @Test void actAsSetsTheActorToTheAdministratorsId() {
        adminKnown("casey@example.com", 5L);

        service.actAs("casey@example.com");

        verify(entityManager).createNativeQuery("select set_config('app.actor', :actor, true)");
        verify(query).setParameter("actor", "ADMIN:5");
        verify(query).getSingleResult();
    }

    @Test void actAsFallsBackToPlainAdminForAnUnknownEmail() {
        when(adminUsers.findByEmail("who@example.com")).thenReturn(Optional.empty());

        service.actAs("who@example.com");

        verify(query).setParameter("actor", "ADMIN");
    }

    @Test void actAsFallsBackToPlainAdminWithoutAnEmail() {
        service.actAs(null);

        verify(query).setParameter("actor", "ADMIN");
        verifyNoInteractions(adminUsers);
    }

    @Test void actAsClientSetsTheActorToTheClientsId() {
        service.actAsClient(42L);

        verify(query).setParameter("actor", "CLIENT:42");
        verify(query).getSingleResult();
    }

    // -------------------------------------------------- recordSecurityEvent

    @Test void recordsASecurityEventWithTheAdministratorAndReason() {
        adminKnown("casey@example.com", 5L);

        service.recordSecurityEvent("casey@example.com", "SECURITY_UNSAFE_FILE_REJECTED",
                "library_resources", "Wrong file type");

        verify(query).setParameter("action", "SECURITY_UNSAFE_FILE_REJECTED");
        verify(query).setParameter("entityType", "library_resources");
        verify(query).setParameter("actor", "ADMIN:5");
        verify(query).setParameter("reason", "Wrong file type");
        verify(query).executeUpdate();
    }

    @Test void theSecurityEventInsertsIntoTheAuditLog() {
        service.recordSecurityEvent(null, "A", "B", "C");

        verify(entityManager).createNativeQuery(contains("insert into audit_log"));
    }

    @Test void aLongReasonIsCutTo300Characters() {
        service.recordSecurityEvent(null, "A", "B", "x".repeat(500));

        verify(query).setParameter("reason", "x".repeat(300));
    }

    @Test void aLongActionAndEntityTypeAreCutTo60Characters() {
        service.recordSecurityEvent(null, "a".repeat(100), "b".repeat(100), "reason");

        verify(query).setParameter("action", "a".repeat(60));
        verify(query).setParameter("entityType", "b".repeat(60));
    }

    @Test void aMissingReasonIsSavedAsEmptyText() {
        service.recordSecurityEvent(null, "A", "B", null);

        verify(query).setParameter("reason", "");
    }

    @Test void aSecurityEventWithoutAnAdministratorIsRecordedAsAdmin() {
        service.recordSecurityEvent(null, "A", "B", "C");

        verify(query).setParameter("actor", "ADMIN");
    }

    // ------------------------------------------------ recentContentActivity

    @Test void turnsDatabaseRowsIntoResponses() {
        Object[] row = {7, "2026-11-10 14:30:00", "CONTENT_PUBLISHED", "website_content_pages", 3, "ADMIN:5", "{\"page_key\":\"about\"}"};
        when(query.getResultList()).thenReturn(List.<Object[]>of(row));

        List<AuditLogResponse> result = service.recentContentActivity(50);

        assertEquals(1, result.size());
        AuditLogResponse entry = result.get(0);
        assertEquals(7L, entry.id());
        assertEquals("2026-11-10 14:30:00", entry.occurredAt());
        assertEquals("CONTENT_PUBLISHED", entry.action());
        assertEquals("website_content_pages", entry.entityType());
        assertEquals(3L, entry.entityId());
        assertEquals("ADMIN:5", entry.actor());
        assertEquals("{\"page_key\":\"about\"}", entry.details());
    }

    @Test void keepsAMissingItemIdAsNull() {
        Object[] row = {8, "2026-11-10 14:30:00", "SECURITY_UNSAFE_LINK_REJECTED", "library_resources", null, "ADMIN", null};
        when(query.getResultList()).thenReturn(List.<Object[]>of(row));

        AuditLogResponse entry = service.recentContentActivity(50).get(0);

        assertNull(entry.entityId());
        assertNull(entry.details());
    }

    @Test void keepsTheOrderOfTheRows() {
        Object[] first = {2, "t2", "A", "library_resources", 1, "ADMIN", null};
        Object[] second = {1, "t1", "B", "library_resources", 1, "ADMIN", null};
        when(query.getResultList()).thenReturn(List.<Object[]>of(first, second));

        List<AuditLogResponse> result = service.recentContentActivity(50);

        assertEquals(2L, result.get(0).id());
        assertEquals(1L, result.get(1).id());
    }

    @Test void returnsAnEmptyListWhenNothingWasRecorded() {
        when(query.getResultList()).thenReturn(List.of());

        assertTrue(service.recentContentActivity(50).isEmpty());
    }

    @Test void onlyAsksForContentPagesResourcesAndMemoryWallPhotos() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(50);

        verify(entityManager).createNativeQuery(argThat(sql ->
                sql.contains("website_content_pages")
                        && sql.contains("library_resources")
                        && sql.contains("memory_wall_photos")
                        && sql.contains("order by a.occurred_at desc")));
    }

    @Test void passesTheRequestedLimitWhenItIsReasonable() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(25);

        verify(query).setParameter("limit", 25);
    }

    @Test void neverAsksForMoreThan200Rows() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(5000);

        verify(query).setParameter("limit", 200);
    }

    @Test void alwaysAsksForAtLeastOneRow() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(0);
        service.recentContentActivity(-10);

        verify(query, times(2)).setParameter("limit", 1);
    }
}