package com.ims.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

final class AuthOriginGuardFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;

    AuthOriginGuardFilter(CorsProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.allowedOrigins());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/api/v1/auth/refresh") && !path.equals("/api/v1/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin is not allowed for cookie-authenticated requests");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
