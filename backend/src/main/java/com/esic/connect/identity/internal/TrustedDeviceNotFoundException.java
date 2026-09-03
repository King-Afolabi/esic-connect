package com.esic.connect.identity.internal;

import java.util.UUID;

/** Appareil inconnu, ou appartenant à un autre compte : dans les deux cas {@code 404}. */
public class TrustedDeviceNotFoundException extends RuntimeException {

    public TrustedDeviceNotFoundException(UUID publicId) {
        super("Appareil introuvable : " + publicId);
    }
}
