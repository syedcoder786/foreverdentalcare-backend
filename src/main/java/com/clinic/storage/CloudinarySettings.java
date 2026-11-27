package com.clinic.storage;

import com.cloudinary.Cloudinary;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cloudinary account details: the parts of {@code CLOUDINARY_URL=cloudinary://<api_key>:<api_secret>@<cloud_name>}
 * plus the folder uploads go into. {@link #toString()} never contains the secret.
 */
public record CloudinarySettings(String cloudName, String apiKey, String apiSecret, String folder) {

    private static final Pattern URL = Pattern.compile("^cloudinary://([^:@/]+):([^@/]+)@([^/?#]+)/?$");
    /** Seconds Cloudinary gets to answer one request. */
    private static final int TIMEOUT_SECONDS = 20;

    public CloudinarySettings {
        cloudName = trim(cloudName);
        apiKey = trim(apiKey);
        apiSecret = trim(apiSecret);
        folder = normalizeFolder(folder);
    }

    /**
     * Reads a CLOUDINARY_URL. Throws {@link IllegalArgumentException} with a readable message when it is not one.
     */
    public static CloudinarySettings fromUrl(String url, String folder) {
        Matcher m = url == null ? null : URL.matcher(url.trim());
        if (m == null || !m.matches()) {
            throw new IllegalArgumentException(
                    "CLOUDINARY_URL must look like cloudinary://<api_key>:<api_secret>@<cloud_name>");
        }
        return new CloudinarySettings(decode(m.group(3)), decode(m.group(1)), decode(m.group(2)), folder);
    }

    public Cloudinary client() {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", true);
        config.put("timeout", TIMEOUT_SECONDS);
        return new Cloudinary(config);
    }

    /** The CLOUDINARY_URL with the secret hidden. */
    public String maskedUrl() {
        return "cloudinary://" + apiKey + ":" + "*".repeat(8) + "@" + cloudName;
    }

    public CloudinarySettings withSecret(String secret) {
        return new CloudinarySettings(cloudName, apiKey, secret, folder);
    }

    @Override
    public String toString() {
        return "cloud '" + cloudName + "', folder '" + (folder == null ? "" : folder) + "'";
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeFolder(String folder) {
        if (folder == null) {
            return null;
        }
        String f = folder.trim().replaceAll("^/+|/+$", "");
        return f.isEmpty() ? null : f;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
