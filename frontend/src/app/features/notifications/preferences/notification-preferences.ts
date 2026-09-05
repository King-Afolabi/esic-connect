import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { NotificationService } from '../../../core/notifications/notification.service';
import {
  NotificationPreference,
  NotificationSettingsApiService,
  PushStatus,
} from '../notification-settings-api.service';

/** Ordre d'affichage stable des catégories, du plus fréquent au plus rare. */
const CATEGORY_ORDER = [
  'PLANNING',
  'SESSION',
  'ATTENDANCE',
  'JUSTIFICATION',
  'CLAIM',
  'SECURITY',
] as const;

const CATEGORY_LABELS: Readonly<Record<string, string>> = {
  PLANNING: 'Planning',
  SESSION: 'Séances',
  ATTENDANCE: 'Assiduité',
  JUSTIFICATION: 'Justificatifs',
  CLAIM: 'Réclamations',
  SECURITY: 'Sécurité du compte',
};

const CHANNEL_LABELS: Readonly<Record<string, string>> = {
  IN_APP: 'Application',
  EMAIL: 'Courriel',
  PUSH: 'Notification poussée',
};

interface CategoryRow {
  readonly category: string;
  readonly label: string;
  readonly channels: readonly NotificationPreference[];
}

type State = { kind: 'loading' } | { kind: 'error'; message: string } | { kind: 'ready' };

/**
 * Préférences de notification de l'utilisateur (EF-NOTIF-006 ; docs/02
 * §21.6).
 *
 * <p><strong>Ce que cet écran refuse de faire.</strong> Il n'affiche
 * jamais un interrupteur qui n'aurait aucun effet. Un réglage verrouillé
 * — le centre de notifications, les alertes de sécurité — est montré
 * coché et désactivé, avec sa raison. Et lorsque la poussée n'est pas
 * configurée sur le serveur, l'écran le dit explicitement au lieu de
 * laisser croire qu'un abonnement suffirait.
 */
@Component({
  selector: 'app-notification-preferences',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, MatSlideToggleModule],
  templateUrl: './notification-preferences.html',
  styleUrl: './notification-preferences.scss',
})
export class NotificationPreferences {
  private readonly api = inject(NotificationSettingsApiService);
  private readonly toasts = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly saving = signal<string | null>(null);
  protected readonly push = signal<PushStatus | null>(null);
  protected readonly pushBusy = signal(false);

  private readonly preferences = signal<readonly NotificationPreference[]>([]);

  protected readonly channelLabel = (channel: string): string =>
    CHANNEL_LABELS[channel] ?? channel;

  /** Une ligne par catégorie, canaux ordonnés Application / Courriel / Poussée. */
  protected readonly rows = computed<readonly CategoryRow[]>(() => {
    const all = this.preferences();
    return CATEGORY_ORDER.filter((category) => all.some((p) => p.category === category)).map(
      (category) => ({
        category,
        label: CATEGORY_LABELS[category] ?? category,
        channels: ['IN_APP', 'EMAIL', 'PUSH']
          .map((channel) => all.find((p) => p.category === category && p.channel === channel))
          .filter((p): p is NotificationPreference => p !== undefined),
      }),
    );
  });

  /** Le navigateur sait-il faire de la poussée ? Indépendant du serveur. */
  protected readonly pushSupported = signal(
    typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window,
  );

  constructor() {
    this.load();
    this.loadPushStatus();
  }

  protected load(): void {
    this.state.set({ kind: 'loading' });
    this.api
      .preferences()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.preferences.set(list.preferences);
          this.state.set({ kind: 'ready' });
        },
        error: () =>
          this.state.set({
            kind: 'error',
            message: 'Vos préférences n’ont pas pu être chargées.',
          }),
      });
  }

  protected toggle(preference: NotificationPreference, enabled: boolean): void {
    if (preference.locked) {
      return;
    }
    const key = `${preference.category}:${preference.channel}`;
    this.saving.set(key);
    this.api
      .updatePreference(preference.category, preference.channel, enabled)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.preferences.set(list.preferences);
          this.saving.set(null);
        },
        error: () => {
          this.saving.set(null);
          // Le serveur reste l'autorité : on recharge plutôt que de
          // laisser l'écran afficher un état que le serveur n'a pas retenu.
          this.toasts.error('Ce réglage n’a pas pu être enregistré.');
          this.load();
        },
      });
  }

  protected trackRow = (_: number, row: CategoryRow): string => row.category;
  protected trackChannel = (_: number, p: NotificationPreference): string => p.channel;

  private loadPushStatus(): void {
    this.api
      .pushStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => this.push.set(status),
        error: () => this.push.set(null),
      });
  }
}
