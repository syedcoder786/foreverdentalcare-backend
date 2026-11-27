package com.clinic.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryFileStorageTest {

    @Test
    void refusesIncompleteSettings() {
        assertThatThrownBy(() -> new CloudinaryFileStorage(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CloudinaryFileStorage(new CloudinarySettings("demo", "123", " ", "f")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("API secret");
    }

    @Test
    void readsCloudinaryUrl() {
        CloudinarySettings s = CloudinarySettings.fromUrl(" cloudinary://123456789012345:abc-DEF_9@my-cloud ", "/clinic/photos/");
        assertThat(s.cloudName()).isEqualTo("my-cloud");
        assertThat(s.apiKey()).isEqualTo("123456789012345");
        assertThat(s.apiSecret()).isEqualTo("abc-DEF_9");
        assertThat(s.folder()).isEqualTo("clinic/photos");
        assertThat(s.maskedUrl()).isEqualTo("cloudinary://123456789012345:********@my-cloud").doesNotContain("abc-DEF_9");
        assertThat(s.toString()).doesNotContain("abc-DEF_9");
    }

    @Test
    void rejectsOtherUrls() {
        assertThatThrownBy(() -> CloudinarySettings.fromUrl("https://wrong", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOUDINARY_URL");
        assertThatThrownBy(() -> CloudinarySettings.fromUrl(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CloudinarySettings.fromUrl("cloudinary://keyonly@demo", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startsWithValidSettingsAndNeverServesFilesLocally() {
        CloudinaryFileStorage storage = new CloudinaryFileStorage(CloudinarySettings.fromUrl("cloudinary://key:secret@demo", "clinic"));
        assertThat(storage.client().config.cloudName).isEqualTo("demo");
        assertThat(storage.client().config.secure).isTrue();
        assertThat(storage.load("anything.png")).isEmpty();
    }

    @Test
    void explainsCloudinaryErrors() {
        CloudinarySettings s = new CloudinarySettings("demo", "123", "x", null);
        assertThat(StorageSettingsService.friendlyError(new RuntimeException("Invalid cloud_name demo"), s)).contains("cloud name \"demo\"");
        assertThat(StorageSettingsService.friendlyError(new RuntimeException("Invalid api_key 123"), s)).contains("API key 123");
        assertThat(StorageSettingsService.friendlyError(new RuntimeException("wrapped", new java.net.UnknownHostException("api.cloudinary.com")), s))
                .contains("Could not reach Cloudinary");
    }
}
