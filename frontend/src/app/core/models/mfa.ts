/**
 * Contrats du second facteur et des clés d'accès, alignés sur
 * `MfaWeb`, `WebAuthnWeb` et `TrustedDeviceWeb` côté serveur
 * (EF-AUTH-006 à EF-AUTH-013).
 */

/** Défi renvoyé par `POST /auth/login` quand la connexion doit être complétée. */
export interface MfaChallenge {
  challengeId: string;
  /** `VERIFY` : un facteur actif existe. `ENROLL` : il faut d'abord en créer un. */
  purpose: 'VERIFY' | 'ENROLL' | 'STEP_UP';
  expiresInSeconds: number;
}

export interface MfaEnrollment {
  /** Secret partagé, affiché une seule fois. */
  secret: string;
  /** URI `otpauth://` à encoder en QR code. */
  provisioningUri: string;
  periodSeconds: number;
}

export interface MfaStatus {
  enabled: boolean;
  enrollmentPending: boolean;
  requiredByRole: boolean;
  confirmedAt: string | null;
  activeRecoveryCodes: number;
}

/** Clé d'accès enregistrée (`WebAuthnWeb.CredentialResponse`). */
export interface PasskeyCredential {
  id: string;
  label: string;
  createdAt: string;
  lastUsedAt: string | null;
}

/** Appareil de confiance (`TrustedDeviceWeb.DeviceResponse`). */
export interface TrustedDevice {
  id: string;
  label: string;
  firstSeenAt: string;
  lastSeenAt: string;
  expiresAt: string;
  usable: boolean;
}

/** Configuration publique du contrôle anti-robot (EF-AUTH-011). */
export interface CaptchaConfig {
  /** Faux si aucun fournisseur n'est configuré : le widget est alors inutile. */
  enforced: boolean;
  siteKey: string;
}
