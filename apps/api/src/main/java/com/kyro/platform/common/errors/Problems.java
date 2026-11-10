package com.kyro.platform.common.errors;

import com.kyro.platform.common.observability.CorrelationContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds RFC 9457 problem bodies (PLAN section 6). Shared by the MVC advice and servlet-level handlers. */
public final class Problems {

    private Problems() {}

    public static Map<String, Object> body(
            int status, String code, String detail, String instance, List<FieldError> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "urn:kyro:problem:" + code);
        m.put("title", titleFor(code));
        m.put("status", status);
        m.put("detail", detail);
        m.put("instance", instance);
        m.put("code", code);
        m.put("correlationId", CorrelationContext.current());
        if (errors != null && !errors.isEmpty()) {
            m.put("errors", errors.stream()
                    .map(e -> Map.of("field", e.field(), "message", e.message()))
                    .toList());
        }
        return m;
    }

    public static String titleFor(String code) {
        String lower = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
