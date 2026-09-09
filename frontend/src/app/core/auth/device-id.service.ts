import { Injectable } from '@angular/core';

/**
 * Identifiant d'appareil, envoyé au serveur dans l'en-tête
 * `X-Device-Id` (EF-AUTH-010, EF-AUTH-013).
 *
 * ## Pourquoi `localStorage` ici, et pourquoi ce n'est pas une entorse
 *
 * La règle du dépôt interdit d'y placer un **jeton sensible** : un jeton
 * d'accès volé donne la session. Cette valeur-ci n'est pas un
 * justificatif d'identité — c'est une chaîne aléatoire opaque, sans
 * signification hors du serveur, qui n'ouvre aucune session à elle
 * seule. Elle doit survivre au rechargement de page, sinon la
 * reconnaissance d'appareil ne peut pas exister.
 *
 * Ce qu'elle apporte : sur un appareil déjà reconnu, un compte ordinaire
 * n'a pas à ressaisir son second facteur à chaque connexion. Ce qu'elle
 * ne donne jamais : ni contournement du mot de passe, ni dispense pour un
 * compte privilégié (RG-007).
 *
 * Le serveur n'en conserve qu'une empreinte : la valeur brute ne lui
 * survit pas (RG-094).
 */
@Injectable({ providedIn: 'root' })
export class DeviceIdService {
  private static readonly STORAGE_KEY = 'esic-connect.device-id';

  private cached: string | null = null;

  /**
   * Identifiant de cet appareil ; en génère un à la première demande.
   *
   * Renvoie `null` si le stockage local est indisponible (navigation
   * privée verrouillée, stockage désactivé) : le serveur traite alors la
   * connexion comme venant d'un appareil inconnu, ce qui est le
   * comportement sûr.
   */
  current(): string | null {
    if (this.cached !== null) {
      return this.cached;
    }
    try {
      const existing = localStorage.getItem(DeviceIdService.STORAGE_KEY);
      if (existing) {
        this.cached = existing;
        return existing;
      }
      const generated = this.generate();
      localStorage.setItem(DeviceIdService.STORAGE_KEY, generated);
      this.cached = generated;
      return generated;
    } catch {
      return null;
    }
  }

  /** Oublie l'appareil : une prochaine connexion sera traitée comme nouvelle. */
  forget(): void {
    this.cached = null;
    try {
      localStorage.removeItem(DeviceIdService.STORAGE_KEY);
    } catch {
      // Rien à faire : l'identifiant n'a de toute façon pas pu être écrit.
    }
  }

  private generate(): string {
    const raw = new Uint8Array(32);
    crypto.getRandomValues(raw);
    return Array.from(raw, (byte) => byte.toString(16).padStart(2, '0')).join('');
  }
}
