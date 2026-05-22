package dev.waiz.datamanager.config;

import dev.waiz.datamanager.util.CookieUtil;
import dev.waiz.datamanager.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:C:/Users/User/Documents/Projectwaiz/datamanager/uploads/");
        registry.addResourceHandler("/uploads/ocr-files/**")
            .addResourceLocations("file:C:/Users/User/Documents/Projectwaiz/datamanager/uploads/ocr-files/");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Public endpoints ───────────────────────────────────
                .requestMatchers("/api/accounts/signin").permitAll()
                .requestMatchers("/api/accounts/signin/google").permitAll()
                .requestMatchers("/api/accounts/signup/user").permitAll()
                .requestMatchers("/api/accounts/signup/staff").permitAll()
                .requestMatchers("/api/accounts/security-question/**").permitAll()
                .requestMatchers("/api/accounts/reset-password").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()   // token refresh is public
                .requestMatchers("/api/auth/logout").permitAll() // logout needs valid session
                .requestMatchers("/uploads/**").permitAll()

                // ── Complete-profile (Google new users) ────────────────
                .requestMatchers("/api/accounts/complete-profile/**").authenticated()

                // ── Role-protected endpoints ───────────────────────────
                .requestMatchers("/api/staff/**").hasRole("STAFF")
                .requestMatchers("/api/ocr/**").hasAnyRole("USER", "STAFF")
                .requestMatchers(org.springframework.http.HttpMethod.POST,   "/api/forms/submit").hasRole("USER")
                .requestMatchers(org.springframework.http.HttpMethod.GET,    "/api/forms/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST,   "/api/forms/templates").hasRole("STAFF")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,    "/api/forms/templates/**").hasRole("STAFF")
                .requestMatchers(org.springframework.http.HttpMethod.PATCH,  "/api/forms/templates/**").hasRole("STAFF")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/forms/templates/**").hasRole("STAFF")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // Required for cookies
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        source.registerCorsConfiguration("/uploads/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ══════════════════════════════════════════════════════════════════
    //  JWT Auth Filter
    //  Reads access token from httpOnly cookie (falls back to Bearer
    //  header so Postman / API clients still work during development).
    // ══════════════════════════════════════════════════════════════════
    @Component
    public static class JwtAuthFilter extends OncePerRequestFilter {

        @Autowired
        private JwtUtil jwtUtil;

        @Autowired
        private CookieUtil cookieUtil;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {

            String token = resolveToken(request);

            if (token != null) {
                if (jwtUtil.validateToken(token)) {
                    String username = jwtUtil.extractUsername(token);
                    String role     = jwtUtil.extractRole(token);

                    System.out.println("✅ Auth: " + username + " | Role: " + role);

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    System.out.println("❌ Invalid token for: " + request.getRequestURI());
                }
            } else {
                System.out.println("❌ No token for: " + request.getRequestURI());
            }

            filterChain.doFilter(request, response);
        }

        /**
         * Token resolution order:
         *  1. httpOnly cookie  (preferred — browser clients)
         *  2. Authorization: Bearer header  (fallback — Postman / mobile)
         */
        private String resolveToken(HttpServletRequest request) {
            // 1. Cookie
            Optional<String> cookieToken =
                cookieUtil.readCookie(request, CookieUtil.ACCESS_TOKEN_COOKIE);
            if (cookieToken.isPresent()) return cookieToken.get();

            // 2. Bearer header fallback
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }

            return null;
        }
    }
}