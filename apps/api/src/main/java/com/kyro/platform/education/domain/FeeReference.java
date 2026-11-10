package com.kyro.platform.education.domain;

import java.util.Locale;

public final class FeeReference {

    private FeeReference() {}

    /** Upper-case alphanumerics only; null/blank input gives null. Used for duplicate detection. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return n.isEmpty() ? null : n;
    }
}
