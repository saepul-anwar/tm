package com.studenttask.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.common.api.ApiResponse;
import com.studenttask.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defence in depth for the cookie-authenticated endpoints (refresh, logout) next to SameSite=Strict: when the
 * browser sends an {@code Origin} header it must be one of {@code app.cors.allowedOrigins}. A missing header is
 * allowed (same-origin proxy, non-browser clients). Must run before Spring's CorsFilter so the caller gets the
 * standard error envelope. Not a Spring bean on purpose (see JwtAuthenticationFilter).
 */
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of("/api/auth/refresh", "/api/auth/logout");

    private final ObjectMapper objectMapper;
    private final Set<String> allowedOrigins;

    public OriginCheckFilter(ObjectMapper objectMapper, List<String> allowedOrigins) {
        this.objectMapper = objectMapper;
        this.allowedOrigins = Set.copyOf(allowedOrigins.stream().map(String::trim).toList());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PROTECTED_PATHS.contains(RequestPaths.canonical(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || allowedOrigins.contains(origin)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ErrorCode.FORBIDDEN, "Origin tidak diizinkan.", null));
    }
}
