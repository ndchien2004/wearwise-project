package org.group7.wearwise.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.service.AuthTokenDetails;
import org.group7.wearwise.service.AuthTokenRevocationService;
import org.group7.wearwise.service.AuthTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;
    private final AuthTokenRevocationService authTokenRevocationService;
    private final AppUserRepository appUserRepository;

    public BearerTokenAuthenticationFilter(
            AuthTokenService authTokenService,
            AuthTokenRevocationService authTokenRevocationService,
            AppUserRepository appUserRepository
    ) {
        this.authTokenService = authTokenService;
        this.authTokenRevocationService = authTokenRevocationService;
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

            authTokenService.validateAndGetDetails(token)
                    .filter(details -> !authTokenRevocationService.isRevoked(token))
                    .flatMap(this::loadActiveUser)
                    .ifPresent(this::authenticate);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Token chỉ được chấp nhận khi tài khoản còn tồn tại, không bị khóa, và token được
     * phát hành sau lần đổi mật khẩu gần nhất.
     */
    private Optional<AppUser> loadActiveUser(AuthTokenDetails details) {
        return appUserRepository.findByUsername(details.username())
                .filter(user -> !isLocked(user))
                .filter(user -> isIssuedAfterPasswordChange(details, user));
    }

    private boolean isLocked(AppUser user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private boolean isIssuedAfterPasswordChange(AuthTokenDetails details, AppUser user) {
        LocalDateTime passwordChangedAt = user.getPasswordChangedAt();
        if (passwordChangedAt == null || details.issuedAt() == null) {
            return true;
        }

        // `iat` chỉ có độ phân giải giây, nên cắt mốc đổi mật khẩu về giây để token vừa
        // phát hành ngay sau khi đổi mật khẩu không bị loại nhầm.
        Instant passwordChangedAtInstant = passwordChangedAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS);

        return !details.issuedAt().isBefore(passwordChangedAtInstant);
    }

    private void authenticate(AppUser user) {
        String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
