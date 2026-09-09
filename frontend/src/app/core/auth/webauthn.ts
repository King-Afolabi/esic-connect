/**
 * Conversions entre les tableaux d'octets de l'API
 * `navigator.credentials` et le **base64url** utilisé par l'API REST
 * (EF-AUTH-006, EF-AUTH-007).
 *
 * Aucune donnée biométrique ne passe par ici : le navigateur ne restitue
 * qu'une signature et une clé publique (RG-091, AC-020).
 */

/**
 * Décode une valeur base64url en octets.
 *
 * <p>Le tampon est alloué explicitement en {@link ArrayBuffer} : l'API
 * `navigator.credentials` refuse un `SharedArrayBuffer`, et TypeScript
 * distingue désormais les deux dans le type de `Uint8Array`.
 */
export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded + '='.repeat((4 - (padded.length % 4)) % 4));
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function bytesToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Le navigateur et le contexte permettent-ils une clé d'accès ? */
export function isWebAuthnAvailable(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.PublicKeyCredential !== 'undefined' &&
    typeof navigator !== 'undefined' &&
    navigator.credentials !== undefined &&
    // WebAuthn exige un contexte sûr : HTTPS, ou localhost en développement.
    window.isSecureContext
  );
}
