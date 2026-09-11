import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { ClipboardService } from '../../../core/clipboard/clipboard.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { expectNoAxeViolations } from '../../../../testing/axe';
import {
  BuildingResponse,
  PageResponse,
  RoomResponse,
  SiteResponse,
} from '../organization.models';
import { SiteDetail } from './site-detail';

const ID = 's-1';

const SITE: SiteResponse = {
  publicId: ID,
  code: 'PAR',
  name: 'Campus Paris',
  addressLine1: '1 rue A',
  addressLine2: null,
  postalCode: '75001',
  city: 'Paris',
  countryCode: 'FR',
  timeZoneId: 'Europe/Paris',
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

const BUILDING: BuildingResponse = {
  publicId: 'b-1',
  sitePublicId: ID,
  code: 'A',
  name: 'Bâtiment A',
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

const ROOM: RoomResponse = {
  publicId: 'r-1',
  sitePublicId: ID,
  buildingPublicId: 'b-1',
  code: 'A101',
  name: 'Salle 101',
  capacity: 30,
  floorLabel: '1er étage',
  staticQrIssuedAt: null,
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

interface Internals {
  startEditBuilding: (b: BuildingResponse) => void;
  startEditRoom: (r: RoomResponse) => void;
}

function setup(roles: Role[] = ['ADMIN']) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      { provide: ClipboardService, useValue: { copy: vi.fn().mockResolvedValue(true) } },
      { provide: RoleContextService, useValue: { effectiveRoles } },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ publicId: ID }) } } },
    ],
  });
  const fixture: ComponentFixture<SiteDetail> = TestBed.createComponent(SiteDetail);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http, internals: fixture.componentInstance as unknown as Internals };
}

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur la fiche de site,
 * y compris les formulaires d'édition en ligne de bâtiment et de salle
 * ajoutés cette nuit. Voir `src/testing/axe.ts` pour le périmètre.
 */
describe('SiteDetail — accessibilité (axe-core)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('ne présente aucune violation axe-core avec les formulaires d’édition bâtiment/salle ouverts', () => {
    const { fixture, http, internals } = setup();
    http.expectOne(`/api/v1/sites/${ID}`).flush(SITE);
    fixture.detectChanges();
    http.expectOne((r) => r.url === `/api/v1/sites/${ID}/buildings`).flush(page([BUILDING]));
    http.expectOne((r) => r.url === `/api/v1/sites/${ID}/rooms`).flush(page([ROOM]));
    fixture.detectChanges();

    internals.startEditBuilding(BUILDING);
    internals.startEditRoom(ROOM);
    fixture.detectChanges();

    return expectNoAxeViolations(fixture.nativeElement);
  });
});
