package com.kyro.platform.common.security;

import com.kyro.platform.common.errors.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes problem+json bodies from servlet filters / security handlers (outside Spring MVC). */
@Component
public class ProblemResponseWriter {

    private final JsonMapper mapper;

    public ProblemResponseWriter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(
                response.getOutputStream(),
                Problems.body(status, code, detail, request.getRequestURI(), List.of()));
    }
}
