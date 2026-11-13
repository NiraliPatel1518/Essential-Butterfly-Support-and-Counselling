package ca.sheridancollege.shtirthb.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ca.sheridancollege.shtirthb.exception.AppExceptions;

/*
 * Unit tests for ContentBodyValidator.
 *
 * The admin edits website pages. The page text is saved as JSON:
 *   { "fields": { "heroTitle": "..." }, "services": [ { ... } ] }
 * The validator checks the shape and the size of that JSON before it is saved.
 * No database is needed.
 */
class ContentBodyValidatorTest {

    private static final String BAD_SHAPE_START = "The page content is not in the expected format";

    private final ContentBodyValidator validator = new ContentBodyValidator();

    // ------------------------------------------------------------- helpers

    private String message(String pageKey, String body) {
        AppExceptions.ValidationException error = assertThrows(
                AppExceptions.ValidationException.class,
                () -> validator.validate(pageKey, body));
        return error.getMessage();
    }

    private void assertBadShape(String pageKey, String body) {
        assertTrue(message(pageKey, body).startsWith(BAD_SHAPE_START),
                "Expected the 'not in the expected format' message");
    }

    private String service(String id, String title, String text) {
        return "{\"id\":\"" + id + "\",\"title\":\"" + title
                + "\",\"text\":\"" + text + "\"}";
    }

    private String servicesBody(String... services) {
        return "{\"fields\":{},\"services\":[" + String.join(",", services) + "]}";
    }

    // --------------------------------------------------------------- fields

    @Test
    void acceptsAPageWithTextFields() {
        assertDoesNotThrow(() -> validator.validate("about",
                "{\"fields\":{\"heroTitle\":\"Hello\",\"intro_text\":\"Welcome\"}}"));
    }

    @Test
    void acceptsAPageWithNoFields() {
        assertDoesNotThrow(() -> validator.validate("about", "{\"fields\":{}}"));
    }

    @Test
    void rejectsTextThatIsNotJson() {
        assertBadShape("about", "this is not json");
    }

    @Test
    void rejectsJsonThatIsNotAnObject() {
        assertBadShape("about", "[1,2,3]");
        assertBadShape("about", "\"just text\"");
    }

    @Test
    void rejectsAnUnknownTopLevelPart() {
        assertBadShape("about", "{\"fields\":{},\"script\":\"x\"}");
    }

    @Test
    void rejectsABodyWithoutFields() {
        assertBadShape("about", "{}");
    }

    @Test
    void rejectsFieldsThatAreNotAnObject() {
        assertBadShape("about", "{\"fields\":[\"a\"]}");
    }

    @Test
    void rejectsMoreThanEightyFields() {
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i <= ContentBodyValidator.MAX_FIELDS; i++) {
            if (i > 0) {
                fields.append(",");
            }
            fields.append("\"slot").append(i).append("\":\"x\"");
        }

