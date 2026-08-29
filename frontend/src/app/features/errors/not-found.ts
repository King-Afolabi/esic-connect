import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButtonModule],
  template: `
    <main class="error-page" id="main-content">
      <h1>Page introuvable</h1>
      <p>L'adresse demandée n'existe pas ou a été déplacée.</p>
      <a mat-flat-button color="primary" routerLink="/dashboard">Retour au tableau de bord</a>
    </main>
  `,
  styles: `
    .error-page {
      max-width: 32rem;
      margin: 4rem auto;
      padding: 0 1.5rem;
      text-align: center;
    }
    h1 {
      font: var(--mat-sys-headline-medium);
    }
    p {
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
      margin-bottom: 1.5rem;
    }
  `,
})
export class NotFound {}
