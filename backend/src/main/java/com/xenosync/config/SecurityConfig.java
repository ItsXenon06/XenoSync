package com.xenosync.config;

import com.xenosync.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final GithubOAuth2SuccessHandler githubOAuth2SuccessHandler;
    private final GithubOAuth2FailureHandler githubOAuth2FailureHandler;
    private final SessionRateLimitFilter sessionRateLimitFilter;


    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            CustomOAuth2UserService customOAuth2UserService,
            GithubOAuth2SuccessHandler githubOAuth2SuccessHandler,
            GithubOAuth2FailureHandler githubOAuth2FailureHandler,
            RateLimitFilter rateLimitFilter,
            SessionRateLimitFilter sessionRateLimitFilter, SessionRateLimitFilter sessionRateLimitFilter1
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.customOAuth2UserService = customOAuth2UserService;
        this.githubOAuth2SuccessHandler = githubOAuth2SuccessHandler;
        this.githubOAuth2FailureHandler = githubOAuth2FailureHandler;
        this.rateLimitFilter = rateLimitFilter;
        this.sessionRateLimitFilter = sessionRateLimitFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Request-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // SameSite=Lax on our auth cookies is the CSRF defense here (Section 5) —
                // this isn't a session/form-login app, so Spring's CSRF token machinery
                // would be redundant, not additive.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())

                // No server-side HttpSession — auth state lives entirely in the JWT cookie,
                // re-derived on every request by JwtAuthFilter.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v1/auth/register",
                                "/v1/auth/login",
                                "/v1/auth/refresh",
                                "/v1/auth/logout",
                                "/v1/auth/oauth/exchange",
                                "/v1/auth/oauth/complete-signup",
                                "/v1/auth/verify-email",
                                "/v1/auth/resend-verification",
                                "/v1/auth/forgot-password",
                                "/v1/auth/reset-password",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**"
                        ).permitAll()
                        // /auth/logout intentionally NOT listed — AUTH.md Section 8.1:
                        // requires an authenticated request.
                        .anyRequest().authenticated()
                )

                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(sessionRateLimitFilter, JwtAuthFilter.class)

                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(githubOAuth2SuccessHandler)
                        .failureHandler(githubOAuth2FailureHandler)
                );

        return http.build();
    }

}