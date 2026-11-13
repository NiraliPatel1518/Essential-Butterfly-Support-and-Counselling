package ca.sheridancollege.shtirthb.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.dto.UploadRulesResponse;
import ca.sheridancollege.shtirthb.exception.AppExceptions;

@Component
public class ResourceUploadValidator {

    public static final String UNSAFE_FILE_EVENT = "SECURITY_UNSAFE_FILE_REJECTED";
    public static final String UNSAFE_LINK_EVENT = "SECURITY_UNSAFE_LINK_REJECTED";

    static final String UNSAFE_FILE_MESSAGE =
            "This file was rejected because its name, type or contents are not safe. "
            + "Nothing has been published.";

    static final String UNSAFE_LINK_MESSAGE =
            "This link was rejected because its address is not safe. "
            + "Nothing has been published.";

    private static final String AUDIT_ENTITY = "library_resources";
    private static final int MAX_LINK_LENGTH = 2000;
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg"
    );

    private static final Map<String, String> LABELS = Map.of(
            "pdf", "PDF",
            "docx", "DOCX",
            "png", "PNG",
            "jpg", "JPEG",
            "jpeg", "JPEG"
    );

    // Programs and web pages are never accepted, wherever they appear in a name.
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "dll", "sh", "js",
            "jar", "ps1", "vbs", "php", "html", "htm", "svg", "apk", "app",
            "jsp", "asp", "aspx", "py", "pl", "hta", "lnk"
    );

    private static final Set<String> DANGEROUS_SCHEMES = Set.of(
            "javascript", "data", "vbscript", "file", "blob"
    );

    private static final Pattern IPV4_ADDRESS =
            Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final byte[] PDF_START = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_START =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_START =
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ZIP_START = {'P', 'K', 0x03, 0x04};

    private final AppProperties properties;
    private final AuditTrailService auditTrail;

    public ResourceUploadValidator(
            AppProperties properties,
            AuditTrailService auditTrail) {

        this.properties = properties;
        this.auditTrail = auditTrail;
    }

    /* The checked file, ready to be saved. */
    public record ValidatedUpload(
            String displayName,
            String contentType,
            byte[] content,
            boolean image) {
    }

    public ValidatedUpload validateFile(
            MultipartFile file,
            String adminEmail) {

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new AppExceptions.ValidationException(
                    "The selected file has no name. Please rename it and try again."
            );
        }

        if (isUnsafeFileName(originalName)) {
            throw unsafeFile(adminEmail, "Unsafe file name");
        }

        String lowerName = originalName.toLowerCase(Locale.ROOT);
        String[] parts = lowerName.split("\\.", -1);

        for (int i = 1; i < parts.length; i++) {
            if (DANGEROUS_EXTENSIONS.contains(parts[i])) {
                throw unsafeFile(adminEmail, "Executable or web-page extension in file name");
            }
        }

        int lastDot = lowerName.lastIndexOf('.');
        String extension = lastDot > 0 && lastDot < lowerName.length() - 1
                ? lowerName.substring(lastDot + 1)
                : "";

        if (!allowedExtensions().contains(extension)) {
            throw new AppExceptions.ValidationException(unsupportedTypeMessage());
        }

        long maxSize = properties.getContent().getMaxFileSizeBytes();

        if (file.getSize() > maxSize) {
            throw new AppExceptions.PayloadTooLargeException(
                    sizeLimitMessage(maxSize)
            );
        }

        byte[] content = readBytes(file);

        if (content.length == 0) {
            throw new AppExceptions.ValidationException(
                    "The selected file is empty. Please choose a different file."
            );
        }

        if (content.length > maxSize) {
            throw new AppExceptions.PayloadTooLargeException(
                    sizeLimitMessage(maxSize)
            );
        }

        if (!contentsMatchType(extension, content)) {
            throw unsafeFile(adminEmail, "File contents do not match the ." + extension + " type");
        }

        return new ValidatedUpload(
                originalName,
                CONTENT_TYPES.get(extension),
                content,
                CONTENT_TYPES.get(extension).startsWith("image/")
        );
    }

    /* Returns the trimmed link when it is safe to publish. */
    public String validateLink(String rawLink, String adminEmail) {

        String link = rawLink.trim();

        if (link.length() > MAX_LINK_LENGTH) {
            throw new AppExceptions.ValidationException(
                    "The link is too long. Please use a shorter web address."
            );
        }

        for (int i = 0; i < link.length(); i++) {
            if (Character.isISOControl(link.charAt(i))) {
                throw unsafeLink(adminEmail, "Link contains control characters");
            }
        }

        /*
         * URI reports a badly formed address with a checked exception. It is
         * turned into the same friendly message the administrator gets for any
         * other invalid link.
         */
        URI uri;

        try {
            uri = new URI(link);
        } catch (URISyntaxException exception) {
            throw new AppExceptions.ValidationException(invalidLinkMessage());
        }

        String scheme = uri.getScheme() == null
                ? null
                : uri.getScheme().toLowerCase(Locale.ROOT);

        if (scheme == null) {
            throw new AppExceptions.ValidationException(invalidLinkMessage());
        }

        if (DANGEROUS_SCHEMES.contains(scheme)) {
            throw unsafeLink(adminEmail, "Blocked link type: " + scheme);
        }

        if (!allowedSchemes().contains(scheme)) {
            throw new AppExceptions.ValidationException(invalidLinkMessage());
        }

        if (uri.getRawUserInfo() != null) {
            throw unsafeLink(adminEmail, "Link contains embedded credentials");
        }

        String host = uri.getHost() == null
                ? null
                : uri.getHost().toLowerCase(Locale.ROOT);

        if (host == null || host.isBlank()) {
            throw new AppExceptions.ValidationException(invalidLinkMessage());
        }

        if (isUnsafeHost(host)) {
            throw unsafeLink(adminEmail, "Link points to a local, private or numeric address");
        }

        return link;
    }

    public UploadRulesResponse rules() {

        long maxSize = properties.getContent().getMaxFileSizeBytes();

        return new UploadRulesResponse(
                maxSize,
                formatSize(maxSize),
                new ArrayList<>(allowedExtensions()),
                acceptedFormats()
        );
    }

    public String unsupportedTypeMessage() {
        return "This file type is not supported. Please upload a "
                + acceptedFormats() + " file.";
    }

    public static String sizeLimitMessage(long maxSizeBytes) {
        return "This file is too large. The maximum size is "
                + formatSize(maxSizeBytes) + ".";
    }

    // "PDF, DOCX, PNG or JPEG", built from the configured formats.
    String acceptedFormats() {

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

    static String formatSize(long bytes) {

        if (bytes < 1024L * 1024L) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }

        double megabytes = bytes / (1024.0 * 1024.0);

        return megabytes == Math.rint(megabytes)
                ? String.format(Locale.ROOT, "%.0f MB", megabytes)
                : String.format(Locale.ROOT, "%.1f MB", megabytes);
    }

    // Only formats this validator knows how to check can be allowed.
    private Set<String> allowedExtensions() {

        Set<String> allowed = new LinkedHashSet<>();

        for (String extension : properties.getContent().getAllowedFileExtensions()) {

            String cleaned = extension.trim().toLowerCase(Locale.ROOT);

            if (CONTENT_TYPES.containsKey(cleaned)) {
                allowed.add(cleaned);
            }
        }

        return allowed;
    }

    private Set<String> allowedSchemes() {

        Set<String> allowed = new LinkedHashSet<>();

        for (String scheme : properties.getContent().getAllowedLinkSchemes()) {
            allowed.add(scheme.trim().toLowerCase(Locale.ROOT));
        }

        return allowed;
    }

    private String invalidLinkMessage() {
        return "Enter a valid web address that starts with "
                + String.join(" or ", allowedSchemes().stream()
                        .map(scheme -> scheme + "://").toList())
                + ", for example https://example.org/guide.";
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

    private boolean isUnsafeHost(String host) {

        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".localdomain")
                || host.startsWith("[")
                || IPV4_ADDRESS.matcher(host).matches()
                || !host.contains(".");
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
                    exception
            );
        }
    }

    private boolean contentsMatchType(String extension, byte[] content) {

        return switch (extension) {
            case "pdf" -> startsWith(content, PDF_START)
                    && !contains(content, "/Launch")
                    && !contains(content, "/JavaScript");
            case "png" -> startsWith(content, PNG_START);
            case "jpg", "jpeg" -> startsWith(content, JPEG_START);
            case "docx" -> startsWith(content, ZIP_START)
                    && contains(content, "[Content_Types].xml")
                    && contains(content, "word/");
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, byte[] prefix) {

        if (content.length < prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean contains(byte[] content, String text) {

        byte[] needle = text.getBytes(StandardCharsets.US_ASCII);

        for (int i = 0; i <= content.length - needle.length; i++) {

            int j = 0;

            while (j < needle.length && content[i + j] == needle[j]) {
                j++;
            }

            if (j == needle.length) {
                return true;
            }
        }

        return false;
    }

    private AppExceptions.ValidationException unsafeFile(
            String adminEmail,
            String reason) {

        auditTrail.recordSecurityEvent(
                adminEmail, UNSAFE_FILE_EVENT, AUDIT_ENTITY, reason);

        return new AppExceptions.ValidationException(UNSAFE_FILE_MESSAGE);
    }

    private AppExceptions.ValidationException unsafeLink(
            String adminEmail,
            String reason) {

        auditTrail.recordSecurityEvent(
                adminEmail, UNSAFE_LINK_EVENT, AUDIT_ENTITY, reason);

        return new AppExceptions.ValidationException(UNSAFE_LINK_MESSAGE);
    }
}
