package com.esic.connect.identity.internal;

/** Cycle de vie d'un appareil de confiance (migration V18). */
public enum TrustedDeviceStatus {
    ACTIVE,
    /** Révoqué par l'utilisateur ou par un incident ; plus jamais reconnu. */
    REVOKED
}
