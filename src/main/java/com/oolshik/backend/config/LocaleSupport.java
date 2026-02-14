package com.oolshik.backend.config;

import java.util.Locale;

public final class LocaleSupport {

    public static final String EN_IN_TAG = "en-IN";
    public static final String MR_IN_TAG = "mr-IN";
    public static final Locale EN_IN = Locale.forLanguageTag(EN_IN_TAG);
    public static final Locale MR_IN = Locale.forLanguageTag(MR_IN_TAG);

    private LocaleSupport() {
    }

    public static String normalizeTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return EN_IN_TAG;
        }
        String normalized = raw.trim().replace('_', '-');
        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return EN_IN_TAG;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("mr")) {
            return MR_IN_TAG;
        }
        return EN_IN_TAG;
    }

    public static Locale normalizeLocale(Locale locale) {
        if (locale == null) {
            return EN_IN;
        }
        return Locale.forLanguageTag(normalizeTag(locale.toLanguageTag()));
    }
}
