package com.clinic.util;

public final class ValidationPatterns {

    /** Optional leading '+', then 10-15 digits, allowing spaces, dashes and parentheses as separators. */
    public static final String PHONE = "^\\+?(?:[\\s()-]*\\d){10,15}[\\s()-]*$";

    /** Image references: files uploaded through the admin, or absolute https URLs. Empty allowed. */
    public static final String IMAGE_URL = "^$|^/api/public/files/[A-Za-z0-9._-]+$|^https://\\S+$";

    /** Link targets for buttons/navigation: internal paths, http(s), tel:, mailto: or #anchors. Empty allowed. */
    public static final String LINK_URL = "^$|^/\\S*$|^https?://\\S+$|^tel:\\+?[0-9]+$|^mailto:\\S+$|^#\\S*$";

    /** Absolute http(s) URL (social links). Empty allowed. */
    public static final String WEB_URL = "^$|^https?://\\S+$";

    private ValidationPatterns() {
    }

    /** Normalises a phone number to an optional leading '+' followed by digits only. */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        return trimmed.startsWith("+") ? "+" + digits : digits;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
