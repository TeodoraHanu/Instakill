package com.instakill.analytics.web;

import com.instakill.analytics.application.AnalyticsService;
import com.instakill.analytics.dto.AnalyticsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final JdbcTemplate jdbcTemplate;

    public AnalyticsController(AnalyticsService analyticsService, JdbcTemplate jdbcTemplate) {
        this.analyticsService = analyticsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public AnalyticsResponse getAnalytics(
            @RequestHeader(value = "X-User-Name", required = false) String customUserHeader,
            @RequestParam(value = "username", required = false) String paramUser
    ) {
        String identifier = null;

        // 1. Preluam identificatorul transmis din frontend
        if (customUserHeader != null && !customUserHeader.isBlank()) {
            identifier = customUserHeader;
        } else if (paramUser != null && !paramUser.isBlank()) {
            identifier = paramUser;
        } else {
            // 2. Verificam SecurityContextHolder daca antetele lipsesc
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                identifier = auth.getName();
            }
        }

        // Fallback pe charlie daca nu este detectat niciun utilizator
        if (identifier == null || identifier.isBlank()) {
            identifier = "charlie";
        }

        UUID userId = null;

        // Incercam conversia directa din string UUID
        try {
            userId = UUID.fromString(identifier);
        } catch (Exception e) {
            // Daca e username/email, cautam UUID-ul in SQLite
            try {
                String sql = "SELECT id FROM users WHERE username = ? OR email = ? LIMIT 1";
                String idStr = jdbcTemplate.queryForObject(sql, String.class, identifier, identifier);
                if (idStr != null) {
                    userId = UUID.fromString(idStr);
                }
            } catch (Exception ignored) {}
        }

        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilizatorul nu a fost găsit în baza de date");
        }

        return analyticsService.getUserAnalytics(userId);
    }
}