import { ChangeDetectionStrategy, Component, ElementRef, HostListener, input, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { QRCodeComponent } from 'angularx-qrcode';

/**
 * Rendu visuel d'un QR code encodant **uniquement** la chaîne opaque
 * fournie par le serveur (jeton d'émargement). Angular ne fait
 * qu'afficher cette chaîne sous forme d'image : elle n'est pas insérée en
 * texte dans le DOM, et aucune autorité n'est générée côté client.
 *
 * L'`alt` de l'image ne contient jamais la valeur du jeton. Quand aucune
 * valeur n'est disponible (séance non ouverte, jeton non émis, backend
 * indisponible), le composant affiche un message neutre au lieu du QR.
 *
 * Le QR est centré dans son conteneur, et un bouton plein écran
 * (`Fullscreen API` native, sans dépendance) l'agrandit pour un
 * affichage lisible à distance (vidéoprojecteur, tableau) — utile quand
 * un formateur affiche le code à toute une classe.
 */
@Component({
  selector: 'app-qr-display',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QRCodeComponent, MatButtonModule, MatIconModule],
  template: `
    <div class="qr-display" #container>
      @if (value()) {
        <qrcode
          [qrdata]="value()!"
          [width]="isFullscreen() ? 512 : 256"
          [margin]="2"
          errorCorrectionLevel="M"
          elementType="img"
          alt="QR code d'émargement de la séance"
          cssClass="qr-display__code"
        ></qrcode>
        <button
          mat-stroked-button
          type="button"
          class="qr-display__fullscreen-toggle"
          (click)="toggleFullscreen()"
        >
          <mat-icon aria-hidden="true">{{ isFullscreen() ? 'fullscreen_exit' : 'fullscreen' }}</mat-icon>
          <span>{{ isFullscreen() ? 'Quitter le plein écran' : 'Plein écran' }}</span>
        </button>
      } @else {
        <p class="qr-display__empty" role="status">
          Aucun QR code disponible. Ouvrez la séance, puis affichez un code.
        </p>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .qr-display {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.75rem;
      text-align: center;
    }
    .qr-display:fullscreen {
      justify-content: center;
      height: 100%;
      background: #fff;
    }
    .qr-display__code {
      display: block;
      max-width: 100%;
    }
    .qr-display__empty {
      margin: 0;
      color: var(--mat-sys-on-surface-variant, #5f6368);
    }
  `,
})
export class QrDisplay {
  /** Chaîne opaque à encoder ; `null` / vide → message neutre. */
  readonly value = input<string | null>(null);

  private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
  protected readonly isFullscreen = signal(false);

  /** Le plein écran natif peut être quitté par l'utilisateur (Échap) sans passer par le bouton. */
  @HostListener('document:fullscreenchange')
  protected onFullscreenChange(): void {
    this.isFullscreen.set(document.fullscreenElement === this.container().nativeElement);
  }

  protected toggleFullscreen(): void {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
      return;
    }
    void this.container().nativeElement.requestFullscreen?.();
  }
}
