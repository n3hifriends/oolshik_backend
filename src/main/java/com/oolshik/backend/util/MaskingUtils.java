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

    public static String maskUpiId(String upiId) {
        if (upiId == null || upiId.isBlank()) {
            return "[blank]";
        }
        String normalized = upiId.trim();
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex == normalized.length() - 1) {
            return "[redacted]";
        }

        String handle = normalized.substring(0, atIndex);
        String provider = normalized.substring(atIndex + 1);
        String visiblePrefix = handle.substring(0, Math.min(2, handle.length()));
        String visibleSuffix = handle.substring(Math.max(handle.length() - 1, 0));
        String maskedHandle =
                handle.length() <= 3
                        ? visiblePrefix + "*".repeat(Math.max(1, handle.length() - visiblePrefix.length()))
                        : visiblePrefix + "*".repeat(Math.max(2, handle.length() - 3)) + visibleSuffix;
        return maskedHandle + "@" + provider;
    }
}
