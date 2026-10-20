package com.claimflow.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @TempDir
    Path dir;

    LocalStorageService storage;
    final UUID org = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        storage = new LocalStorageService(new StorageProperties(dir.toString(), List.of("application/pdf", "image/png")));
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storesUnderOrganisationNamespaceAndReadsBack() throws Exception {
        String ref = storage.store(org, bytes("hello"), "../../evil.pdf", "application/pdf");
        assertThat(ref).startsWith(org + "/").endsWith(".pdf").doesNotContain("evil");
        assertThat(storage.load(ref).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void rejectsDisallowedContentTypes() {
        assertThatThrownBy(() -> storage.store(org, bytes("x"), "a.exe", "application/x-msdownload"))
                .isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> storage.store(org, bytes("x"), "a", null)).isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void refusesPathTraversalOnLoadAndDelete() {
        assertThatThrownBy(() -> storage.load("../../etc/passwd")).isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> storage.delete("../outside")).isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        String ref = storage.store(org, bytes("x"), "r.png", "image/png");
        storage.delete(ref);
        storage.delete(ref);
        assertThatThrownBy(() -> storage.load(ref)).isInstanceOf(com.claimflow.common.exception.NotFoundException.class);
    }
}
