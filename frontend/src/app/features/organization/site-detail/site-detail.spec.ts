import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import {
  BuildingResponse,
  PageResponse,
  RoomResponse,
  SiteNetworkRangeResponse,
  SiteResponse,
} from '../organization.models';
import { SiteDetail } from './site-detail';

const ID = 's-1';
const notifications = { info: vi.fn(), error: vi.fn() };

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

function bpage(content: BuildingResponse[]): PageResponse<BuildingResponse> {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}
function rpage(content: RoomResponse[]): PageResponse<RoomResponse> {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}
function npage(content: SiteNetworkRangeResponse[]): PageResponse<SiteNetworkRangeResponse> {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

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

interface Internals {
  siteReasonForm: { controls: { reason: { setValue: (v: string) => void } } };
  buildingForm: { controls: { code: { setValue: (v: string) => void }; name: { setValue: (v: string) => void } } };
  startSiteAction: (k: 'archive' | 'restore') => void;
  confirmSiteAction: () => void;
  submitBuilding: () => void;
}

function setup(roles: Role[] = ['ADMIN']) {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  notifications.error.mockReset();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: notifications },
      { provide: RoleContextService, useValue: { effectiveRoles } },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ publicId: ID }) } },
      },
    ],
  });
  const fixture: ComponentFixture<SiteDetail> = TestBed.createComponent(SiteDetail);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as Internals;
  fixture.detectChanges();
  return {
    fixture,
    http,
    internals,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    flushSite: (site: SiteResponse = SITE) => {
      http.expectOne(`/api/v1/sites/${ID}`).flush(site);
      fixture.detectChanges();
    },
    flushChildren: (buildings: BuildingResponse[] = [], rooms: RoomResponse[] = []) => {
      http.expectOne((r) => r.url === `/api/v1/sites/${ID}/buildings`).flush(bpage(buildings));
      http.expectOne((r) => r.url === `/api/v1/sites/${ID}/rooms`).flush(rpage(rooms));
      fixture.detectChanges();
    },
    expectRanges: (): TestRequest =>
      http.expectOne((r) => r.url === `/api/v1/sites/${ID}/network-ranges`),
  };
}

describe('SiteDetail', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads the site then its buildings and rooms, and renders the facts', () => {
    const s = setup();
    s.flushSite();
    s.flushChildren([BUILDING], []);
    expect(s.text()).toContain('Campus Paris');
    expect(s.text()).toContain('Europe/Paris');
    expect(s.text()).toContain('Bâtiment A');
    expect(s.text()).toContain('Aucune salle pour ce site');
  });

  it('shows a not-found panel on a 404 and never loads children', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/sites/${ID}`).flush(
      { status: 404, code: 'SITE_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    s.fixture.detectChanges();
    expect(s.text()).toContain('Aucun site ne correspond');
    s.http.expectNone((r) => r.url === `/api/v1/sites/${ID}/buildings`);
  });

  it('shows an access-denied panel on a 403', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/sites/${ID}`).flush(
      { status: 403, code: 'X', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    s.fixture.detectChanges();
    expect(s.text()).toContain("Vous n'êtes pas autorisé à consulter ce site");
  });

  it('archives the site with a mandatory reason and reloads', () => {
    const s = setup();
    s.flushSite();
    s.flushChildren();
    s.internals.startSiteAction('archive');
    s.fixture.detectChanges();
    // Empty reason → no request.
    s.internals.confirmSiteAction();
    s.http.expectNone((r) => r.method === 'POST' && r.url === `/api/v1/sites/${ID}/archive`);

    s.internals.siteReasonForm.controls.reason.setValue('Fermeture du campus');
    s.fixture.detectChanges();
    s.internals.confirmSiteAction();
    const req = s.http.expectOne(`/api/v1/sites/${ID}/archive`);
    expect(req.request.body).toEqual({ reason: 'Fermeture du campus' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    // Reload chain.
    s.flushSite({ ...SITE, status: 'ARCHIVED', archiveReason: 'Fermeture du campus' });
    s.flushChildren();
    expect(notifications.info).toHaveBeenCalled();
    expect(s.text()).toContain('Restaurer le site');
  });

  it('creates a building and refreshes the buildings list only', () => {
    const s = setup();
    s.flushSite();
    s.flushChildren([], []);
    s.internals.buildingForm.controls.code.setValue('B');
    s.internals.buildingForm.controls.name.setValue('Bâtiment B');
    s.fixture.detectChanges();
    s.internals.submitBuilding();

    const req = s.http.expectOne(`/api/v1/sites/${ID}/buildings`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'B', name: 'Bâtiment B' });
    req.flush({ ...BUILDING, publicId: 'b-2', code: 'B', name: 'Bâtiment B' }, { status: 201, statusText: 'Created' });

    // Only the buildings list reloads (not rooms).
    s.http.expectOne((r) => r.url === `/api/v1/sites/${ID}/buildings`).flush(
      bpage([{ ...BUILDING, publicId: 'b-2', code: 'B', name: 'Bâtiment B' }]),
    );
    s.fixture.detectChanges();
    expect(notifications.info).toHaveBeenCalled();
    expect(s.text()).toContain('Bâtiment B');
  });

  it('hides every write action for a PEDAGOGICAL_MANAGER (read-only) and never requests network ranges', () => {
    const s = setup(['PEDAGOGICAL_MANAGER']);
    s.flushSite();
    s.flushChildren([BUILDING]);
    expect(s.text()).not.toContain('Ajouter le bâtiment');
    expect(s.text()).not.toContain('Archiver le site');
    expect(s.text()).not.toContain('Plages réseau autorisées');
    s.http.expectNone((r) => r.url === `/api/v1/sites/${ID}/network-ranges`);
  });

  it('loads and shows the network-range panel for a SUPER_ADMIN', () => {
    const s = setup(['SUPER_ADMIN']);
    s.flushSite();
    s.flushChildren();
    s.expectRanges().flush(
      npage([
        {
          publicId: 'n-1',
          sitePublicId: ID,
          cidr: '10.0.0.0/24',
          label: 'LAN',
          active: true,
          validFrom: null,
          validUntil: null,
          createdAt: '2026-08-01T10:00:00Z',
          updatedAt: '2026-08-01T10:00:00Z',
        },
      ]),
    );
    s.fixture.detectChanges();
    expect(s.text()).toContain('Plages réseau autorisées');
    expect(s.text()).toContain('10.0.0.0/24');
    expect(s.text()).toContain('Désactiver');
  });
});
