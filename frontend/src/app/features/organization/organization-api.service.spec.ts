import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OrganizationApiService } from './organization-api.service';

const BASE = '/api/v1';

describe('OrganizationApiService', () => {
  let service: OrganizationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [OrganizationApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrganizationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('sites', () => {
    it('GETs /sites and only sends the filters that are set', () => {
      service
        .listSites({ q: 'PAR', status: 'ACTIVE', sort: 'code,asc', page: 2, size: 50 })
        .subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/sites`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('q')).toBe('PAR');
      expect(req.request.params.get('status')).toBe('ACTIVE');
      expect(req.request.params.get('sort')).toBe('code,asc');
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('omits null / empty filter params entirely', () => {
      service.listSites({ q: null, status: null, sort: 'code,asc' }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/sites`);
      expect(req.request.params.has('q')).toBe(false);
      expect(req.request.params.has('status')).toBe(false);
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('getSite GETs /sites/{publicId}', () => {
      service.getSite('s-1').subscribe();
      const req = http.expectOne(`${BASE}/sites/s-1`);
      expect(req.request.method).toBe('GET');
      req.flush({});
    });

    it('createSite POSTs the body to /sites', () => {
      const body = { code: 'PAR', name: 'Paris', timeZoneId: 'Europe/Paris' };
      service.createSite(body).subscribe();
      const req = http.expectOne(`${BASE}/sites`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({}, { status: 201, statusText: 'Created' });
    });

    it('updateSite PATCHes /sites/{publicId}', () => {
      const body = { name: 'Paris 2', timeZoneId: 'Europe/Paris' };
      service.updateSite('s-9', body).subscribe();
      const req = http.expectOne(`${BASE}/sites/s-9`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('archiveSite POSTs the reason to /sites/{publicId}/archive', () => {
      service.archiveSite('s-2', { reason: 'fermeture' }).subscribe();
      const req = http.expectOne(`${BASE}/sites/s-2/archive`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'fermeture' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('restoreSite POSTs to /sites/{publicId}/restore', () => {
      service.restoreSite('s-3').subscribe();
      const req = http.expectOne(`${BASE}/sites/s-3/restore`);
      expect(req.request.method).toBe('POST');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('buildings', () => {
    it('listBuildings GETs the nested route with sort and size only', () => {
      service.listBuildings('s-1', { sort: 'code,asc', size: 100 }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/sites/s-1/buildings`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('sort')).toBe('code,asc');
      expect(req.request.params.get('size')).toBe('100');
      req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    });

    it('createBuilding POSTs to the nested route', () => {
      const body = { code: 'A', name: 'Bâtiment A' };
      service.createBuilding('s-1', body).subscribe();
      const req = http.expectOne(`${BASE}/sites/s-1/buildings`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({}, { status: 201, statusText: 'Created' });
    });

    it('archiveBuilding / restoreBuilding hit the flat unit routes', () => {
      service.archiveBuilding('b-1', { reason: 'travaux' }).subscribe();
      const a = http.expectOne(`${BASE}/buildings/b-1/archive`);
      expect(a.request.body).toEqual({ reason: 'travaux' });
      a.flush(null, { status: 204, statusText: 'No Content' });

      service.restoreBuilding('b-1').subscribe();
      const r = http.expectOne(`${BASE}/buildings/b-1/restore`);
      expect(r.request.method).toBe('POST');
      r.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('rooms', () => {
    it('listRooms GETs the nested route and forwards the building filter', () => {
      service.listRooms('s-1', { building: 'b-1', sort: 'code,asc', size: 100 }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/sites/s-1/rooms`);
      expect(req.request.params.get('building')).toBe('b-1');
      expect(req.request.params.get('sort')).toBe('code,asc');
      req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    });

    it('createRoom POSTs the exact body to the nested route', () => {
      const body = {
        code: 'A101',
        name: 'Salle A101',
        buildingPublicId: 'b-1',
        capacity: 30,
        floorLabel: '1',
      };
      service.createRoom('s-1', body).subscribe();
      const req = http.expectOne(`${BASE}/sites/s-1/rooms`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({}, { status: 201, statusText: 'Created' });
    });
  });

  describe('network ranges', () => {
    it('listNetworkRanges GETs the nested route with active / sort / page / size only', () => {
      service.listNetworkRanges('s-1', { active: 'true', sort: 'createdAt,desc', page: 0, size: 100 }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/sites/s-1/network-ranges`);
      expect(req.request.params.keys().sort()).toEqual(['active', 'page', 'size', 'sort']);
      expect(req.request.params.get('active')).toBe('true');
      req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    });

    it('createNetworkRange POSTs the body to the nested route', () => {
      const body = { cidr: '10.0.0.0/24', label: 'LAN' };
      service.createNetworkRange('s-1', body).subscribe();
      const req = http.expectOne(`${BASE}/sites/s-1/network-ranges`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({}, { status: 201, statusText: 'Created' });
    });

    it('activate / deactivate hit the flat unit routes', () => {
      service.activateNetworkRange('n-1').subscribe();
      http.expectOne(`${BASE}/network-ranges/n-1/activate`).flush(null, { status: 204, statusText: 'No Content' });
      service.deactivateNetworkRange('n-1').subscribe();
      http.expectOne(`${BASE}/network-ranges/n-1/deactivate`).flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  it('never exposes a client parameter that could widen a pedagogical-manager scope', () => {
    service.listSites({ q: 'x' }).subscribe();
    const a = http.expectOne((r) => r.url === `${BASE}/sites`);
    expect(a.request.params.keys().sort()).toEqual(['q']);
    a.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
