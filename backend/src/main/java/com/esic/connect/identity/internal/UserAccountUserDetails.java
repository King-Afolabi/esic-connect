package com.esic.connect.identity.internal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Adaptateur {@link UserDetails} : ne duplique aucune donnée, référence
 * directement le compte persistant.
 */
final class UserAccountUserDetails implements UserDetails {

    private final UserAccount account;
    private final Collection<? extends GrantedAuthority> authorities;

    UserAccountUserDetails(UserAccount account, Collection<? extends GrantedAuthority> authorities) {
        this.account = account;
        this.authorities = authorities;
    }

    UserAccount getAccount() {
        return account;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return account.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return account.getStatus() != AccountStatus.LOCKED && account.getStatus() != AccountStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Seul un compte {@code ACTIVE} peut se connecter (docs/02 §8.3/§9.4). */
    @Override
    public boolean isEnabled() {
        return account.getStatus() == AccountStatus.ACTIVE;
    }
}
