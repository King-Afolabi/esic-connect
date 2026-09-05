import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { NotificationService } from '../../core/notifications/notification.service';
import { CalendarSubscriptionsApiService } from './calendar-subscriptions-api.service';
import { CalendarSubscriptionSummary } from './calendar-subscriptions.models';

/**
 * Abonnements iCalendar (EF-INT-001 ; AC-034 — « le flux iCalendar d'un
 * utilisateur ne contient que son propre planning et se révoque »).
 *
 * L'URL complète n'est affichée **qu'une fois**, juste après la
 * création : le serveur ne conserve que l'empreinte du jeton et ne
 * pourra pas la réafficher. L'écran le dit explicitement plutôt que de
 * laisser la personne la chercher plus tard.
 */
@Component({
  selector: 'app-calendar-subscriptions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './calendar-subscriptions.html',
  styleUrl: './calendar-subscriptions.scss',
})
export class CalendarSubscriptions {
  private readonly api = inject(CalendarSubscriptionsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  protected readonly loading = signal(true);
  protected readonly working = signal(false);
  protected readonly subscriptions = signal<CalendarSubscriptionSummary[]>([]);
  protected readonly freshUrl = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({ label: '' });

  constructor() {
    this.load();
  }

  protected create(): void {
    if (this.working()) {
      return;
    }
    this.working.set(true);
    const label = this.form.getRawValue().label.trim();
    this.api.create(label || null).subscribe({
      next: (created) => {
        this.working.set(false);
        this.form.reset({ label: '' });
        // Le chemin est relatif : c'est le client qui connaît son origine.
        this.freshUrl.set(`${window.location.origin}${created.feedPath}`);
        this.load();
      },
      error: (error: unknown) => {
        this.working.set(false);
        const status = (error as { status?: number } | null)?.status;
        this.notifications.error(
          status === 409
            ? "Trop d'abonnements actifs. Révoquez-en un avant d'en créer un autre."
            : "L'abonnement n'a pas pu être créé.",
        );
      },
    });
  }

  protected revoke(subscription: CalendarSubscriptionSummary): void {
    if (this.working() || subscription.revokedAt) {
      return;
    }
    this.working.set(true);
    this.api.revoke(subscription.publicId).subscribe({
      next: () => {
        this.working.set(false);
        this.freshUrl.set(null);
        this.load();
      },
      error: () => {
        this.working.set(false);
        this.notifications.error("L'abonnement n'a pas pu être révoqué.");
      },
    });
  }

  protected async copy(url: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(url);
      this.notifications.info('Lien copié.');
    } catch {
      // Le presse-papiers peut être refusé (contexte non sécurisé,
      // permission) : le lien reste sélectionnable à l'écran.
      this.notifications.error('Copie impossible : sélectionnez le lien manuellement.');
    }
  }

  private load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (rows) => {
        this.subscriptions.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.subscriptions.set([]);
        this.loading.set(false);
      },
    });
  }
}
