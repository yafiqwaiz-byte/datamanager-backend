package dev.waiz.datamanager.config;

import java.io.IOException;
import java.util.List;
import java.util.Optional;


import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

import dev.waiz.datamanager.util.CookieUtil;
import dev.waiz.datamanager.util.JwtUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

    
    private final JwtAuthFilter jwtAuthFilter;

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
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/logout").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/favicon.ico").permitAll()

                //-Admin Only
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // ── Complete-profile (Google new users) ────────────────
                .requestMatchers("/api/accounts/complete-profile/**").authenticated()

                // ── OCR (USER + STAFF) ─────────────────────────────────
                .requestMatchers("/api/files/ocr/**").hasAnyRole("USER", "STAFF")
                .requestMatchers("/api/ocr/**").hasAnyRole("USER", "STAFF")

                // ── Forms: USER only ───────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/api/forms/submit").hasRole("USER")
                .requestMatchers("/api/forms/my-submissions").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/api/forms/user-templates").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.GET, "/api/forms/**").hasAnyRole("USER", "STAFF")

                // ── Forms: STAFF only ──────────────────────────────────
                .requestMatchers(HttpMethod.POST,   "/api/forms/templates").hasRole("STAFF")
                .requestMatchers(HttpMethod.PUT,    "/api/forms/templates/**").hasRole("STAFF")
                .requestMatchers(HttpMethod.PATCH,  "/api/forms/templates/**").hasRole("STAFF")
                .requestMatchers(HttpMethod.DELETE, "/api/forms/templates/**").hasRole("STAFF")
                .requestMatchers(HttpMethod.GET,    "/api/forms/templates/*/submissions").hasRole("STAFF")
                .requestMatchers(HttpMethod.GET,    "/api/forms/submissions/all").hasRole("STAFF")
                .requestMatchers("/api/staff/**").hasRole("STAFF")
                .requestMatchers("/api/po-aging/**").hasRole("STAFF")

                // ── Letters: USER + STAFF (specific endpoints first) ───
                .requestMatchers(HttpMethod.GET,  "/api/letters/templates/all").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.POST, "/api/letters/mapping/auto").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.PUT,  "/api/letters/mapping/confirm/**").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.POST, "/api/letters/generate/**").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.GET,  "/api/letters/generated/**").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.GET,  "/api/letters/download/**").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.GET, "/api/letters/mapping/**").hasAnyRole("USER", "STAFF")
                .requestMatchers(HttpMethod.PUT, "/api/letters/mapping/confirm/**").hasAnyRole("USER", "STAFF")
                .requestMatchers("/api/ai/**").hasAnyRole("STAFF", "USER")

                // ── Letters: STAFF only catch-all ─────────────────────
                .requestMatchers("/api/letters/**").hasRole("STAFF")

                // ── Everything else: authenticated ─────────────────────
                .requestMatchers("/api/**").authenticated()
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
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        source.registerCorsConfiguration("/uploads/**", configuration);
        return source;
    }

    @Bean
    public FilterRegistrationBean<Filter> coopHeaderFilter(){
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((request,response,chain) -> {
            HttpServletResponse httpresponse = (HttpServletResponse) response;
            httpresponse.setHeader("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
            httpresponse.setHeader("Cross-Origin-Embedder-Policy", "unsafe-none");
            chain.doFilter(request, response);
        });
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Component
    @RequiredArgsConstructor
    public static class JwtAuthFilter extends OncePerRequestFilter {

        
        private final JwtUtil jwtUtil;

        
        private final CookieUtil cookieUtil;

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