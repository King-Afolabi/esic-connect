import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NotificationService } from '../../../../core/notifications/notification.service';
import { ClassAssignmentResponse } from '../../alternation.models';
import { ClassAlternation } from './class-alternation';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const CID = 'c-1';
const ASSIGN_LIST_URL = `/api/v1/alternation/classes/${CID}/assignments`;
const PATTERNS_URL = '/api/v1/alternation/patterns';
const CONTEXT_URL = `/api/v1/alternation/classes/${CID}/context`;
const CREATE_URL = '/api/v1/alternation/class-assignments';

const ASSIGNMENT: ClassAssignmentResponse = {
  publicId: 'a-1',
  classGroupPublicId: CID,
  classGroupCode: 'BTS-SIO-1-A',
  workStudyPatternPublicId: 'p-1',
  workStudyPatternCode: 'RY-3-2',
  workStudyPatternType: 'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY',
  cycleStartDate: '2026-09-07',
  validFrom: '2026-09-07',
  validUntil: null,
  status: 'ACTIVE',
  closeReason: null,
  createdAt: '2026-09-01T10:00:00Z',
  updatedAt: '2026-09-01T10:00:00Z',
};

const notifications = { info: vi.fn(), error: vi.fn() };

async function setup() {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'alternation/classes', component: Stub },
        { path: 'alternation/classes/:classPublicId', component: ClassAlternation },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: notifications },
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/alternation/classes/${CID}`, ClassAlternation);
  harness.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  // Deux appels initiaux : historique + modèles actifs.
  const listReq = http.expectOne((r) => r.url === ASSIGN_LIST_URL);
  const patternsReq = http.expectOne((r) => r.url === PATTERNS_URL);
  patternsReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  return {
    harness,
    http,
    listReq,
    text: () => harness.routeNativeElement?.textContent ?? '',
    component: harness.routeDebugElement?.componentInstance as unknown as ClassAlternationInternals,
  };
}

interface ClassAlternationInternals {
  assignForm: { setValue: (v: Record<string, string>) => void };
  submitAssign: () => void;
  startClose: (a: ClassAssignmentResponse) => void;
  closeForm: { setValue: (v: { reason: string; effectiveDate: string }) => void };
  submitClose: () => void;
  contextForm: { setValue: (v: { date: string }) => void };
  resolveContext: () => void;
  onSortChange: (s: { active: string; direction: 'asc' | 'desc' | '' }) => void;
}

describe('ClassAlternation', () => {
  it('renders the assignment history for the class', async () => {
    const { harness, http, listReq, text } = await setup();
    listReq.flush({ content: [ASSIGNMENT], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();
    expect(text()).toContain('RY-3-2');
    expect(text()).toContain('BTS-SIO-1-A');
    http.verify();
  });

  it('assigns a pattern with the exact body and refreshes the history', async () => {
    const { harness, http, listReq, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    component.assignForm.setValue({
      workStudyPatternPublicId: 'p-1',
      cycleStartDate: '2026-09-07',
      validFrom: '2026-09-07',
      validUntil: '',
    });
    component.submitAssign();

    const req = http.expectOne(CREATE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      classGroupPublicId: CID,
      workStudyPatternPublicId: 'p-1',
      cycleStartDate: '2026-09-07',
      validFrom: '2026-09-07',
      validUntil: null,
    });
    req.flush(ASSIGNMENT);
    // Refresh
    http.expectOne((r) => r.url === ASSIGN_LIST_URL).flush({ content: [ASSIGNMENT], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalled();
    http.verify();
  });

  it('prevents a double assign submit', async () => {
    const { http, listReq, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    component.assignForm.setValue({
      workStudyPatternPublicId: 'p-1',
      cycleStartDate: '2026-09-07',
      validFrom: '2026-09-07',
      validUntil: '',
    });
    component.submitAssign();
    component.submitAssign();
    http.expectOne(CREATE_URL).flush(ASSIGNMENT);
    http.expectOne((r) => r.url === ASSIGN_LIST_URL).flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http.verify();
  });

  it('closes an assignment and surfaces a 409 close conflict message', async () => {
    const { harness, http, listReq, text, component } = await setup();
    listReq.flush({ content: [ASSIGNMENT], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();

    component.startClose(ASSIGNMENT);
    harness.detectChanges();
    component.closeForm.setValue({ reason: 'changement', effectiveDate: '' });
    component.submitClose();

    const req = http.expectOne(`${CREATE_URL}/a-1/close`);
    expect(req.request.body).toEqual({ reason: 'changement', effectiveDate: null });
    req.flush(
      {
        status: 409,
        code: 'ALT_ASSIGNMENT_CLOSE_CONFLICT',
        message: 'Cette date de clôture chevaucherait l’affectation suivante.',
        path: '',
        correlationId: null,
        details: [],
      },
      { status: 409, statusText: 'Conflict' },
    );
    harness.detectChanges();
    expect(text()).toContain('chevaucherait');
    http.verify();
  });

  it('shows the class context returned by the server without recomputing it', async () => {
    const { harness, http, listReq, text, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    component.contextForm.setValue({ date: '2026-09-08' });
    component.resolveContext();
    const req = http.expectOne((r) => r.url === CONTEXT_URL);
    expect(req.request.params.get('date')).toBe('2026-09-08');
    req.flush({
      classGroupPublicId: CID,
      date: '2026-09-08',
      context: 'COMPANY',
      source: 'PATTERN',
      classAssignmentPublicId: 'a-1',
      workStudyPatternPublicId: 'p-1',
      workStudyPatternCode: 'RY-3-2',
      cycleWeekIndex: 1,
      dayOfWeek: 'TUESDAY',
    });
    harness.detectChanges();
    expect(text()).toContain('Entreprise');
    expect(text()).toContain('Rythme affecté à la classe');
    expect(text()).toContain('Mardi');
    http.verify();
  });

  it('only ever sends a whitelisted sort field for the history', async () => {
    const { http, listReq, component } = await setup();
    listReq.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    component.onSortChange({ active: 'validUntil', direction: 'asc' });
    const r1 = http.expectOne((r) => r.url === ASSIGN_LIST_URL);
    expect(r1.request.params.get('sort')).toBe('validUntil,asc');
    r1.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    component.onSortChange({ active: 'closeReason', direction: 'asc' });
    const r2 = http.expectOne((r) => r.url === ASSIGN_LIST_URL);
    expect(r2.request.params.get('sort')).toBe('validFrom,asc');
    r2.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http.verify();
  });
});
