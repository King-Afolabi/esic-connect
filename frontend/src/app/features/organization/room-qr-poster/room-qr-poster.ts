import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { QRCodeComponent } from 'angularx-qrcode';

import { OrganizationApiService } from '../organization-api.service';
import { toOrganizationError } from '../organization-errors';
import { RoomStaticQrView, formatIsoDate } from '../organization.models';
import { buildRoomCheckInUrl } from '../../attendance/check-in-reference';
import { publicOrigin } from '../../attendance/public-origin';

type PosterState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'not-found' }
  | { kind: 'ready'; view: RoomStaticQrView };

/**
 * Affiche imprimable du **QR fixe permanent** d'une salle (EF-ORG-003 ;
 * docs/02 §7.1, §16.6). Destinée à rester posée durablement en salle.
 *
 * <p>Le projet n'a pas de générateur PDF orienté page (le module
 * `document` ne produit que du tabulaire) ; l'affiche est donc une **vue
 * d'impression Angular** propre, avec `@media print`, qui réutilise
 * `angularx-qrcode` déjà présent. Elle ne montre **jamais** la référence
 * complète en clair : seule la forme masquée figure en pied de page ;
 * c'est l'URL encodée dans le QR (invisible à l'œil) qui porte le jeton.
 *
 * <p>Le QR encode <strong>uniquement le jeton opaque</strong> de
 * l'affiche (aucune donnée personnelle), comme le fait déjà
 * `app-qr-display` pour le QR dynamique : l'écran d'émargement de
 * l'apprenant reçoit ce jeton dans son champ « jeton d'affiche » et le
 * valide. Après l'ouverture de la séance / l'heure de début, ce canal est
 * refusé et le QR dynamique du formateur prend le relais (RG-051).
 */
@Component({
  selector: 'app-room-qr-poster',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, QRCodeComponent, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './room-qr-poster.html',
  styleUrl: './room-qr-poster.scss',
})
export class RoomQrPoster {
  private readonly api = inject(OrganizationApiService);
  private readonly route = inject(ActivatedRoute);

  private readonly roomId = this.route.snapshot.paramMap.get('roomId') ?? '';
  /** Site parent — lien de retour absolu (la route est hors du sous-arbre `AppShell`). */
  protected readonly sitePublicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly formatDate = formatIsoDate;
  protected readonly state = signal<PosterState>({ kind: 'loading' });

  protected readonly view = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.view : null;
  });

  /**
   * Contenu encodé dans le QR — jamais rendu en texte dans le DOM.
   *
   * Forme privilégiée : l'**URL absolue d'émargement de salle**
   * (`<origine publique>/attendance?ref=<opaque>`), construite à partir du
   * `checkInPath` fourni par le back-end. C'est la même URL qu'un tag NFC
   * NDEF doit contenir. Repli sur la référence opaque brute si l'URL ne
   * peut pas être construite — compat des lecteurs qui saisissent la
   * référence à la main.
   */
  protected readonly qrPayload = computed(() => {
    const v = this.view();
    if (!v?.issued) {
      return null;
    }
    return buildRoomCheckInUrl(v.checkInPath, publicOrigin()) ?? v.staticQrReference;
  });

  protected readonly siteLine = computed(() => {
    const v = this.view();
    if (!v) {
      return '';
    }
    return [v.siteName, v.buildingName, v.floorLabel].filter(Boolean).join(' · ');
  });

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected print(): void {
    if (typeof window !== 'undefined') {
      window.print();
    }
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.getRoomStaticQr(this.roomId).subscribe({
      next: (view) => this.state.set({ kind: 'ready', view }),
      error: (error: unknown) => {
        const v = toOrganizationError(error);
        if (v.notFound) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        if (v.forbidden) {
          this.state.set({ kind: 'forbidden' });
          return;
        }
        this.state.set({ kind: 'error', message: v.message });
      },
    });
  }
}
