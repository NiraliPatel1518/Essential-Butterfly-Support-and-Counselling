package ca.sheridancollege.shtirthb.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.exception.AppExceptions;

/*
 * Checks a photo BEFORE it is stored: the name, the format (by extension and
 * by the file's own first bytes, so a renamed program is not accepted), and
 * the size. The accepted formats and the size limit come from
 * application.properties (app.memory-wall.*).
 */
@Component
public class MemoryWallImageValidator {

    public static final String UNSAFE_FILE_EVENT = "SECURITY_UNSAFE_FILE_REJECTED";

    static final String UNSUPPORTED_MESSAGE =
            "This file format is not supported. Please select a valid image.";

    private static final String AUDIT_ENTITY = "memory_wall_photos";
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private static final Map<String, String> LABELS = Map.of(
            "jpg", "JPEG",
            "jpeg", "JPEG",
            "png", "PNG",
            "webp", "WEBP"
    );

    // Programs and web pages are never accepted, wherever they appear in a name.
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "dll", "sh", "js",
            "jar", "ps1", "vbs", "php", "html", "htm", "svg", "apk", "app",
            "jsp", "asp", "aspx", "py", "pl", "hta", "lnk"
    );

    private static final byte[] PNG_START =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_START =
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private final AppProperties properties;
    private final AuditTrailService auditTrail;

    public MemoryWallImageValidator(
            AppProperties properties,
            AuditTrailService auditTrail) {

        this.properties = properties;
        this.auditTrail = auditTrail;
    }

    // The checked photo, ready to be stored.
    public record ValidatedImage(
            String fileName,
            String extension,
            String contentType,
            byte[] content) {
    }

    public ValidatedImage validate(MultipartFile file, String adminEmail) {

        if (file == null || file.isEmpty()) {
            throw new AppExceptions.ValidationException(
                    "Choose a photo to upload.");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new AppExceptions.ValidationException(
                    "The selected file has no name. Please rename it and try again.");
        }

        if (isUnsafeFileName(originalName)) {
            throw unsafeFile(adminEmail, "Unsafe file name");
        }

        String lowerName = originalName.toLowerCase(Locale.ROOT);
        String[] parts = lowerName.split("\\.", -1);

        for (int i = 1; i < parts.length; i++) {
            if (DANGEROUS_EXTENSIONS.contains(parts[i])) {
                throw unsafeFile(adminEmail,
                        "Executable or web-page extension in file name");
            }
        }

        int lastDot = lowerName.lastIndexOf('.');
        String extension = lastDot > 0 && lastDot < lowerName.length() - 1
                ? lowerName.substring(lastDot + 1)
                : "";

        if (!allowedExtensions().contains(extension)) {
            throw new AppExceptions.ValidationException(UNSUPPORTED_MESSAGE);
        }

        long maxSize = properties.getMemoryWall().getMaxFileSizeBytes();

        if (file.getSize() > maxSize) {
            throw new AppExceptions.PayloadTooLargeException(
                    ResourceUploadValidator.sizeLimitMessage(maxSize));
        }

        byte[] content = readBytes(file);

        if (content.length > maxSize) {
            throw new AppExceptions.PayloadTooLargeException(
                    ResourceUploadValidator.sizeLimitMessage(maxSize));
        }

        if (!contentsMatchType(extension, content)) {
            auditTrail.recordSecurityEvent(
                    adminEmail, UNSAFE_FILE_EVENT, AUDIT_ENTITY,
                    "File contents do not match the ." + extension + " type");

            throw new AppExceptions.ValidationException(UNSUPPORTED_MESSAGE);
        }

        return new ValidatedImage(
                originalName,
                extension.equals("jpeg") ? "jpg" : extension,
                CONTENT_TYPES.get(extension),
                content);
    }

    public List<String> allowedExtensionList() {
        return new ArrayList<>(allowedExtensions());
    }

    // "JPEG, PNG or WEBP", built from the configured formats.
    public String acceptedFormats() {

        Set<String> labels = new LinkedHashSet<>();

        for (String extension : allowedExtensions()) {
            labels.add(LABELS.get(extension));
        }

        List<String> list = new ArrayList<>(labels);

        if (list.size() <= 1) {
            return String.join("", list);
        }

        return String.join(", ", list.subList(0, list.size() - 1))
                + " or " + list.get(list.size() - 1);
    }

    // Only formats this validator knows how to check can be allowed.
    private Set<String> allowedExtensions() {

        Set<String> allowed = new LinkedHashSet<>();

        for (String extension
                : properties.getMemoryWall().getAllowedFileExtensions()) {

            String cleaned = extension.trim().toLowerCase(Locale.ROOT);

            if (CONTENT_TYPES.containsKey(cleaned)) {
                allowed.add(cleaned);
            }
        }

        return allowed;
    }

    private boolean isUnsafeFileName(String name) {

        if (name.length() > MAX_FILE_NAME_LENGTH
                || name.contains("/")
                || name.contains("\\")
                || name.contains("..")) {
            return true;
        }

        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    private byte[] readBytes(MultipartFile file) {

        /*
         * Reading an upload can fail with a checked IOException. It is turned
         * into the project's ServiceUnavailableException, which the global
         * handler answers with a friendly message.
         */
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new AppExceptions.ServiceUnavailableException(
                    "We could not read the uploaded file. Please try again.",
                    exception);
        }
    }

    private boolean contentsMatchType(String extension, byte[] content) {

        return switch (extension) {
            case "png" -> startsWith(content, 0, PNG_START);
            case "jpg", "jpeg" -> startsWith(content, 0, JPEG_START);
            case "webp" -> startsWith(content, 0, RIFF)
                    && startsWith(content, 8, WEBP);
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, int offset, byte[] prefix) {

        if (content.length < offset + prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (content[offset + i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private AppExceptions.ValidationException unsafeFile(
            String adminEmail,
            String reason) {

        auditTrail.recordSecurityEvent(
                adminEmail, UNSAFE_FILE_EVENT, AUDIT_ENTITY, reason);

        return new AppExceptions.ValidationException(UNSUPPORTED_MESSAGE);
    }
}
