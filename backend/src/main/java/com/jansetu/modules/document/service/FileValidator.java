package com.jansetu.modules.document.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Server-side file validation: size limits, an allowlist of formats, magic-byte detection, and consistency of the
 * detected type with the file extension and declared content type. Client-side checks are only a convenience.
 */
@Component
public class FileValidator {

    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    public static final long ABSOLUTE_MAX_BYTES = 10L * 1024 * 1024;
    static final Set<String> SUPPORTED = Set.of("application/pdf", "image/png", "image/jpeg");
    private static final Map<String, Set<String>> EXTENSIONS = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/png", Set.of("png"),
            "image/jpeg", Set.of("jpg", "jpeg"));

    public record ValidatedFile(String safeFilename, String mediaType, byte[] content) {
    }

    public ValidatedFile validate(String originalFilename, String declaredType, byte[] content, String allowedTypesCsv,
                                  Long maxBytes) {
        long limit = Math.min(maxBytes == null ? DEFAULT_MAX_BYTES : maxBytes, ABSOLUTE_MAX_BYTES);
        if (content == null || content.length == 0) {
            throw invalid("The file is empty.");
        }
        if (content.length > limit) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "The file is too large. The maximum size is " + (limit / 1024) + " KB.");
        }
        Set<String> allowed = allowedTypes(allowedTypesCsv);
        String detected = detect(content);
        if (detected == null || !allowed.contains(detected)) {
            throw invalid("Unsupported file format. Allowed: " + String.join(", ", allowed) + ".");
        }
        String filename = sanitize(originalFilename);
        String extension = extension(filename);
        if (!EXTENSIONS.get(detected).contains(extension)) {
            throw invalid("The file extension does not match its content.");
        }
        if (declaredType != null && !declaredType.isBlank()
                && !declaredType.equalsIgnoreCase("application/octet-stream")
                && !declaredType.toLowerCase(Locale.ROOT).startsWith(detected)) {
            throw invalid("The declared content type does not match the file content.");
        }
        return new ValidatedFile(filename, detected, content);
    }

    static String detect(byte[] c) {
        if (startsWith(c, new byte[] {'%', 'P', 'D', 'F', '-'})) {
            return "application/pdf";
        }
        if (startsWith(c, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(c, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "image/jpeg";
        }
        return null;
    }

    static String sanitize(String name) {
        String base = name == null ? "" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isEmpty() || base.startsWith(".")) {
            base = "document" + base;
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static Set<String> allowedTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return SUPPORTED;
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(csv.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(SUPPORTED::contains).forEach(result::add);
        return result.isEmpty() ? SUPPORTED : result;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
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

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
