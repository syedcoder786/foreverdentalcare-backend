package com.clinic.util;

import com.clinic.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.Set;

/** Phone number helpers for sign-in: everything is stored and compared in E.164 form (e.g. +919876543210). */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * Converts user input to E.164. Numbers without a country code get {@code defaultCountryCode}:
     * "98765 43210", "098765 43210" and "+91 98765 43210" all become "+919876543210".
     */
    public static String toE164(String raw, String defaultCountryCode) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("phone", "Please enter your mobile number");
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        String cc = defaultCountryCode == null ? "" : defaultCountryCode.replaceAll("\\D", "");
        String e164;
        if (trimmed.startsWith("+")) {
            e164 = "+" + digits;
        } else if (digits.startsWith("00")) {
            e164 = "+" + digits.substring(2);
        } else {
            String national = digits.replaceFirst("^0+", "");
            e164 = national.length() == 10 && !cc.isEmpty() ? "+" + cc + national : "+" + national;
        }
        int length = e164.length() - 1;
        if (length < 8 || length > 15) {
            throw new BusinessException("phone", "Please enter a valid mobile number");
        }
        return e164;
    }

    /** Ways the same number may already be stored for patients created by older bookings. */
    public static Set<String> storedVariants(String e164, String defaultCountryCode) {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(e164);
        variants.add(e164.substring(1));
        String cc = defaultCountryCode == null ? "" : defaultCountryCode.replaceAll("\\D", "");
        if (!cc.isEmpty() && e164.startsWith("+" + cc)) {
            String national = e164.substring(cc.length() + 1);
            variants.add(national);
            variants.add("0" + national);
        }
        return variants;
    }

    /** "+919876543210" becomes "+91 ••••• •3210" for display in messages. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 6) {
            return e164;
        }
        return e164.substring(0, 3) + " ••••• •" + e164.substring(e164.length() - 4);
    }
}
