package com.esic.connect.identity.internal;

import java.time.Instant;
import java.util.UUID;

/** Contrats HTTP des appareils de confiance (EF-AUTH-013). */
public final class TrustedDeviceWeb {

    private TrustedDeviceWeb() {
    }

    /**
     * @param usable faux lorsque la confiance a expiré sans révocation
     *               explicite : l'appareil reste listé, mais une
     *               vérification complète sera redemandée
     */
    public record DeviceResponse(UUID id,
                                 String label,
                                 Instant firstSeenAt,
                                 Instant lastSeenAt,
                                 Instant expiresAt,
                                 boolean usable) {
    }
}
