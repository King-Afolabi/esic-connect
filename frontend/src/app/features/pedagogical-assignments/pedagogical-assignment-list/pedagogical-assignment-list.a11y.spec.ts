import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { expectNoAxeViolations } from '../../../../testing/axe';
import { NotificationService } from '../../../core/notifications/notification.service';
import { PedagogicalAssignmentList } from './pedagogical-assignment-list';

/**
 * Garde-fou d'accessibilité automatisé sur l'écran des responsables
 * pédagogiques — liste peuplée, cas le plus représentatif (tableau +
 * en-tête d'actions).
 */
describe('PedagogicalAssignmentList — accessibilité (axe-core)', () => {
  it('ne présente aucune violation axe-core avec une liste peuplée', async () => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      ],
    });

    const fixture = TestBed.createComponent(PedagogicalAssignmentList);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    http.expectOne((r) => r.url === '/api/v1/pedagogical-assignments').flush({
      content: [
        {
          publicId: 'pa-1',
          programPublicId: 'prg-1',
          programCode: 'BTS-CIEL',
          userPublicId: 'u-1',
          type: 'PRIMARY_MANAGER',
          status: 'ACTIVE',
          validFrom: '2026-09-01',
          validUntil: null,
          reason: null,
          closeReason: null,
          createdAt: '2026-09-01T00:00:00Z',
          updatedAt: '2026-09-01T00:00:00Z',
        },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne((r) => r.url === '/api/v1/programs').flush({
      content: [
        { publicId: 'prg-1', code: 'BTS-CIEL', name: 'BTS CIEL', programType: 'BTS', description: null, status: 'ACTIVE', archivedAt: null, archiveReason: null, createdAt: '', updatedAt: '' },
      ],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/users/u-1').flush({
      publicId: 'u-1',
      email: 'manager@esic.test',
      firstName: 'Fatou',
      lastName: 'Diallo',
      phone: null,
      status: 'ACTIVE',
      emailVerifiedAt: null,
      lastLoginAt: null,
      suspendedAt: null,
      suspensionReason: null,
      archivedAt: null,
      createdAt: '',
      updatedAt: '',
      roleAssignments: [],
    });
    fixture.detectChanges();

    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
    http.verify();
  });
});
