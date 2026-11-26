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

    /*
     * Checks: A page body with normal text fields is accepted.
     * How:    Unit test, no Spring. validate() is given a body with heroTitle and intro_text. It must
     *   not throw.
     */
    @Test
    void acceptsAPageWithTextFields() {
        assertDoesNotThrow(() -> validator.validate("about",
                "{\"fields\":{\"heroTitle\":\"Hello\",\"intro_text\":\"Welcome\"}}"));
    }

    /*
     * Checks: A page with an empty fields object is accepted.
     * How:    validate() with {"fields":{}} must not throw.
     */
    @Test
    void acceptsAPageWithNoFields() {
        assertDoesNotThrow(() -> validator.validate("about", "{\"fields\":{}}"));
    }

    /*
     * Checks: Text that is not JSON is refused.
     * How:    "this is not json" throws ValidationException with the "not in the expected format"
     *   message.
     */
    @Test
    void rejectsTextThatIsNotJson() {
        assertBadShape("about", "this is not json");
    }

    /*
     * Checks: JSON that is not an object is refused.
     * How:    An array and a plain string are both refused.
     */
    @Test
    void rejectsJsonThatIsNotAnObject() {
        assertBadShape("about", "[1,2,3]");
        assertBadShape("about", "\"just text\"");
    }

    /*
     * Checks: An unknown top-level part is refused.
     * How:    A body with an extra "script" key is refused.
     */
    @Test
    void rejectsAnUnknownTopLevelPart() {
        assertBadShape("about", "{\"fields\":{},\"script\":\"x\"}");
    }

    /*
     * Checks: A body without a fields part is refused.
     * How:    "{}" is refused.
     */
    @Test
    void rejectsABodyWithoutFields() {
        assertBadShape("about", "{}");
    }

    /*
     * Checks: Fields that are not an object are refused.
     * How:    "fields" as an array is refused.
     */
    @Test
    void rejectsFieldsThatAreNotAnObject() {
        assertBadShape("about", "{\"fields\":[\"a\"]}");
    }

    /*
     * Checks: More than 80 fields are refused.
     * How:    We build MAX_FIELDS + 1 fields. The body is refused.
     */
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

    /*
     * Checks: Exactly 80 fields are accepted.
     * How:    We build MAX_FIELDS fields. validate() does not throw.
     */
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

    /*
     * Checks: Unsafe field names are refused.
     * How:    Names that start with a digit, contain a space, or contain a dash are refused.
     */
    @Test
    void rejectsFieldNamesThatAreNotSafe() {
        assertBadShape("about", "{\"fields\":{\"1bad\":\"x\"}}");
        assertBadShape("about", "{\"fields\":{\"has space\":\"x\"}}");
        assertBadShape("about", "{\"fields\":{\"a-b\":\"x\"}}");
    }

    /*
     * Checks: A field name longer than 40 characters is refused.
     * How:    We use a 41-character name.
     */
    @Test
    void rejectsAFieldNameLongerThanFortyCharacters() {
        String longName = "a".repeat(41);

        assertBadShape("about", "{\"fields\":{\"" + longName + "\":\"x\"}}");
    }

    /*
     * Checks: Field values that are not text are refused.
     * How:    A number, null and an object are each refused.
     */
    @Test
    void rejectsFieldValuesThatAreNotText() {
        assertBadShape("about", "{\"fields\":{\"heroTitle\":5}}");
        assertBadShape("about", "{\"fields\":{\"heroTitle\":null}}");
        assertBadShape("about", "{\"fields\":{\"heroTitle\":{\"a\":1}}}");
    }

    /*
     * Checks: A text of exactly the maximum length is accepted.
     * How:    We use MAX_FIELD_LENGTH characters. It does not throw.
     */
    @Test
    void acceptsATextOfExactlyTheMaximumLength() {
        String text = "a".repeat(ContentBodyValidator.MAX_FIELD_LENGTH);

        assertDoesNotThrow(() -> validator.validate("about",
                "{\"fields\":{\"intro\":\"" + text + "\"}}"));
    }

    /*
     * Checks: A text that is too long is refused.
     * How:    MAX_FIELD_LENGTH + 1 characters give the message "A text must be 1500 characters or
     *   fewer."
     */
    @Test
    void rejectsATextThatIsTooLong() {
        String text = "a".repeat(ContentBodyValidator.MAX_FIELD_LENGTH + 1);

        assertEquals("A text must be 1500 characters or fewer.",
                message("about", "{\"fields\":{\"intro\":\"" + text + "\"}}"));
    }

    /*
     * Checks: A text with a null character is refused.
     * How:    A value with \u0000 gives "The content contains characters that are not allowed."
     */
    @Test
    void rejectsATextWithANullCharacter() {
        // \u0000 inside JSON text becomes a real NUL character after parsing.
        assertEquals("The content contains characters that are not allowed.",
                message("about", "{\"fields\":{\"intro\":\"a\\u0000b\"}}"));
    }

    // ------------------------------------------------------------- services

    /*
     * Checks: The services page accepts a list of services.
     * How:    Two valid services are accepted on the page key services.
     */
    @Test
    void acceptsServicesOnTheServicesPage() {
        assertDoesNotThrow(() -> validator.validate("services", servicesBody(
                service("respite", "Respite", "Short breaks for families."),
                service("coaching-1", "Coaching", "One to one coaching."))));
    }

    /*
     * Checks: A service marked comingSoon is accepted.
     * How:    A service with comingSoon true does not throw.
     */
    @Test
    void acceptsAServiceMarkedAsComingSoon() {
        String body = servicesBody(
                "{\"id\":\"dbt\",\"title\":\"DBT\",\"text\":\"Coming later.\",\"comingSoon\":true}");

        assertDoesNotThrow(() -> validator.validate("services", body));
    }

    /*
     * Checks: A services list on any other page is refused.
     * How:    The same body on the page about is refused.
     */
    @Test
    void rejectsServicesOnAnyOtherPage() {
        assertBadShape("about", servicesBody(service("a", "A", "B")));
    }

    /*
     * Checks: An empty services list is refused.
     * How:    The message is "Keep at least one service on the page."
     */
    @Test
    void rejectsAnEmptyServicesList() {
        assertEquals("Keep at least one service on the page.",
                message("services", servicesBody()));
    }

    /*
     * Checks: Services that are not a list are refused.
     * How:    An object instead of a list gives the same "Keep at least one service" message.
     */
    @Test
    void rejectsServicesThatAreNotAList() {
        assertEquals("Keep at least one service on the page.",
                message("services", "{\"fields\":{},\"services\":{\"a\":1}}"));
    }

    /*
     * Checks: Twelve services are accepted, but thirteen are not.
     * How:    12 pass. 13 give "The page can show up to 12 services."
     */
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

    /*
     * Checks: A service id that is not safe is refused.
     * How:    "Bad Id", "-start" and "UPPER" are refused.
     */
    @Test
    void rejectsAServiceWithAnUnsafeId() {
        assertBadShape("services", servicesBody(service("Bad Id", "A", "B")));
        assertBadShape("services", servicesBody(service("-start", "A", "B")));
        assertBadShape("services", servicesBody(service("UPPER", "A", "B")));
    }

    /*
     * Checks: A service with a missing or wrong part is refused.
     * How:    Missing title, missing text, missing id, and a service that is just a string are each
     *   refused.
     */
    @Test
    void rejectsAServiceWithAMissingOrWrongPart() {
        assertBadShape("services", servicesBody("{\"id\":\"a\",\"text\":\"B\"}"));
        assertBadShape("services", servicesBody("{\"id\":\"a\",\"title\":\"A\"}"));
        assertBadShape("services", servicesBody("{\"title\":\"A\",\"text\":\"B\"}"));
        assertBadShape("services", servicesBody("\"just text\""));
    }

    /*
     * Checks: comingSoon must be true or false.
     * How:    The text "yes" is refused.
     */
    @Test
    void rejectsComingSoonThatIsNotTrueOrFalse() {
        assertBadShape("services", servicesBody(
                "{\"id\":\"a\",\"title\":\"A\",\"text\":\"B\",\"comingSoon\":\"yes\"}"));
    }

    /*
     * Checks: A service needs a name and a description.
     * How:    A blank title and a blank text both give "Required content is missing: every service
     *   needs a name and a description."
     */
    @Test
    void rejectsAServiceWithABlankNameOrDescription() {
        String expected = "Required content is missing: every service needs a name and a description.";

        assertEquals(expected, message("services", servicesBody(service("a", "   ", "Text"))));
        assertEquals(expected, message("services", servicesBody(service("a", "Title", "  "))));
    }

    /*
     * Checks: A service name or description that is too long is refused.
     * How:    A name over 80 characters and a description over 300 characters give their own messages.
     */
    @Test
    void rejectsAServiceNameOrDescriptionThatIsTooLong() {
        String longTitle = "t".repeat(ContentBodyValidator.MAX_SERVICE_TITLE + 1);
        String longText = "d".repeat(ContentBodyValidator.MAX_SERVICE_TEXT + 1);

        assertEquals("A service name must be 80 characters or fewer.",
                message("services", servicesBody(service("a", longTitle, "Text"))));
        assertEquals("A service description must be 300 characters or fewer.",
                message("services", servicesBody(service("a", "Title", longText))));
    }

    /*
     * Checks: The services page still checks its normal fields.
     * How:    A bad field name on the services page is refused.
     */
    @Test
    void validatesTheFieldsOfTheServicesPageToo() {
        assertBadShape("services", "{\"fields\":{\"1bad\":\"x\"},\"services\":["
                + service("a", "A", "B") + "]}");
    }
}