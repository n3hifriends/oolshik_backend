package com.oolshik.notificationworker.service;

import java.util.Locale;

public final class LocaleSupport {

    public static final String EN_IN_TAG = "en-IN";
    public static final String MR_IN_TAG = "mr-IN";

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
        if (normalized.toLowerCase(Locale.ROOT).startsWith("mr")) {
            return MR_IN_TAG;
        }
        return EN_IN_TAG;
    }

    public static boolean isMarathi(String raw) {
        return MR_IN_TAG.equals(normalizeTag(raw));
    }
}

