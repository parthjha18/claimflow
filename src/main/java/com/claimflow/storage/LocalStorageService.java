package com.claimflow.storage;

import com.claimflow.common.exception.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "claimflow.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", ".pdf", "image/png", ".png", "image/jpeg", ".jpg");

    private final Path root;
    private final StorageProperties props;

    public LocalStorageService(StorageProperties props) throws IOException {
        this.props = props;
        this.root = Path.of(props.localRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(UUID organizationId, InputStream content, String originalFilename, String contentType)
            throws IOException {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!props.allowedContentTypes().contains(type)) {
            throw new InvalidUploadException("Unsupported file type; allowed: " + props.allowedContentTypes());
        }
        // The file name is generated, never taken from the client, so it cannot escape the directory.
        String ref = organizationId + "/" + UUID.randomUUID() + EXTENSIONS.getOrDefault(type, ".bin");
        Path target = resolve(ref);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return ref;
    }

    @Override
    public Resource load(String ref) {
        Path file = resolve(ref);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("Receipt file");
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String ref) {
        try {
            Files.deleteIfExists(resolve(ref));
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete " + ref, e);
        }
    }

    private Path resolve(String ref) {
        Path p = root.resolve(ref).normalize();
        if (!p.startsWith(root)) {
            throw new InvalidUploadException("Invalid file reference");
        }
        return p;
    }
}
