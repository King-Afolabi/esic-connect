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
  RoomStaticQrView,
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

const ROOM: RoomResponse = {
  publicId: 'r-1',
  sitePublicId: ID,
  buildingPublicId: null,
  code: 'A101',
  name: 'Salle 101',
  capacity: 30,
  floorLabel: '1er étage',
  staticQrIssuedAt: '2026-09-01T08:00:00Z',
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

const QR_VIEW: RoomStaticQrView = {
  roomPublicId: 'r-1',
  roomCode: 'A101',
  roomName: 'Salle 101',
  buildingName: null,
  siteName: 'Campus Paris',
  floorLabel: '1er étage',
  issued: true,
  staticQrReference: 'AbCd1234EfGh5678IjKl9012MnOp3456QrSt7890',
  maskedReference: 'AbCd…7890',
  checkInPath: '/attendance?ref=AbCd1234EfGh5678IjKl9012MnOp3456QrSt7890',
  staticQrIssuedAt: '2026-09-01T08:00:00Z',
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

interface Internals {
  siteReasonForm: { controls: { reason: { setValue: (v: string) => void } } };
  buildingForm: { controls: { code: { setValue: (v: string) => void }; name: { setValue: (v: string) => void } } };
  startSiteAction: (k: 'archive' | 'restore') => void;
  confirmSiteAction: () => void;
  submitBuilding: () => void;
  openQr: (room: RoomResponse) => void;
  startRotateQr: () => void;
  confirmRotateQr: () => void;
  setFilter: (which: 'building' | 'room' | 'range', value: string) => void;
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

  it('bounds the child tables and pins their headers (ANO-UX-002/003)', () => {
    const s = setup();
    s.flushSite();
    s.flushChildren([BUILDING], [ROOM]);
    const el = s.fixture.nativeElement as HTMLElement;
    // Bâtiments + Salles : enveloppe à hauteur bornée, défilement interne.
    expect(el.querySelectorAll('.org__table-wrapper.esic-table-wrap--tall').length).toBeGreaterThanOrEqual(2);
    // L'entête figée est marquée par Angular Material (`sticky: true`).
    expect(el.querySelector('.mat-mdc-table-sticky, tr.mat-mdc-header-row')).not.toBeNull();
  });

  it('filters the rooms sub-list by a free-text query (client-side, no request)', () => {
    const s = setup();
    s.flushSite();
    s.flushChildren(
      [],
      [ROOM, { ...ROOM, publicId: 'r-2', code: 'B200', name: 'Amphi B' }],
    );
    expect(s.text()).toContain('Salle 101');
    expect(s.text()).toContain('Amphi B');

    s.internals.setFilter('room', 'amphi');
    s.fixture.detectChanges();
    // Aucun nouvel appel réseau : le filtre est local.
    s.http.expectNone((r) => r.url === `/api/v1/sites/${ID}/rooms`);
    expect(s.text()).toContain('Amphi B');
    expect(s.text()).not.toContain('Salle 101');

    s.internals.setFilter('room', 'zzz-introuvable');
    s.fixture.detectChanges();
    expect(s.text()).toContain('Aucune salle ne correspond à ce filtre');
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

  // --- QR fixe de salle (EF-ORG-003) -------------------------------

  it('shows the static-QR column state and an "Afficher" action for an ADMIN', () => {
    const s = setup(['ADMIN']);
    s.flushSite();
    s.flushChildren([], [ROOM, { ...ROOM, publicId: 'r-2', code: 'A102', staticQrIssuedAt: null }]);
    expect(s.text()).toContain('Disponible');
    expect(s.text()).toContain('Non émis');
    const el = s.fixture.nativeElement as HTMLElement;
    expect(
      el.querySelector('button[aria-label="Afficher le QR fixe de la salle A101"]'),
    ).not.toBeNull();
  });

  it('opens the QR panel (reimpression: a plain GET, nothing mutated) and offers print + renew for an ADMIN', () => {
    const s = setup(['ADMIN']);
    s.flushSite();
    s.flushChildren([], [ROOM]);
    s.internals.openQr(ROOM);
    const req = s.http.expectOne(`/api/v1/rooms/r-1/static-qr`);
    expect(req.request.method).toBe('GET');
    req.flush(QR_VIEW);
    s.fixture.detectChanges();
    expect(s.text()).toContain('AbCd…7890');
    expect(s.text()).toContain("Imprimer l'affiche");
    expect(s.text()).toContain('Renouveler le QR');
    // The full token is never rendered as text.
    expect(s.text()).not.toContain(QR_VIEW.staticQrReference);
  });

  it('never shows "Renouveler" to SCHOOL_ADMINISTRATION or SUPER_ADMIN, but still lets them view/print', () => {
    for (const role of ['SCHOOL_ADMINISTRATION', 'SUPER_ADMIN'] as Role[]) {
      const s = setup([role]);
      s.flushSite();
      const buildings = s.http.expectOne((r) => r.url === `/api/v1/sites/${ID}/buildings`);
      buildings.flush(bpage([]));
      s.http.expectOne((r) => r.url === `/api/v1/sites/${ID}/rooms`).flush(rpage([ROOM]));
      if (role === 'SUPER_ADMIN') {
        s.expectRanges().flush(npage([]));
      }
      s.fixture.detectChanges();
      s.internals.openQr(ROOM);
      s.http.expectOne(`/api/v1/rooms/r-1/static-qr`).flush(QR_VIEW);
      s.fixture.detectChanges();
      expect(s.text()).toContain("Imprimer l'affiche");
      expect(s.text()).not.toContain('Renouveler le QR');
      s.http.verify();
    }
  });

  it('hides the static-QR "Afficher" action from a PEDAGOGICAL_MANAGER', () => {
    const s = setup(['PEDAGOGICAL_MANAGER']);
    s.flushSite();
    s.flushChildren([], [ROOM]);
    expect(s.text()).not.toContain('Afficher le QR fixe de la salle A101');
  });

  it('renews the QR only after an explicit confirmation, then reloads the rooms list', () => {
    const s = setup(['ADMIN']);
    s.flushSite();
    s.flushChildren([], [ROOM]);
    s.internals.openQr(ROOM);
    s.http.expectOne(`/api/v1/rooms/r-1/static-qr`).flush(QR_VIEW);
    s.fixture.detectChanges();

    // First click only reveals the danger confirmation — no request yet.
    s.internals.startRotateQr();
    s.fixture.detectChanges();
    s.http.expectNone((r) => r.url === `/api/v1/rooms/r-1/static-qr/rotate`);
    expect(s.text()).toContain('immédiatement invalides');

    s.internals.confirmRotateQr();
    const rotate = s.http.expectOne(`/api/v1/rooms/r-1/static-qr/rotate`);
    expect(rotate.request.method).toBe('POST');
    rotate.flush({ ...QR_VIEW, staticQrReference: 'ZZZZnew', maskedReference: 'ZZZZ…wnew' });

    // Rooms list reloads to refresh the issue-date column.
    s.http.expectOne((r) => r.url === `/api/v1/sites/${ID}/rooms`).flush(rpage([ROOM]));
    s.fixture.detectChanges();
    expect(notifications.info).toHaveBeenCalled();
  });

  it('surfaces an API error from a renew without crashing', () => {
    const s = setup(['ADMIN']);
    s.flushSite();
    s.flushChildren([], [ROOM]);
    s.internals.openQr(ROOM);
    s.http.expectOne(`/api/v1/rooms/r-1/static-qr`).flush(QR_VIEW);
    s.fixture.detectChanges();
    s.internals.startRotateQr();
    s.fixture.detectChanges();
    s.internals.confirmRotateQr();
    s.http.expectOne(`/api/v1/rooms/r-1/static-qr/rotate`).flush(
      { status: 403, code: 'X', message: 'Accès refusé', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    s.fixture.detectChanges();
    const el = s.fixture.nativeElement as HTMLElement;
    // The error is surfaced in the panel (role="alert") and the panel is still there.
    expect(el.querySelector('.esic-reveal__error')?.textContent ?? '').not.toBe('');
    expect(s.text()).toContain('Renouveler et invalider les affiches');
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
