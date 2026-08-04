package com.xenosync.config;

import com.xenosync.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // SameSite=Lax on our auth cookies is the CSRF defense here (Section 5) —
                // this isn't a session/form-login app, so Spring's CSRF token machinery
                // would be redundant, not additive.
                .csrf(csrf -> csrf.disable())

                // No server-side HttpSession — auth state lives entirely in the JWT cookie,
                // re-derived on every request by JwtAuthFilter.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/oauth/exchange",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/auth/forgot-password",
                                "/auth/reset-password"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // TODO (next step, per build order): wire GitHub OAuth2 identity login.
        // .oauth2Login(oauth2 -> oauth2.successHandler(githubOAuth2SuccessHandler))
        // Deferred until GithubOAuth2SuccessHandler exists — referencing it now
        // would be a compile error against a class that isn't written yet.

        return http.build();
    }
}