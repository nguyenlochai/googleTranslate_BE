package com.entr.translator.security;

import org.springframework.stereotype.Component;

@Component
public class AuthHelper {
    private final JwtService jwtService;

    public AuthHelper(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Resolve user email either from explicit request param or from Authorization: Bearer <jwt>.
     * Returns null if neither is available/valid.
     */
    public String resolveEmail(String explicitEmail, String authorizationHeader) {
        if (explicitEmail != null && !explicitEmail.isBlank()) return explicitEmail;

        if (authorizationHeader == null) return null;
        String h = authorizationHeader.trim();
        if (!h.toLowerCase().startsWith("bearer ")) return null;
        String token = h.substring(7).trim();
        if (token.isEmpty()) return null;
        if (!jwtService.isValid(token)) return null;
        try {
            return jwtService.extractEmail(token);
        } catch (Exception e) {
            return null;
        }
    }
}
