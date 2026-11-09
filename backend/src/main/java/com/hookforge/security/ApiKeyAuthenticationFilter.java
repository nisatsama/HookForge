package com.hookforge.security;

import com.hookforge.identity.application.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        boolean authorizationHeaderPresent = authorization != null;
        boolean bearerFormatValid = authorization != null
                && authorization.startsWith("Bearer ")
                && !authorization.substring(7).isBlank();
        log.warn("[AuthDiag] request={} authorization_header_present={} bearer_format_valid={}",
                request.getRequestURI(), authorizationHeaderPresent, bearerFormatValid);

        if (authorization == null || authorization.isBlank()) {
            logSecurityContextBeforeChain(request);
            filterChain.doFilter(request, response);
            return;
        }

        if (!bearerFormatValid) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid API key authentication header"
            );
            return;
        }

        String rawKey = authorization.substring(7).trim();

        var authenticated = apiKeyService.authenticate(rawKey);
        log.warn("[AuthDiag] request={} api_key_authentication_succeeded={}",
                request.getRequestURI(), authenticated.isPresent());
        if (authenticated.isEmpty()) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired API key"
            );
            return;
        }

        ApiKeyService.AuthenticatedTenant apiKeyPrincipal =
                authenticated.get();

        AuthenticatedTenant principal = new AuthenticatedTenant(
                apiKeyPrincipal.tenantId(),
                apiKeyPrincipal.apiKeyId()
        );
        log.warn("[AuthDiag] request={} constructed_principal_tenant_id_present={}",
                request.getRequestURI(), principal.tenantId() != null);

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of()
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
        logSecurityContextBeforeChain(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void logSecurityContextBeforeChain(HttpServletRequest request) {
        var contextAuthentication = SecurityContextHolder.getContext()
                .getAuthentication();
        boolean authenticatedPrincipalPresent = contextAuthentication != null
                && contextAuthentication.isAuthenticated()
                && !(contextAuthentication instanceof AnonymousAuthenticationToken)
                && contextAuthentication.getPrincipal() != null;
        log.warn("[AuthDiag] request={} authenticated_principal_in_context_before_chain={}",
                request.getRequestURI(), authenticatedPrincipalPresent);
    }
}
