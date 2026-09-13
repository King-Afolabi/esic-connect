import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PlanningVersions } from './planning-versions';

const CLASSES_URL = '/api/v1/class-groups';
const VERSIONS_URL = '/api/v1/planning/versions';

interface Internals {
  form: { controls: { classGroupPublicId: { setValue: (v: string) => void } } };
  toggleDetail: (version: { publicId: string }) => void;
}

function classesPage() {
  return {
    content: [
      {
        publicId: 'c-1',
        code: 'C1',
        name: 'Classe 1',
        promotionPublicId: 'p',
        programLevelPublicId: 'l',
        sitePublicId: null,
        capacity: null,
        status: 'ACTIVE',
        archivedAt: null,
        archiveReason: null,
        createdAt: '',
        updatedAt: '',
      },
    ],
    page: 0,
    size: 100,
    totalElements: 1,
    totalPages: 1,
  };
}

function versionsPage() {
  return {
    content: [
      {
        publicId: 'v-1',
        schedulePublicId: 's-1',
        classGroupPublicId: 'c-1',
        academicYearPublicId: 'ay-1',
        versionNumber: 1,
        status: 'ACTIVE',
        entryCount: 1,
        changeSummary: null,
        replacedByVersionPublicId: null,
        publishedAt: '2026-09-01T08:00:00Z',
        createdAt: '2026-09-01T08:00:00Z',
      },
    ],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
  };
}

function versionDetail() {
  return {
    version: versionsPage().content[0],
    entries: [
      {
        publicId: 'e-1',
        slotPublicId: 'slot-1',
        slotKey: 'LUN-08',
        title: 'Anglais',
        startsAt: '2026-09-07T06:00:00Z',
        endsAt: '2026-09-07T08:00:00Z',
        timeZoneId: 'Europe/Paris',
        roomCode: 'A101',
        sessionPublicId: null,
      },
    ],
  };
}

function setup() {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(PlanningVersions);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as Internals;
  fixture.detectChanges();
  return { fixture, http, internals };
}

describe('PlanningVersions', () => {
  let fixture: ComponentFixture<PlanningVersions>;
  let http: HttpTestingController;
  let internals: Internals;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('converts an entry window to its declared zone, never the raw UTC value (Lot 8)', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne((r) => r.url === CLASSES_URL).flush(classesPage());
    fixture.detectChanges();

    internals.form.controls.classGroupPublicId.setValue('c-1');
    http.expectOne((r) => r.url === VERSIONS_URL).flush(versionsPage());
    fixture.detectChanges();

    internals.toggleDetail({ publicId: 'v-1' });
    http.expectOne((r) => r.url === `${VERSIONS_URL}/v-1`).flush(versionDetail());
    fixture.detectChanges();

    // 06:00Z / 08:00Z converted to Europe/Paris (DST, UTC+2) → 08:00 / 10:00 local.
    expect(text()).toContain('08:00 (Europe/Paris)');
    expect(text()).toContain('10:00 (Europe/Paris)');
    expect(text()).not.toContain('06:00 (Europe/Paris)');
  });

  it('keeps the publication timestamp in UTC — a technical stamp, not a business time (Lot 8)', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne((r) => r.url === CLASSES_URL).flush(classesPage());
    fixture.detectChanges();

    internals.form.controls.classGroupPublicId.setValue('c-1');
    http.expectOne((r) => r.url === VERSIONS_URL).flush(versionsPage());
    fixture.detectChanges();

    expect(text()).toContain('01/09/2026 08:00');
  });
});
