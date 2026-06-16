package org.group7.wearwise.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.service.AuthTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;
    private final AppUserRepository appUserRepository;

    public BearerTokenAuthenticationFilter(
            AuthTokenService authTokenService,
            AppUserRepository appUserRepository
    ) {
        this.authTokenService = authTokenService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null
                && authorizationHeader.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length());
            authTokenService.validateAndGetUsername(token)
                    .filter(appUserRepository::existsByUsername)
                    .ifPresent(this::authenticate);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String username) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
