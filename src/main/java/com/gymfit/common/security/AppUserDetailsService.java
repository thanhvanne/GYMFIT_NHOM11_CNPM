package com.gymfit.common.security;

import com.gymfit.user.AppUser;
import com.gymfit.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = appUserRepository
                .findByEmailIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("user_not_found"));

        return new AppPrincipal(user);
    }
}