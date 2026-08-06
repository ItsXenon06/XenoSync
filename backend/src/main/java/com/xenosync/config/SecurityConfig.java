package com.xenosync.config;

import com.xenosync.security.CustomOAuth2UserService;
import com.xenosync.security.GithubOAuth2FailureHandler;
import com.xenosync.security.GithubOAuth2SuccessHandler;
import com.xenosync.security.JwtAuthFilter;
import com.xenosync.security.RateLimitFilter;
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
    private final RateLimitFilter rateLimitFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final GithubOAuth2SuccessHandler githubOAuth2SuccessHandler;
    private final GithubOAuth2FailureHandler githubOAuth2FailureHandler;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            CustomOAuth2UserService customOAuth2UserService,
            GithubOAuth2SuccessHandler githubOAuth2SuccessHandler,
            GithubOAuth2FailureHandler githubOAuth2FailureHandler,
            RateLimitFilter rateLimitFilter
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.customOAuth2UserService = customOAuth2UserService;
        this.githubOAuth2SuccessHandler = githubOAuth2SuccessHandler;
        this.githubOAuth2FailureHandler = githubOAuth2FailureHandler;
        this.rateLimitFilter = rateLimitFilter;
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
                                "/auth/logout",
                                "/auth/oauth/exchange",
                                "/auth/oauth/complete-signup",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**"
                        ).permitAll()
                        // /auth/logout intentionally NOT listed — AUTH.md Section 8.1:
                        // requires an authenticated request.
                        .anyRequest().authenticated()
                )

                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(githubOAuth2SuccessHandler)
                        .failureHandler(githubOAuth2FailureHandler)
                );

        return http.build();
    }

}