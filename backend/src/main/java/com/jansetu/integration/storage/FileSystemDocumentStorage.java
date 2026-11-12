package com.jansetu.integration.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores document bytes in a private directory outside any web root. Used for local development and tests; an
 * S3/MinIO adapter implementing the same port is planned before any shared deployment (see docs).
 */
@Component
public class FileSystemDocumentStorage implements DocumentStoragePort {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-f0-9]{2}/[a-f0-9-]{36}$");
    private final Path root;

    public FileSystemDocumentStorage(@Value("${jansetu.storage.local-dir:./storage/documents}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create document storage directory", e);
        }
    }

    @Override
    public void put(String key, byte[] content) {
        Path path = resolve(key);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Storage write failed", e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Storage read failed", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Storage delete failed", e);
        }
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
