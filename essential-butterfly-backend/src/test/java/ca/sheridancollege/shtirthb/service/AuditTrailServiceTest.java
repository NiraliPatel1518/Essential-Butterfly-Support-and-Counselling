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

    /*
     * Checks: actAs sets the database actor to the admin's id.
     * How:    Mock EntityManager and query. For an admin with id 5 we expect the SQL
     *   set_config('app.actor', ...) with the value ADMIN:5. This is how the database triggers
     *   know who made a change.
     */
    @Test void actAsSetsTheActorToTheAdministratorsId() {
        adminKnown("casey@example.com", 5L);

        service.actAs("casey@example.com");

        verify(entityManager).createNativeQuery("select set_config('app.actor', :actor, true)");
        verify(query).setParameter("actor", "ADMIN:5");
        verify(query).getSingleResult();
    }

    /*
     * Checks: An unknown admin email gives the plain actor ADMIN.
     * How:    findByEmail returns empty. The actor parameter is ADMIN.
     */
    @Test void actAsFallsBackToPlainAdminForAnUnknownEmail() {
        when(adminUsers.findByEmail("who@example.com")).thenReturn(Optional.empty());

        service.actAs("who@example.com");

        verify(query).setParameter("actor", "ADMIN");
    }

    /*
     * Checks: No email gives the plain actor ADMIN.
     * How:    actAs(null) sets ADMIN and never looks up the admin.
     */
    @Test void actAsFallsBackToPlainAdminWithoutAnEmail() {
        service.actAs(null);

        verify(query).setParameter("actor", "ADMIN");
        verifyNoInteractions(adminUsers);
    }

    /*
     * Checks: actAsClient sets the actor to the client's id.
     * How:    For client id 42 the actor is CLIENT:42.
     */
    @Test void actAsClientSetsTheActorToTheClientsId() {
        service.actAsClient(42L);

        verify(query).setParameter("actor", "CLIENT:42");
        verify(query).getSingleResult();
    }

    // -------------------------------------------------- recordSecurityEvent

    /*
     * Checks: A security event is recorded with the admin and the reason.
     * How:    We check the query parameters action, entityType, actor (ADMIN:5) and reason, and that
     *   executeUpdate() ran.
     */
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

    /*
     * Checks: A security event is written into the audit_log table.
     * How:    The SQL passed to the EntityManager contains "insert into audit_log".
     */
    @Test void theSecurityEventInsertsIntoTheAuditLog() {
        service.recordSecurityEvent(null, "A", "B", "C");

        verify(entityManager).createNativeQuery(contains("insert into audit_log"));
    }

    /*
     * Checks: A long reason is cut to 300 characters.
     * How:    A 500-character reason is stored as 300 characters.
     */
    @Test void aLongReasonIsCutTo300Characters() {
        service.recordSecurityEvent(null, "A", "B", "x".repeat(500));

        verify(query).setParameter("reason", "x".repeat(300));
    }

    /*
     * Checks: A long action and entity type are cut to 60 characters.
     * How:    100-character values are stored as 60 characters.
     */
    @Test void aLongActionAndEntityTypeAreCutTo60Characters() {
        service.recordSecurityEvent(null, "a".repeat(100), "b".repeat(100), "reason");

        verify(query).setParameter("action", "a".repeat(60));
        verify(query).setParameter("entityType", "b".repeat(60));
    }

    /*
     * Checks: A missing reason is saved as empty text.
     * How:    A null reason becomes "".
     */
    @Test void aMissingReasonIsSavedAsEmptyText() {
        service.recordSecurityEvent(null, "A", "B", null);

        verify(query).setParameter("reason", "");
    }

    /*
     * Checks: A security event with no admin is recorded as ADMIN.
     * How:    A null email gives the actor ADMIN.
     */
    @Test void aSecurityEventWithoutAnAdministratorIsRecordedAsAdmin() {
        service.recordSecurityEvent(null, "A", "B", "C");

        verify(query).setParameter("actor", "ADMIN");
    }

    // ------------------------------------------------ recentContentActivity

    /*
     * Checks: Database rows are turned into response objects.
     * How:    We give the service one row (as an Object array). Each field (id, time, action, entity
     *   type, entity id, actor, details) is checked.
     */
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

    /*
     * Checks: A missing item id stays null.
     * How:    A row with a null id and null details gives null in the response.
     */
    @Test void keepsAMissingItemIdAsNull() {
        Object[] row = {8, "2026-11-10 14:30:00", "SECURITY_UNSAFE_LINK_REJECTED", "library_resources", null, "ADMIN", null};
        when(query.getResultList()).thenReturn(List.<Object[]>of(row));

        AuditLogResponse entry = service.recentContentActivity(50).get(0);

        assertNull(entry.entityId());
        assertNull(entry.details());
    }

    /*
     * Checks: The order of the rows is kept.
     * How:    Two rows go in, and come out in the same order.
     */
    @Test void keepsTheOrderOfTheRows() {
        Object[] first = {2, "t2", "A", "library_resources", 1, "ADMIN", null};
        Object[] second = {1, "t1", "B", "library_resources", 1, "ADMIN", null};
        when(query.getResultList()).thenReturn(List.<Object[]>of(first, second));

        List<AuditLogResponse> result = service.recentContentActivity(50);

        assertEquals(2L, result.get(0).id());
        assertEquals(1L, result.get(1).id());
    }

    /*
     * Checks: An empty result gives an empty list.
     * How:    The query returns no rows.
     */
    @Test void returnsAnEmptyListWhenNothingWasRecorded() {
        when(query.getResultList()).thenReturn(List.of());

        assertTrue(service.recentContentActivity(50).isEmpty());
    }

    /*
     * Checks: The query only covers content pages, resources and Memory Wall photos, newest first.
     * How:    The SQL must contain the three table names and "order by a.occurred_at desc".
     */
    @Test void onlyAsksForContentPagesResourcesAndMemoryWallPhotos() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(50);

        verify(entityManager).createNativeQuery(argThat(sql ->
                sql.contains("website_content_pages")
                        && sql.contains("library_resources")
                        && sql.contains("memory_wall_photos")
                        && sql.contains("order by a.occurred_at desc")));
    }

    /*
     * Checks: A reasonable limit is passed on.
     * How:    A limit of 25 is used as 25.
     */
    @Test void passesTheRequestedLimitWhenItIsReasonable() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(25);

        verify(query).setParameter("limit", 25);
    }

    /*
     * Checks: The limit never goes above 200.
     * How:    A request for 5000 uses 200.
     */
    @Test void neverAsksForMoreThan200Rows() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(5000);

        verify(query).setParameter("limit", 200);
    }

    /*
     * Checks: The limit never goes below 1.
     * How:    0 and -10 both use 1.
     */
    @Test void alwaysAsksForAtLeastOneRow() {
        when(query.getResultList()).thenReturn(List.of());

        service.recentContentActivity(0);
        service.recentContentActivity(-10);

        verify(query, times(2)).setParameter("limit", 1);
    }
}