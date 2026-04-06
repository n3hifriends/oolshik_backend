package com.oolshik.backend.util;

public final class MaskingUtils {

    private MaskingUtils() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "[blank]";
        }
        String normalized = PhoneUtil.normalize(phone);
        if (normalized == null || normalized.isBlank()) {
            return "[blank]";
        }
        int visibleSuffix = Math.min(2, normalized.length());
        int visiblePrefix = normalized.startsWith("+") ? Math.min(3, normalized.length()) : Math.min(2, normalized.length());
        if (normalized.length() <= visiblePrefix + visibleSuffix) {
            return "[redacted]";
        }
        String prefix = normalized.substring(0, visiblePrefix);
        String suffix = normalized.substring(normalized.length() - visibleSuffix);
        return prefix + "*".repeat(Math.max(normalized.length() - visiblePrefix - visibleSuffix, 4)) + suffix;
    }

    public static String redactOtp() {
        return "[redacted]";
    }
}
