import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NotificationService } from '../../../../core/notifications/notification.service';
import { StudentExceptionResponse } from '../../alternation.models';
import { EnrollmentAlternation } from './enrollment-alternation';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const EID = 'e-1';
const LIST_URL = `/api/v1/alternation/enrollments/${EID}/exceptions`;
const CREATE_URL = '/api/v1/alternation/student-exceptions';
const CONTEXT_URL = `/api/v1/alternation/enrollments/${EID}/context`;

const EXCEPTION: StudentExceptionResponse = {
  publicId: 'x-1',
  enrollmentPublicId: EID,
  studentProfilePublicId: 'sp-1',
  classGroupPublicId: 'c-1',
  type: 'COMPANY_PERIOD',
  startAt: '2026-09-07T06:00:00.000Z',
  endAt: '2026-09-11T06:00:00.000Z',
  timeZoneId: 'Europe/Paris',
  reason: 'stage entreprise',
  status: 'ACTIVE',
  cancelReason: null,
  createdAt: '2026-09-01T08:00:00Z',
  updatedAt: '2026-09-01T08:00:00Z',
};

const notifications = { info: vi.fn(), error: vi.fn() };

interface Internals {
  createForm: {
    setValue: (v: {
      type: string;
      startWall: string;
      endWall: string;
      timeZoneId: string;
      reason: string;
    }) => void;
    controls: { timeZoneId: { setValue: (v: string) => void } };
  };
  submitCreate: () => void;
  createBlockingMessage: () => string | null;
  computedInstants: () => { startAt: string | null; endAt: string | null };
  startCancel: (e: StudentExceptionResponse) => void;
  cancelForm: { setValue: (v: { reason: string }) => void };
  submitCancel: () => void;
  contextForm: { setValue: (v: { date: string }) => void };
  resolveContext: () => void;
  onSortChange: (s: { active: string; direction: 'asc' | 'desc' | '' }) => void;
}

async function setup() {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'alternation/enrollments', component: Stub },
        { path: 'alternation/enrollments/:enrollmentPublicId', component: EnrollmentAlternation },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: notifications },
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/alternation/enrollments/${EID}`, EnrollmentAlternation);
  harness.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  const listReq = http.expectOne((r) => r.url === LIST_URL);
  return {
    harness,
    http,
    listReq,
    text: () => harness.routeNativeElement?.textContent ?? '',
    component: harness.routeDebugElement?.componentInstance as unknown as Internals,
  };
}

describe('EnrollmentAlternation', () => {
  it('states the [start, end) semantics and lists the exceptions', async () => {
    const { harness, http, listReq, text } = await setup();
    expect(text()).toContain('[début, fin)');
    listReq.flush({ content: [EXCEPTION], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();
    expect(text()).toContain('Période en entreprise');
    expect(text()).toContain('stage entreprise');
    expect(text()).toContain('Europe/Paris');
    http.verify();
  });

  it('encodes the wall time in the chosen zone and POSTs the exact body', async () => {
    const { harness, http, listReq, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    component.createForm.setValue({
      type: 'COMPANY_PERIOD',
      startWall: '2026-09-07T08:00',
      endWall: '2026-09-11T08:00',
      timeZoneId: 'Europe/Paris',
      reason: 'stage',
    });
    harness.detectChanges();
    // Europe/Paris is UTC+2 in September (DST) → 08:00 local = 06:00Z.
    expect(component.computedInstants().startAt).toBe('2026-09-07T06:00:00.000Z');
    component.submitCreate();

    const req = http.expectOne(CREATE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      enrollmentPublicId: EID,
      type: 'COMPANY_PERIOD',
      startAt: '2026-09-07T06:00:00.000Z',
      endAt: '2026-09-11T06:00:00.000Z',
      timeZoneId: 'Europe/Paris',
      reason: 'stage',
    });
    req.flush(EXCEPTION);
    http.expectOne((r) => r.url === LIST_URL).flush({ content: [EXCEPTION], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalled();
    http.verify();
  });

  it('blocks submission when end is not after start', async () => {
    const { harness, http, listReq, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();
    component.createForm.setValue({
      type: 'ON_SITE_REQUIRED',
      startWall: '2026-09-11T08:00',
      endWall: '2026-09-07T08:00',
      timeZoneId: 'Europe/Paris',
      reason: 'x',
    });
    harness.detectChanges();
    expect(component.createBlockingMessage()).toContain('postérieure au début');
    component.submitCreate();
    http.expectNone(CREATE_URL);
    http.verify();
  });

  it('blocks submission and never falls back to UTC for an unknown zone', async () => {
    const { harness, http, listReq, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();
    component.createForm.setValue({
      type: 'REMOTE_ALLOWED',
      startWall: '2026-09-07T08:00',
      endWall: '2026-09-08T08:00',
      timeZoneId: 'Europe/Paris',
      reason: 'x',
    });
    component.createForm.controls.timeZoneId.setValue('Mars/Olympus');
    harness.detectChanges();
    expect(component.createBlockingMessage()).toContain('Fuseau horaire inconnu');
    expect(component.computedInstants().startAt).toBeNull();
    component.submitCreate();
    http.expectNone(CREATE_URL);
    http.verify();
  });

  it('cancels an active exception with a mandatory reason', async () => {
    const { harness, http, listReq, component } = await setup();
    listReq.flush({ content: [EXCEPTION], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();
    component.startCancel(EXCEPTION);
    harness.detectChanges();
    component.cancelForm.setValue({ reason: 'saisie erronée' });
    component.submitCancel();
    const req = http.expectOne(`${CREATE_URL}/x-1/cancel`);
    expect(req.request.body).toEqual({ reason: 'saisie erronée' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne((r) => r.url === LIST_URL).flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalled();
    http.verify();
  });

  it('shows the effective enrollment context from the server without recomputing it', async () => {
    const { harness, http, listReq, text, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();
    component.contextForm.setValue({ date: '2026-09-08' });
    component.resolveContext();
    const req = http.expectOne((r) => r.url === CONTEXT_URL);
    expect(req.request.params.get('date')).toBe('2026-09-08');
    req.flush({
      enrollmentPublicId: EID,
      classGroupPublicId: 'c-1',
      date: '2026-09-08',
      patternContext: 'SCHOOL',
      effectiveContext: 'COMPANY',
      source: 'INDIVIDUAL_EXCEPTION',
      coveringExceptionTypes: ['COMPANY_PERIOD'],
    });
    harness.detectChanges();
    expect(text()).toContain('Contexte du rythme');
    expect(text()).toContain('Exception individuelle');
    expect(text()).toContain('Période en entreprise');
    http.verify();
  });

  it('only ever sends a whitelisted sort field', async () => {
    const { http, listReq, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    component.onSortChange({ active: 'endAt', direction: 'asc' });
    const r1 = http.expectOne((r) => r.url === LIST_URL);
    expect(r1.request.params.get('sort')).toBe('endAt,asc');
    r1.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    component.onSortChange({ active: 'reason', direction: 'asc' });
    const r2 = http.expectOne((r) => r.url === LIST_URL);
    expect(r2.request.params.get('sort')).toBe('startAt,asc');
    r2.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http.verify();
  });
});
