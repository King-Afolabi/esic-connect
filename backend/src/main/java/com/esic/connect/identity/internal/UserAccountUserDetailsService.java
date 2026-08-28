package com.esic.connect.identity.internal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adaptateur {@link UserDetailsService} reposant sur
 * {@link UserAccountRepository}. Le message de
 * {@link UsernameNotFoundException} reste interne : Spring Security le
 * masque par défaut (voir AuthenticationService).
 */
@Service
public class UserAccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;

    public UserAccountUserDetailsService(UserAccountRepository userAccountRepository,
                                          UserRoleRepository userRoleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = EmailNormalization.normalize(email);
        UserAccount account = userAccountRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable."));
        return new UserAccountUserDetails(account, activeAuthorities(account));
    }

    private List<GrantedAuthority> activeAuthorities(UserAccount account) {
        return userRoleRepository.findByUserId(account.getId()).stream()
                .filter(UserRole::isActive)
                .map(userRole -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + userRole.getRole().getCode()))
                .toList();
    }
}
