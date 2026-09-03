package com.esic.connect.identity.internal;

import com.esic.connect.identity.LoginFailedEvent;
import com.esic.connect.identity.LoginSucceededEvent;
import com.esic.connect.shared.captcha.CaptchaGuard;
import com.esic.connect.shared.captcha.LocalCaptchaVerifier;
import com.esic.connect.shared.ratelimit.RateLimitDecision;
import com.esic.connect.shared.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AuthenticationService}, en particulier la
 * garantie qu'un échec de journalisation d'audit ne modifie ni ne
 * masque jamais le résultat réel de l'authentification.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTests {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private JwtEncoder jwtEncoder;
    @Mock
    private MfaService mfaService;
    @Mock
    private TrustedDeviceService trustedDeviceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private Jwt jwt;

    /**
     * Limiteur permissif : ces tests portent sur le comportement d'audit
     * et d'authentification, pas sur la limitation de débit, qui a ses
     * propres tests (RedisRateLimiterTests, AuthRateLimitIntegrationTests).
     */
    private final RateLimiter permissiveRateLimiter = new RateLimiter() {
        @Override
        public RateLimitDecision consume(String bucket, String identityHash, int limit,
                                         java.time.Duration window) {
            return RateLimitDecision.allowed(limit);
        }

        @Override
        public long currentCount(String bucket, String identityHash) {
            // Aucun échec observé : le contrôle anti-robot ne se déclenche pas.
            return 0L;
        }

        @Override
        public void observe(String bucket, String identityHash, java.time.Duration window) {
            // Sans effet : aucun compteur n'est tenu ici.
        }

        @Override
        public void reset(String bucket, String identityHash) {
            // Sans effet : aucun compteur n'est tenu ici.
        }
    };

    private AuthenticationService newService() {
        // Aucun second facteur exigé : ces tests portent sur l'audit et sur
        // la propagation d'exception, pas sur la politique MFA, qui a ses
        // propres tests (MfaIntegrationTests).
        lenient().when(mfaService.challengeFor(any(), any(), anyBoolean()))
                .thenReturn(Optional.empty());
        AccessTokenIssuer issuer = new AccessTokenIssuer(jwtEncoder, "esic-connect-test", 900L);
        return new AuthenticationService(authenticationManager, userAccountRepository, issuer,
                mfaService, trustedDeviceService, eventPublisher, permissiveRateLimiter,
                new CaptchaGuard(new LocalCaptchaVerifier()),
                10, 60, java.time.Duration.ofMinutes(15), 3);
    }

    private UserAccount activeUser() {
        UserAccount account = new UserAccount("user@esic-connect.test", "P", "N", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", 1L);
        ReflectionTestUtils.setField(account, "publicId", UUID.randomUUID());
        return account;
    }

    @Test
    void successfulLoginPublishesSucceededEventAndReturnsToken() {
        UserAccount account = activeUser();
        UserAccountUserDetails principal = new UserAccountUserDetails(account,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        when(authenticationManager.authenticate(any()))
                .thenReturn(new TestingAuthenticationToken(principal, null, List.of()));
        when(jwtEncoder.encode(any())).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("stub-token");

        LoginResponse response = newService().login("user@esic-connect.test", "irrelevant", "127.0.0.1", null, null);

        assertThat(response.accessToken()).isEqualTo("stub-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(account.getLastLoginAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(LoginSucceededEvent.class));
    }

    @Test
    void successStillReturnsTokenWhenAuditPublishingFails() {
        UserAccount account = activeUser();
        UserAccountUserDetails principal = new UserAccountUserDetails(account, List.of());
        when(authenticationManager.authenticate(any()))
                .thenReturn(new TestingAuthenticationToken(principal, null, List.of()));
        when(jwtEncoder.encode(any())).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("stub-token");
        doThrow(new RuntimeException("panne technique simulée")).when(eventPublisher).publishEvent(any());

        LoginResponse response = newService().login("user@esic-connect.test", "irrelevant", "127.0.0.1", null, null);

        assertThat(response.accessToken()).isEqualTo("stub-token");
    }

    @Test
    void failedLoginRethrowsOriginalExceptionAndPublishesFailedEvent() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mauvais mot de passe"));
        when(userAccountRepository.findByEmail("unknown@esic-connect.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().login("unknown@esic-connect.test", "wrong", "127.0.0.1", null, null))
                .isInstanceOf(BadCredentialsException.class);

        verify(eventPublisher).publishEvent(any(LoginFailedEvent.class));
    }

    @Test
    void failureStillRethrowsOriginalExceptionWhenAuditPublishingFails() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mauvais mot de passe"));
        when(userAccountRepository.findByEmail("unknown@esic-connect.test")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("panne technique simulée")).when(eventPublisher).publishEvent(any());

        // L'échec d'audit ne doit jamais masquer la vraie cause ni exposer
        // une autre information : l'exception d'authentification d'origine
        // reste celle propagée à l'appelant.
        assertThatThrownBy(() -> newService().login("unknown@esic-connect.test", "wrong", "127.0.0.1", null, null))
                .isInstanceOf(BadCredentialsException.class);
    }
}
