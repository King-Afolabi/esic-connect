import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { AttendanceApiService } from '../attendance-api.service';
import { JustificationResponse } from '../attendance.models';
import { JustificationQueue } from './justification-queue';

function justification(): JustificationResponse {
  return {
    publicId: 'j-1',
    status: 'PENDING',
    category: 'MEDICAL',
    externalReference: null,
    comment: 'certificat',
    submittedAt: '2026-09-10T09:00:00Z',
    reviewedAt: null,
    decisionReason: null,
    sessionPublicId: 's-1',
    sessionTitle: 'Atelier',
    sessionStartsAt: '2026-09-10T08:00:00Z',
    timeZoneId: 'Europe/Paris',
    checkpointPublicId: 'cp-1',
    checkpointLabel: 'Arrivée',
    classCode: 'C1',
    studentUserPublicId: 'u-1',
    studentNumber: 'E12345',
    firstName: 'Awa',
    lastName: 'Diop',
    attendanceStatus: 'ABSENT',
  };
}

function setup(roles: Role[] = ['PEDAGOGICAL_MANAGER']) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  const listJustificationsForReview = vi.fn().mockReturnValue(of([justification()]));
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles } },
      { provide: AttendanceApiService, useValue: { listJustificationsForReview } },
    ],
  });
  const fixture = TestBed.createComponent(JustificationQueue);
  fixture.detectChanges();
  return { fixture };
}

describe('JustificationQueue', () => {
  let fixture: ComponentFixture<JustificationQueue>;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('converts the session start time to its declared zone, never the raw UTC value (Lot 8)', () => {
    ({ fixture } = setup());
    expect(text()).toContain('Atelier');
    // 08:00Z converted to Europe/Paris (DST, UTC+2) → 10:00 local.
    expect(text()).toContain('10:00 (Europe/Paris)');
  });
});
