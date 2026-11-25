package com.clinic.storage;

import com.clinic.config.AppProperties;
import com.clinic.dto.ContentDtos.UploadResponse;
import com.clinic.exception.BusinessException;
import com.clinic.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Validates uploaded images by size and by their actual content (magic bytes), not the client-supplied name or
 * content type, then hands them to the configured {@link FileStorage}. SVG is rejected (it can carry scripts).
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final FileStorage storage;
    private final AppProperties properties;
    private final AuditService audit;

    private enum ImageType {
        JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp"), GIF("gif", "image/gif");

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    @Transactional
    public UploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("file", "Please choose an image to upload");
        }
        long max = properties.storage().maxFileSizeBytes();
        if (file.getSize() > max) {
            throw new BusinessException("file", "Image is too large. Maximum size is " + (max / (1024 * 1024)) + " MB");
        }
        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException("file", "Could not read the uploaded file");
        }
        ImageType type = detect(bytes);
        if (type == null) {
            throw new BusinessException("file", "Only JPEG, PNG, WebP or GIF images are allowed");
        }
        FileStorage.StoredFile stored = storage.store(new ByteArrayInputStream(bytes), type.extension, type.contentType, bytes.length);
        audit.record("UPLOAD", "Image", stored.fileName(), type.contentType + ", " + bytes.length + " bytes");
        return new UploadResponse(stored.url(), stored.fileName(), stored.contentType(), stored.size());
    }

    static ImageType detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ImageType.PNG;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return ImageType.WEBP;
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return ImageType.GIF;
        }
        return null;
    }
}
