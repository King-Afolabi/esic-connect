import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CalendarSubscriptionSummary,
  CreatedCalendarSubscription,
} from './calendar-subscriptions.models';

/** Accès HTTP aux abonnements iCalendar de l'appelant (EF-INT-001). */
@Injectable({ providedIn: 'root' })
export class CalendarSubscriptionsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/me/calendar-subscriptions`;

  list(): Observable<CalendarSubscriptionSummary[]> {
    return this.http.get<CalendarSubscriptionSummary[]>(this.base);
  }

  create(label: string | null): Observable<CreatedCalendarSubscription> {
    return this.http.post<CreatedCalendarSubscription>(this.base, { label });
  }

  revoke(publicId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${publicId}`);
  }
}
