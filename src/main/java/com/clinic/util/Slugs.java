package com.clinic.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** SEO-friendly URL slugs: "Root Canal Treatment" -> "root-canal-treatment". */
public final class Slugs {

    public static final String PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private Slugs() {
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.length() > 100) {
            normalized = normalized.substring(0, 100).replaceAll("-+$", "");
        }
        return normalized.isEmpty() ? "item" : normalized;
    }

    /** Uses the requested slug (or one derived from the name) and appends -2, -3... until `taken` returns false. */
    public static String unique(String requested, String fallbackName, Predicate<String> taken) {
        String base = requested == null || requested.isBlank() ? slugify(fallbackName) : slugify(requested);
        String candidate = base;
        int n = 2;
        while (taken.test(candidate)) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }
}
