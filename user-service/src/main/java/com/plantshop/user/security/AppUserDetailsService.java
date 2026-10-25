package com.plantshop.user.security;

import com.plantshop.user.entity.User;
import com.plantshop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HTTP Basic login by e-mail. Anonymized accounts have no password hash and can no longer log in. */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email))
                .filter(User::isActive)
                .filter(user -> user.getPasswordHash() != null)
                .map(AppUserPrincipal::new)
                // the message deliberately does not repeat the e-mail
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