        assertBadShape("about", "{\"fields\":{" + fields + "}}");
    }

    @Test
    void acceptsExactlyEightyFields() {
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i < ContentBodyValidator.MAX_FIELDS; i++) {
            if (i > 0) {
                fields.append(",");
            }
            fields.append("\"slot").append(i).append("\":\"x\"");
        }

        assertDoesNotThrow(() -> validator.validate("about",
                "{\"fields\":{" + fields + "}}"));
    }

    @Test
    void rejectsFieldNamesThatAreNotSafe() {
        assertBadShape("about", "{\"fields\":{\"1bad\":\"x\"}}");
        assertBadShape("about", "{\"fields\":{\"has space\":\"x\"}}");
        assertBadShape("about", "{\"fields\":{\"a-b\":\"x\"}}");
    }

    @Test
    void rejectsAFieldNameLongerThanFortyCharacters() {
        String longName = "a".repeat(41);

        assertBadShape("about", "{\"fields\":{\"" + longName + "\":\"x\"}}");
    }

    @Test
    void rejectsFieldValuesThatAreNotText() {
        assertBadShape("about", "{\"fields\":{\"heroTitle\":5}}");
        assertBadShape("about", "{\"fields\":{\"heroTitle\":null}}");
        assertBadShape("about", "{\"fields\":{\"heroTitle\":{\"a\":1}}}");
    }

    @Test
    void acceptsATextOfExactlyTheMaximumLength() {
        String text = "a".repeat(ContentBodyValidator.MAX_FIELD_LENGTH);

        assertDoesNotThrow(() -> validator.validate("about",
                "{\"fields\":{\"intro\":\"" + text + "\"}}"));
    }

    @Test
    void rejectsATextThatIsTooLong() {
        String text = "a".repeat(ContentBodyValidator.MAX_FIELD_LENGTH + 1);

        assertEquals("A text must be 1500 characters or fewer.",
                message("about", "{\"fields\":{\"intro\":\"" + text + "\"}}"));
    }

    @Test
    void rejectsATextWithANullCharacter() {
        // \u0000 inside JSON text becomes a real NUL character after parsing.
        assertEquals("The content contains characters that are not allowed.",
                message("about", "{\"fields\":{\"intro\":\"a\\u0000b\"}}"));
    }

    // ------------------------------------------------------------- services

    @Test
    void acceptsServicesOnTheServicesPage() {
        assertDoesNotThrow(() -> validator.validate("services", servicesBody(
                service("respite", "Respite", "Short breaks for families."),
                service("coaching-1", "Coaching", "One to one coaching."))));
    }

    @Test
    void acceptsAServiceMarkedAsComingSoon() {
        String body = servicesBody(
                "{\"id\":\"dbt\",\"title\":\"DBT\",\"text\":\"Coming later.\",\"comingSoon\":true}");

        assertDoesNotThrow(() -> validator.validate("services", body));
    }

    @Test
    void rejectsServicesOnAnyOtherPage() {
        assertBadShape("about", servicesBody(service("a", "A", "B")));
    }

    @Test
    void rejectsAnEmptyServicesList() {
        assertEquals("Keep at least one service on the page.",
                message("services", servicesBody()));
    }

    @Test
    void rejectsServicesThatAreNotAList() {
        assertEquals("Keep at least one service on the page.",
                message("services", "{\"fields\":{},\"services\":{\"a\":1}}"));
    }

    @Test
    void acceptsTwelveServicesButNotThirteen() {
        String[] twelve = new String[ContentBodyValidator.MAX_SERVICES];
        for (int i = 0; i < twelve.length; i++) {
            twelve[i] = service("s" + i, "Title " + i, "Text " + i);
        }
        assertDoesNotThrow(() -> validator.validate("services", servicesBody(twelve)));

        String[] thirteen = new String[ContentBodyValidator.MAX_SERVICES + 1];
        for (int i = 0; i < thirteen.length; i++) {
            thirteen[i] = service("s" + i, "Title " + i, "Text " + i);
        }
        assertEquals("The page can show up to 12 services.",
                message("services", servicesBody(thirteen)));
    }

    @Test
    void rejectsAServiceWithAnUnsafeId() {
        assertBadShape("services", servicesBody(service("Bad Id", "A", "B")));
        assertBadShape("services", servicesBody(service("-start", "A", "B")));
        assertBadShape("services", servicesBody(service("UPPER", "A", "B")));
    }

    @Test
    void rejectsAServiceWithAMissingOrWrongPart() {
        assertBadShape("services", servicesBody("{\"id\":\"a\",\"text\":\"B\"}"));
        assertBadShape("services", servicesBody("{\"id\":\"a\",\"title\":\"A\"}"));
        assertBadShape("services", servicesBody("{\"title\":\"A\",\"text\":\"B\"}"));
        assertBadShape("services", servicesBody("\"just text\""));
    }

    @Test
    void rejectsComingSoonThatIsNotTrueOrFalse() {
        assertBadShape("services", servicesBody(
                "{\"id\":\"a\",\"title\":\"A\",\"text\":\"B\",\"comingSoon\":\"yes\"}"));
    }

    @Test
    void rejectsAServiceWithABlankNameOrDescription() {
        String expected = "Required content is missing: every service needs a name and a description.";

        assertEquals(expected, message("services", servicesBody(service("a", "   ", "Text"))));
        assertEquals(expected, message("services", servicesBody(service("a", "Title", "  "))));
    }

    @Test
    void rejectsAServiceNameOrDescriptionThatIsTooLong() {
        String longTitle = "t".repeat(ContentBodyValidator.MAX_SERVICE_TITLE + 1);
        String longText = "d".repeat(ContentBodyValidator.MAX_SERVICE_TEXT + 1);

        assertEquals("A service name must be 80 characters or fewer.",
                message("services", servicesBody(service("a", longTitle, "Text"))));
        assertEquals("A service description must be 300 characters or fewer.",
                message("services", servicesBody(service("a", "Title", longText))));
    }

    @Test
    void validatesTheFieldsOfTheServicesPageToo() {
        assertBadShape("services", "{\"fields\":{\"1bad\":\"x\"},\"services\":["
                + service("a", "A", "B") + "]}");
    }
}