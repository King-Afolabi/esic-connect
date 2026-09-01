import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { PlanningJobResponse, PlanningRowResponse } from '../planning.models';
import { PlanningImportReview } from './planning-import-review';

const JOB_ID = 'job-1';
const JOB_URL = `/api/v1/planning-imports/${JOB_ID}`;
const notifications = { info: vi.fn(), error: vi.fn() };

const SIMULATED_JOB: PlanningJobResponse = {
  publicId: JOB_ID,
  status: 'SIMULATED',
  classGroupPublicId: 'c-1',
  academicYearPublicId: 'y-1',
  originalFileName: 'planning.csv',
  fileSizeBytes: 120,
  csvSeparator: ',',
  totalRows: 2,
  validRows: 2,
  warningRows: 0,
  errorRows: 0,
  addedRows: 2,
  modifiedRows: 0,
  unchangedRows: 0,
  removedEntries: 0,
  confirmable: true,
  simulatedAt: '2026-09-01T10:00:00Z',
  expiresAt: '2026-09-08T10:00:00Z',
  publishedAt: null,
  publishedVersionPublicId: null,
  failureReason: null,
  createdAt: '2026-09-01T10:00:00Z',
};

const ROW: PlanningRowResponse = {
  publicId: 'r-1',
  rowNumber: 2,
  slotKey: 'S1',
  sessionDate: '2026-09-07',
  startTime: '09:00',
  endTime: '12:00',
  timeZoneId: 'Europe/Paris',
  title: 'Algorithmique',
  teacherPublicId: 't-1',
  roomCode: 'A101',
  rowStatus: 'VALID',
  plannedAction: 'ADDED',
  resolvedStartsAt: '2026-09-07T07:00:00Z',
  resolvedEndsAt: '2026-09-07T10:00:00Z',
  issues: [],
};

function rowsPage(content: PlanningRowResponse[]) {
  return { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 };
}

interface Internals {
  startPublish: () => void;
  confirmPublish: () => void;
  cancelJob: () => void;
  actionError: () => string | null;
}

function setup() {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: notifications },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ jobId: JOB_ID }) } },
      },
    ],
  });
  const fixture: ComponentFixture<PlanningImportReview> = TestBed.createComponent(PlanningImportReview);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as Internals;
  fixture.detectChanges();
  return {
    fixture,
    http,
    internals,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    flushJob: (job: PlanningJobResponse = SIMULATED_JOB, rows: PlanningRowResponse[] = [ROW]) => {
      http.expectOne(JOB_URL).flush(job);
      fixture.detectChanges();
      http.expectOne((r) => r.url === `${JOB_URL}/rows`).flush(rowsPage(rows));
      fixture.detectChanges();
    },
  };
}

describe('PlanningImportReview', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows the job summary and its rows', () => {
    const s = setup();
    s.flushJob();
    expect(s.text()).toContain('planning.csv');
    expect(s.text()).toContain('Algorithmique');
    expect(s.text()).toContain('Publier le planning');
  });

  it('publishes after an inline confirmation and reloads the job', () => {
    const s = setup();
    s.flushJob();
    s.internals.startPublish();
    s.fixture.detectChanges();
    s.internals.confirmPublish();

    const req = s.http.expectOne(`${JOB_URL}/publish`);
    expect(req.request.method).toBe('POST');
    req.flush({ jobPublicId: JOB_ID, versionPublicId: 'v-1', versionNumber: 1, alreadyPublished: false });

    // Reload chain.
    s.flushJob({ ...SIMULATED_JOB, status: 'PUBLISHED', publishedVersionPublicId: 'v-1' });
    expect(notifications.info).toHaveBeenCalled();
    expect(s.text()).toContain('Ce planning est publié');
  });

  it('treats a concurrent idempotent publish (200 + alreadyPublished) as success, not an error', () => {
    const s = setup();
    s.flushJob();
    s.internals.startPublish();
    s.fixture.detectChanges();
    s.internals.confirmPublish();

    // Course concurrente perdue côté serveur → 200 avec alreadyPublished=true
    // (audit G1-B.1 : jamais 409/FAILED pour une course idempotente).
    s.http
      .expectOne(`${JOB_URL}/publish`)
      .flush({ jobPublicId: JOB_ID, versionPublicId: 'v-1', versionNumber: 1, alreadyPublished: true });

    s.flushJob({ ...SIMULATED_JOB, status: 'PUBLISHED', publishedVersionPublicId: 'v-1' });
    expect(notifications.info).toHaveBeenCalledWith(expect.stringContaining('déjà publié'));
    expect(notifications.error).not.toHaveBeenCalled();
    expect(s.internals.actionError()).toBeFalsy();
  });

  it('renders a 409 blocking error from publish without leaving a confirm panel dangling', () => {
    const s = setup();
    s.flushJob();
    s.internals.startPublish();
    s.fixture.detectChanges();
    s.internals.confirmPublish();
    s.http.expectOne(`${JOB_URL}/publish`).flush(
      { status: 409, code: 'PLAN_BLOCKING_ISSUES', message: 'Une ligne est en anomalie.', path: '', correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );
    s.fixture.detectChanges();
    expect(s.internals.actionError()).toContain('anomalie');
  });

  it('hides the publish action when the job is not confirmable', () => {
    const s = setup();
    s.flushJob({ ...SIMULATED_JOB, confirmable: false, errorRows: 1, validRows: 1 }, [
      { ...ROW, rowStatus: 'ERROR', plannedAction: 'CONFLICT', issues: [{ severity: 'ERROR', errorCode: 'PLAN_CONFLICT_CLASS', columnName: null, receivedValue: null, message: 'Chevauchement.' }] },
    ]);
    expect(s.text()).not.toContain('Publier le planning');
    expect(s.text()).toContain('La publication est bloquée');
    expect(s.text()).toContain('Chevauchement.');
  });

  it('shows a not-found panel on a 404', () => {
    const s = setup();
    s.http.expectOne(JOB_URL).flush(
      { status: 404, code: 'PLAN_JOB_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    s.fixture.detectChanges();
    expect(s.text()).toContain('Aucun import de planning ne correspond');
  });
});
