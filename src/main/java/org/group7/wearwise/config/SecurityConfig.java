package org.group7.wearwise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            @Value("${wearwise.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            List<String> allowedOrigins
    ) {
        this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register/request-otp",
                                "/api/auth/register/verify",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/reset-password/validate").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()
                        // Khu vực vận hành. Quy tắc đặt tập trung ở đây thay vì rải @PreAuthorize
                        // trên từng phương thức: thêm endpoint admin mới thì nó được bảo vệ sẵn,
                        // không phụ thuộc việc người viết có nhớ gắn annotation hay không.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .headers(SecurityConfig::securityHeaders)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(401, "Unauthorized"))
                        // Đã đăng nhập nhưng không đủ quyền (vd người dùng thường gọi /api/admin).
                        // Phân biệt rõ với 401 để client biết đăng nhập lại cũng vô ích.
                        .accessDeniedHandler((request, response, deniedException) ->
                                response.sendError(403, "Forbidden"))
                )
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Đặt sau filter xác thực để hạn mức tính theo tài khoản khi đã đăng nhập,
                // chỉ rơi về IP với request ẩn danh.
                .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * Header phòng thủ theo chiều sâu. Spring Security sẵn có {@code X-Content-Type-Options:
     * nosniff} và {@code Cache-Control: no-store}; phần dưới bổ sung những header nó không bật
     * mặc định.
     *
     * <p>Origin này chỉ phục vụ JSON và Swagger UI — nó không bao giờ dựng HTML từ dữ liệu người
     * dùng. Vì vậy CSP ở đây không phải lớp chống XSS chính (lớp đó thuộc về frontend, xem
     * {@code frontend/public/_headers}) mà nhằm chặn kịch bản kẻ tấn công dụ nạn nhân mở thẳng
     * một URL API rồi bắt trình duyệt diễn giải phản hồi thành trang web.</p>
     */
    private static void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                // Trình duyệt chỉ ghi nhớ HSTS khi request đã là HTTPS, nên bật cái này không
                // ảnh hưởng gì tới việc chạy http://localhost lúc phát triển.
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000L))
                .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                        // Swagger UI được phục vụ ngay từ origin này nên cần 'self'; phần khởi
                        // tạo của nó là script/style inline nên buộc phải cho phép inline.
                        "default-src 'self'",
                        "script-src 'self' 'unsafe-inline'",
                        "style-src 'self' 'unsafe-inline'",
                        "img-src 'self' data:",
                        "connect-src 'self'",
                        "object-src 'none'",
                        "base-uri 'none'",
                        "form-action 'none'",
                        // Chặn clickjacking; đi cùng X-Frame-Options cho trình duyệt cũ.
                        "frame-ancestors 'none'")))
                .frameOptions(FrameOptionsConfig::deny)
                // Đường dẫn API có chứa id tài nguyên; đừng để chúng rò sang site khác qua Referer.
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                // API không dùng tới các quyền này — tắt hẳn để một trang nhúng không mượn được.
                .permissionsPolicyHeader(permissions -> permissions.policy(
                        "geolocation=(), camera=(), microphone=(), payment=(), usb=()"));
    }

    /**
     * Origin được phép gọi API khai báo qua {@code wearwise.cors.allowed-origins} (phân tách
     * bằng dấu phẩy). Khi deploy frontend riêng — vd Cloudflare Pages — phải thêm domain đó
     * vào, nếu không trình duyệt sẽ chặn ngay ở bước preflight.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Cookie refresh token chỉ được gửi kèm khi CORS cho phép credentials, mà bật credentials
        // thì danh sách origin phải thật sự đóng: một pattern "*" ở đây đồng nghĩa mọi trang web
        // trên Internet đều gọi được API kèm cookie của người dùng. Chặn ngay lúc khởi động.
        List<String> wildcards = allowedOrigins.stream().filter(origin -> origin.trim().equals("*")).toList();
        if (!wildcards.isEmpty()) {
            throw new IllegalStateException(
                    "wearwise.cors.allowed-origins không được chứa '*': API gửi cookie xác thực nên "
                            + "phải liệt kê rõ từng domain frontend (vẫn dùng được ký tự đại diện ở "
                            + "cấp subdomain, ví dụ https://*.wearwise.pages.dev).");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        // setAllowedOriginPatterns (thay vì setAllowedOrigins) để dùng được ký tự đại diện
        // cho các domain preview dạng https://<hash>.wearwise-xyz.pages.dev.
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Bắt buộc để trình duyệt gửi kèm cookie refresh token khi frontend nằm ở domain khác.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
