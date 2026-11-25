package com.clinic.storage;

import com.clinic.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryFileStorageTest {

    private static AppProperties props(AppProperties.Storage.Cloudinary cloudinary) {
        return new AppProperties("Asia/Kolkata",
                new AppProperties.Cors(List.of()),
                new AppProperties.Jwt("x".repeat(32), 15, 7, "test"),
                new AppProperties.Cookie(false, "Strict"),
                new AppProperties.AdminBootstrap(null, null, null),
                new AppProperties.RateLimit(10, 5, 30),
                new AppProperties.Storage("cloudinary", "./uploads", 5_242_880, cloudinary));
    }

    @Test
    void refusesToStartWithoutCloudinaryUrl() {
        assertThatThrownBy(() -> new CloudinaryFileStorage(props(null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CLOUDINARY_URL");
        assertThatThrownBy(() -> new CloudinaryFileStorage(props(new AppProperties.Storage.Cloudinary("", "f"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CloudinaryFileStorage(props(new AppProperties.Storage.Cloudinary("https://wrong", "f"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsWithValidUrlAndNeverServesFilesLocally() {
        CloudinaryFileStorage storage = new CloudinaryFileStorage(
                props(new AppProperties.Storage.Cloudinary("cloudinary://key:secret@demo", "clinic")));
        assertThat(storage.load("anything.png")).isEmpty();
    }
}
