package com.kyro.platform.support;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// FROZEN CONTRACT (test support)
/**
 * CSRF helper matching the production scheme (double-submit cookie): the {@code XSRF-TOKEN} cookie value must equal
 * the {@code X-XSRF-TOKEN} header. Use on every mutating MockMvc request: {@code post(...).with(Csrf.token())}.
 */
public final class Csrf {

    private Csrf() {}

    public static RequestPostProcessor token() {
        return request -> {
            String value = UUID.randomUUID().toString();
            request.setCookies(appendCookie(request.getCookies(), new Cookie("XSRF-TOKEN", value)));
            request.addHeader("X-XSRF-TOKEN", value);
            return request;
        };
    }

    private static Cookie[] appendCookie(Cookie[] existing, Cookie extra) {
        if (existing == null || existing.length == 0) {
            return new Cookie[] {extra};
        }
        Cookie[] all = new Cookie[existing.length + 1];
        System.arraycopy(existing, 0, all, 0, existing.length);
        all[existing.length] = extra;
        return all;
    }
}
