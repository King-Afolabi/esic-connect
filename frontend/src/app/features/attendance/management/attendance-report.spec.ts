import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { AttendanceApiService } from '../attendance-api.service';
import { SessionReportRow } from '../attendance.models';
import { AttendanceReport } from './attendance-report';

function sessionRow(): SessionReportRow {
  return {
    sessionPublicId: 's-1',
    sessionTitle: 'Anglais',
    startsAt: '2026-09-07T06:00:00Z',
    endsAt: '2026-09-07T08:00:00Z',
    timeZoneId: 'Europe/Paris',
    classCodes: 'C1',
    teacherName: 'Alice Martin',
    checkpointCount: 1,
    expectedCount: 10,
    presentCount: 8,
    lateCount: 1,
    absentCount: 1,
    excusedCount: 0,
    attendanceRate: 0.8,
  };
}

function setup(roles: Role[] = ['ADMIN']) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  const sessionsReport = vi.fn().mockReturnValue(
    of({ content: [sessionRow()], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
  );
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles } },
      { provide: AttendanceApiService, useValue: { sessionsReport } },
      { provide: ActivatedRoute, useValue: { snapshot: { data: { kind: 'sessions' } } } },
    ],
  });
  const fixture = TestBed.createComponent(AttendanceReport);
  fixture.detectChanges();
  return { fixture };
}

describe('AttendanceReport', () => {
  let fixture: ComponentFixture<AttendanceReport>;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('converts a session row start time to its declared zone, never the raw UTC value (Lot 8)', () => {
    ({ fixture } = setup());
    expect(text()).toContain('Anglais');
    // 06:00Z converted to Europe/Paris (DST, UTC+2) → 08:00 local.
    expect(text()).toContain('08:00 (Europe/Paris)');
  });
});
