import { ChangeDetectionStrategy, Component, input } from '@angular/core';
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
 */
@Component({
  selector: 'app-qr-display',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QRCodeComponent],
  template: `
    @if (value()) {
      <qrcode
        [qrdata]="value()!"
        [width]="256"
        [margin]="2"
        errorCorrectionLevel="M"
        elementType="img"
        alt="QR code d'émargement de la séance"
        cssClass="qr-display__code"
      ></qrcode>
    } @else {
      <p class="qr-display__empty" role="status">
        Aucun QR code disponible. Ouvrez la séance, puis affichez un code.
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
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
}
