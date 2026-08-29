import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/**
 * Retours utilisateur transitoires (bandeaux Material).
 *
 * Ne jamais y faire transiter de secret, de jeton ni de donnée
 * personnelle superflue (docs/07-securite-rgpd.md §14).
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  error(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 8000,
      politeness: 'assertive',
      panelClass: 'app-snackbar-error',
    });
  }

  info(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 5000,
      politeness: 'polite',
    });
  }
}
