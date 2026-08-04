package com.xenosync.security;

import com.xenosync.model.User;
import com.xenosync.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    // BCrypt-shaped but not derivable from any real password — used for GitHub-only
    // accounts (password_hash IS NULL) so passwordEncoder.matches() always returns
    // false instead of the auth path NPEing on a null hash.
    private static final String UNUSABLE_PASSWORD_HASH =
            "{bcrypt}$2a$10$UnusableHashForGitHubOnlyAccountsXXXXXXXXXXXXXXXXXXXXXX";

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Spring Security's contract calls this "username" but XenoSync logs in
     * by email — this method takes the email string. loadUserById is used
     * separately by JwtAuthFilter, where we already have a trusted userId
     * from a validated JWT and don't want a second email lookup.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));
        return toUserDetails(user);
    }

    public UserDetails loadUserById(UUID userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("No user with id: " + userId));
        return toUserDetails(user);
    }

    private UserDetails toUserDetails(User user) {
        String hash = user.getPasswordHash() != null ? user.getPasswordHash() : UNUSABLE_PASSWORD_HASH;

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(hash)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}