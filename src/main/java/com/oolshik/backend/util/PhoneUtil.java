package com.oolshik.backend.util;

public class PhoneUtil {
    // Normalize to E.164-like format: retain + and digits, strip spaces/dashes.
    public static String normalize(String raw) {
    if (raw == null) return null;
    String s = raw.trim().replace(" ", "").replace("-", "");
    if (!s.startsWith("+")) {
        // assume India if 10 digits or starts with 0/91; adapt as needed later
        s = s.replaceFirst("^0+", "");
        if (s.startsWith("91") && s.length() == 12) {
            s = "+" + s;
        } else if (s.length() == 10) {
            s = "+91" + s;
        } else if (s.matches("^\\d{1,15}$")) {
            s = "+" + s;
        }
    }
    return s;
}
}
