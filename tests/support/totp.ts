import { createHmac } from 'node:crypto';

/**
 * Port TypeScript de `TotpGenerator` (backend, RFC 6238 / RFC 4226) —
 * HMAC-SHA1, pas de 30 secondes, code à 6 chiffres. Aucune dépendance
 * externe : `node:crypto` suffit à reproduire exactement l'algorithme
 * vérifié côté serveur contre les vecteurs de test de la RFC 6238
 * (`backend/.../TotpGeneratorTests.java`).
 *
 * Utilisé UNIQUEMENT pour franchir le second facteur des comptes fictifs
 * de démonstration (ADMIN / SUPER_ADMIN) dans la recette Playwright —
 * jamais pour un compte réel (dette T-19/T-20).
 */
const BASE32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const STEP_SECONDS = 30;
const DIGITS = 6;

function decodeBase32(encoded: string): Buffer {
  const normalized = encoded.trim().toUpperCase().replace(/=+$/, '');
  const bytes: number[] = [];
  let buffer = 0;
  let bitsLeft = 0;
  for (const char of normalized) {
    const value = BASE32_ALPHABET.indexOf(char);
    if (value < 0) {
      throw new Error(`Secret base32 invalide : caractère "${char}" hors alphabet.`);
    }
    buffer = (buffer << 5) | value;
    bitsLeft += 5;
    if (bitsLeft >= 8) {
      bytes.push((buffer >> (bitsLeft - 8)) & 0xff);
      bitsLeft -= 8;
    }
  }
  return Buffer.from(bytes);
}

function stepOf(date: Date): bigint {
  return BigInt(Math.floor(date.getTime() / 1000 / STEP_SECONDS));
}

function codeAtStep(base32Secret: string, step: bigint): string {
  const key = decodeBase32(base32Secret);
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(step);
  const mac = createHmac('sha1', key).update(counter).digest();
  const offset = mac[mac.length - 1] & 0x0f;
  const binary =
    ((mac[offset] & 0x7f) << 24) |
    ((mac[offset + 1] & 0xff) << 16) |
    ((mac[offset + 2] & 0xff) << 8) |
    (mac[offset + 3] & 0xff);
  const code = (binary % 10 ** DIGITS).toString();
  return code.padStart(DIGITS, '0');
}

/** Code TOTP courant (pas de l'instant donné, défaut : maintenant). */
export function currentTotpCode(base32Secret: string, now: Date = new Date()): string {
  return codeAtStep(base32Secret, stepOf(now));
}
