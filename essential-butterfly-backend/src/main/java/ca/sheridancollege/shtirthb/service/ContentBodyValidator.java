package ca.sheridancollege.shtirthb.service;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.sheridancollege.shtirthb.exception.AppExceptions;

@Component
public class ContentBodyValidator {

    static final int MAX_FIELDS = 80;
    static final int MAX_FIELD_LENGTH = 1500;
    static final int MAX_SERVICES = 12;
    static final int MAX_SERVICE_TITLE = 80;
    static final int MAX_SERVICE_TEXT = 300;

    private static final Pattern SLOT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,39}");
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    private static final String BAD_SHAPE =
            "The page content is not in the expected format. "
            + "Reload the editor and try again.";

    private final ObjectMapper mapper = new ObjectMapper();

    public void validate(String pageKey, String body) {

        JsonNode root;

        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new AppExceptions.ValidationException(BAD_SHAPE);
        }

        if (root == null || !root.isObject()) {
            throw new AppExceptions.ValidationException(BAD_SHAPE);
        }

        Iterator<Map.Entry<String, JsonNode>> parts = root.fields();

        while (parts.hasNext()) {
            String name = parts.next().getKey();

            if (!"fields".equals(name) && !"services".equals(name)) {
                throw new AppExceptions.ValidationException(BAD_SHAPE);
            }
        }

        JsonNode fields = root.get("fields");

        if (fields == null || !fields.isObject()) {
            throw new AppExceptions.ValidationException(BAD_SHAPE);
        }

        validateFields(fields);

        JsonNode services = root.get("services");

        if (services != null) {

            if (!"services".equals(pageKey)) {
                throw new AppExceptions.ValidationException(BAD_SHAPE);
            }

            validateServices(services);
        }
    }

    private void validateFields(JsonNode fields) {

        if (fields.size() > MAX_FIELDS) {
            throw new AppExceptions.ValidationException(BAD_SHAPE);
        }

        Iterator<Map.Entry<String, JsonNode>> entries = fields.fields();

        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();

            if (!SLOT_KEY.matcher(entry.getKey()).matches()
                    || !entry.getValue().isTextual()) {
                throw new AppExceptions.ValidationException(BAD_SHAPE);
            }

            checkText(entry.getValue().asText(), MAX_FIELD_LENGTH, "A text");
        }
    }

    private void validateServices(JsonNode services) {

        if (!services.isArray() || services.isEmpty()) {
            throw new AppExceptions.ValidationException(
                    "Keep at least one service on the page."
            );
        }

        if (services.size() > MAX_SERVICES) {
            throw new AppExceptions.ValidationException(
                    "The page can show up to " + MAX_SERVICES + " services."
            );
        }

        for (JsonNode service : services) {

            if (!service.isObject()
                    || !service.path("id").isTextual()
                    || !ITEM_ID.matcher(service.path("id").asText()).matches()
                    || !service.path("title").isTextual()
                    || !service.path("text").isTextual()
                    || (service.has("comingSoon")
                            && !service.get("comingSoon").isBoolean())) {
                throw new AppExceptions.ValidationException(BAD_SHAPE);
            }

            String title = service.get("title").asText().trim();
            String text = service.get("text").asText().trim();

            if (title.isEmpty() || text.isEmpty()) {
                throw new AppExceptions.ValidationException(
                        "Required content is missing: every service needs "
                        + "a name and a description."
                );
            }

            checkText(title, MAX_SERVICE_TITLE, "A service name");
            checkText(text, MAX_SERVICE_TEXT, "A service description");
        }
    }

    private void checkText(String value, int max, String what) {

        if (value.length() > max) {
            throw new AppExceptions.ValidationException(
                    what + " must be " + max + " characters or fewer."
            );
        }

        if (value.indexOf('\u0000') >= 0) {
            throw new AppExceptions.ValidationException(
                    "The content contains characters that are not allowed."
            );
        }
    }
}