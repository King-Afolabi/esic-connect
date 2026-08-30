package com.esic.connect.identity.internal;

import com.esic.connect.identity.DemoAccountProvisioner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implémentation du port {@link DemoAccountProvisioner}, confinée à
 * {@code identity.internal} et enregistrée <strong>uniquement sous le
 * profil {@code demo}</strong>. Idempotente ; ne journalise jamais le mot
 * de passe.
 */
@Component
@Profile("demo")
class DefaultDemoAccountProvisioner implements DemoAccountProvisioner {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    DefaultDemoAccountProvisioner(UserAccountRepository userAccountRepository,
                                  UserRoleRepository userRoleRepository,
                                  RoleRepository roleRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UUID ensureActiveAccount(String email, String firstName, String lastName, String rawPassword,
                                    Set<String> roleCodes) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Set<RoleCode> requestedRoles = roleCodes.stream()
                .map(code -> RoleCode.valueOf(code.trim().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());

        UserAccount account = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (account == null) {
            account = new UserAccount(normalizedEmail, firstName, lastName, AccountStatus.PENDING_ACTIVATION);
            account.activateWithPassword(passwordEncoder.encode(rawPassword), Instant.now());
            account = userAccountRepository.saveAndFlush(account);
        }

        Set<RoleCode> alreadyActive = userRoleRepository.findActiveWithRoleByUserId(account.getId()).stream()
                .map(userRole -> userRole.getRole().getCode())
                .collect(Collectors.toUnmodifiableSet());
        for (RoleCode roleCode : requestedRoles) {
            if (!alreadyActive.contains(roleCode)) {
                Role role = roleRepository.findByCode(roleCode).orElseThrow();
                userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
            }
        }
        return account.getPublicId();
    }
}
