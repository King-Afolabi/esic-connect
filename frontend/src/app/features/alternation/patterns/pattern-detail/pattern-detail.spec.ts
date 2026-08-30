import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthService } from '../../../../core/auth/auth.service';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { WorkStudyPatternResponse } from '../../alternation.models';
import { PatternDetail } from './pattern-detail';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const ID = '2f1a9b7c-0000-4000-8000-000000000000';
const URL = `/api/v1/alternation/patterns/${ID}`;

const PATTERN: WorkStudyPatternResponse = {
  publicId: ID,
  code: 'RY-CUSTOM',
  name: 'Rythme personnalisé',
  description: 'Deux semaines à l’école',
  type: 'CUSTOM',
  cycleLengthWeeks: 4,
  configuration: {
    cycleLengthWeeks: 4,
    schoolWeeks: [1, 2],
    companyWeeks: [3, 4],
    schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    companyDays: [],
  },
  status: 'ACTIVE',
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-02T10:00:00Z',
};

const notifications = { info: vi.fn(), error: vi.fn() };

async function setup(canWrite = true) {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'alternation/patterns', component: Stub },
        { path: 'alternation/patterns/:publicId', component: PatternDetail },
        { path: 'alternation/patterns/:publicId/edit', component: Stub },
        { path: 'dashboard', component: Stub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { hasAnyRole: () => canWrite } },
      { provide: NotificationService, useValue: notifications },
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/alternation/patterns/${ID}`, PatternDetail);
  harness.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  return {
    harness,
    http,
    text: () => harness.routeNativeElement?.textContent ?? '',
    req: (): TestRequest => http.expectOne(URL),
  };
}

describe('PatternDetail', () => {
  it('loads and shows the facts plus the cycle preview', async () => {
    const { harness, http, text, req } = await setup();
    expect(text()).toContain('Chargement du modèle');
    req().flush(PATTERN);
    harness.detectChanges();
    expect(text()).toContain('RY-CUSTOM');
    expect(text()).toContain('Personnalisé');
    expect(text()).toContain('Deux semaines à l’école');
    // Cycle preview rendered (4 week rows).
    expect(harness.routeNativeElement?.querySelectorAll('tbody tr').length).toBe(4);
    http.verify();
  });

  it('renders a not-found panel on a 404', async () => {
    const { harness, http, text, req } = await setup();
    req().flush(
      { status: 404, code: 'ALT_PATTERN_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();
    expect(text()).toContain('Aucun modèle de rythme ne correspond');
    http.verify();
  });

  it('hides write actions for a read-only role', async () => {
    const { harness, http, text, req } = await setup(false);
    req().flush(PATTERN);
    harness.detectChanges();
    expect(text()).not.toContain('Archiver');
    expect(harness.routeNativeElement?.querySelector('a[href^="/alternation/patterns/2f1a9b7c"][href$="/edit"]')).toBeNull();
    http.verify();
  });

  it('archives with a mandatory reason and reloads', async () => {
    const { harness, http, text, req } = await setup(true);
    req().flush(PATTERN);
    harness.detectChanges();

    const internals = harness.routeDebugElement?.componentInstance as unknown as {
      startArchive: () => void;
      archiveForm: { setValue: (v: { reason: string }) => void };
      confirmArchive: () => void;
    };
    internals.startArchive();
    harness.detectChanges();
    internals.archiveForm.setValue({ reason: 'obsolète' });
    internals.confirmArchive();

    const archiveReq = http.expectOne(`${URL}/archive`);
    expect(archiveReq.request.method).toBe('POST');
    expect(archiveReq.request.body).toEqual({ reason: 'obsolète' });
    archiveReq.flush(null, { status: 204, statusText: 'No Content' });

    // Reloads the pattern afterwards.
    req().flush({ ...PATTERN, status: 'ARCHIVED', archiveReason: 'obsolète' });
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalled();
    expect(text()).toContain('Restaurer');
    http.verify();
  });

  it('restores an archived pattern', async () => {
    const { harness, http, req } = await setup(true);
    req().flush({ ...PATTERN, status: 'ARCHIVED', archiveReason: 'obsolète' });
    harness.detectChanges();

    const internals = harness.routeDebugElement?.componentInstance as unknown as {
      startRestore: () => void;
      confirmRestore: () => void;
    };
    internals.startRestore();
    harness.detectChanges();
    internals.confirmRestore();

    const restoreReq = http.expectOne(`${URL}/restore`);
    expect(restoreReq.request.method).toBe('POST');
    restoreReq.flush(null, { status: 204, statusText: 'No Content' });
    req().flush(PATTERN);
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalled();
    http.verify();
  });
});
