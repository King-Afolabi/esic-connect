import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { NotificationsApiService } from '../notifications-api.service';
import { NotificationsBadgeService } from '../notifications-badge.service';
import { toNotificationError } from '../notification-errors';
import {
  NotificationStatus,
  NotificationView,
  formatInstantUtc,
  notificationTypeLabel,
} from '../notifications.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; items: NotificationView[]; page: number; totalPages: number; totalElements: number };

const PAGE_SIZE = 20;

/**
 * Centre de notifications de l'appelant (G1-D ; EF-NOTIF-001) : liste
 * paginée, filtre « Toutes / Non lues », marquage lu et « tout marquer
 * comme lu ». États `loading` / `empty` / `error` + reprise. Aucune
 * autorisation fondée sur le front — Spring Security reste l'autorité,
 * l'isolation par destinataire est faite côté serveur.
 */
@Component({
  selector: 'app-notification-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './notification-list.html',
  styleUrl: './notification-list.scss',
})
export class NotificationList {
  private readonly api = inject(NotificationsApiService);
  private readonly badge = inject(NotificationsBadgeService);
  private readonly toasts = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly typeLabel = notificationTypeLabel;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly filter = signal<'ALL' | 'UNREAD'>('ALL');
  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly busy = signal(false);

  protected readonly items = computed<NotificationView[]>(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.items : [];
  });
  protected readonly page = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.page : 0;
  });
  protected readonly totalPages = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.totalPages : 0;
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });
  protected readonly hasUnread = computed(() => this.items().some((n) => n.status === 'UNREAD'));

  constructor() {
    this.load(0);
  }

  protected setFilter(value: 'ALL' | 'UNREAD'): void {
    if (this.filter() === value) {
      return;
    }
    this.filter.set(value);
    this.load(0);
  }

  protected retry(): void {
    this.load(this.page());
  }

  protected goToPage(delta: number): void {
    const next = this.page() + delta;
    if (next < 0 || next >= this.totalPages()) {
      return;
    }
    this.load(next);
  }

  protected markRead(item: NotificationView): void {
    if (this.busy() || item.status !== 'UNREAD') {
      return;
    }
    this.busy.set(true);
    this.api
      .markRead(item.publicId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.badge.refresh();
          this.load(this.page());
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.toasts.error(toNotificationError(error).message);
        },
      });
  }

  protected markAllRead(): void {
    if (this.busy() || !this.hasUnread()) {
      return;
    }
    this.busy.set(true);
    this.api
      .markAllRead()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.badge.refresh();
          this.toasts.info('Toutes les notifications sont marquées comme lues.');
          this.load(0);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.toasts.error(toNotificationError(error).message);
        },
      });
  }

  private load(page: number): void {
    this.state.set({ kind: 'loading' });
    const status: NotificationStatus | null = this.filter() === 'UNREAD' ? 'UNREAD' : null;
    this.api
      .list({ status, page, size: PAGE_SIZE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.state.set({
            kind: 'ready',
            items: result.content,
            page: result.page,
            totalPages: result.totalPages,
            totalElements: result.totalElements,
          });
          this.badge.refresh();
        },
        error: (error: unknown) =>
          this.state.set({ kind: 'error', message: toNotificationError(error).message }),
      });
  }
}
