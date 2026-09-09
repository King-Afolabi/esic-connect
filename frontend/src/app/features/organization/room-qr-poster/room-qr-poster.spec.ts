import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { RoomStaticQrView } from '../organization.models';
import { RoomQrPoster } from './room-qr-poster';

const ROOM_ID = 'r-1';
const SITE_ID = 's-1';

const ISSUED: RoomStaticQrView = {
  roomPublicId: ROOM_ID,
  roomCode: 'A101',
  roomName: 'Salle 101',
  buildingName: 'Bâtiment A',
  siteName: 'Campus Paris',
  floorLabel: '1er étage',
  issued: true,
  staticQrReference: 'AbCd1234EfGh5678IjKl9012MnOp3456QrSt7890',
  maskedReference: 'AbCd…7890',
  checkInPath: '/attendance?ref=AbCd1234EfGh5678IjKl9012MnOp3456QrSt7890',
  staticQrIssuedAt: '2026-09-01T08:00:00Z',
};

function setup() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap({ roomId: ROOM_ID, publicId: SITE_ID }) },
        },
      },
    ],
  });
  const fixture: ComponentFixture<RoomQrPoster> = TestBed.createComponent(RoomQrPoster);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return {
    fixture,
    http,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    el: () => fixture.nativeElement as HTMLElement,
  };
}

describe('RoomQrPoster', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('requests the room static-QR view for the routed room id', () => {
    const s = setup();
    const req = s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`);
    expect(req.request.method).toBe('GET');
    req.flush(ISSUED);
  });

  it('renders the poster: room, site line, instructions, issue date, masked reference and a QR', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`).flush(ISSUED);
    s.fixture.detectChanges();
    const text = s.text();
    expect(text).toContain('ESIC Connect — Émargement');
    expect(text).toContain('A101');
    expect(text).toContain('Campus Paris · Bâtiment A · 1er étage');
    expect(text).toContain('Scannez ce code');
    expect(text).toContain('Ne pas déplacer cette affiche');
    expect(text).toContain('réseau autorisé');
    expect(text).toContain('AbCd…7890');
    expect(s.el().querySelector('qrcode')).not.toBeNull();
    // The full opaque token is never rendered as text.
    expect(text).not.toContain(ISSUED.staticQrReference);
  });

  it('encodes the absolute check-in URL in the QR, never the bare reference as text', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`).flush(ISSUED);
    s.fixture.detectChanges();
    const payload = (
      s.fixture.componentInstance as unknown as { qrPayload: () => string | null }
    ).qrPayload();
    expect(payload).toContain('/attendance?ref=');
    expect(payload).toContain(ISSUED.staticQrReference!);
    expect(payload!.startsWith('http')).toBe(true);
    // ...mais jamais en texte visible.
    expect(s.text()).not.toContain(ISSUED.staticQrReference!);
  });

  it('carries a non-printed NFC setup note', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`).flush(ISSUED);
    s.fixture.detectChanges();
    const help = s.el().querySelector('.poster-page__help');
    expect(help).not.toBeNull();
    expect(help?.classList.contains('no-print')).toBe(true);
    expect(help?.textContent).toContain('NDEF');
    expect(help?.textContent).toContain('la même URL que ce QR fixe');
  });

  it('shows a clear "not issued" state instead of a poster when no QR has been emitted', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`).flush({
      ...ISSUED,
      issued: false,
      staticQrReference: null,
      maskedReference: null,
      checkInPath: null,
      staticQrIssuedAt: null,
    });
    s.fixture.detectChanges();
    expect(s.text()).toContain("Aucun QR fixe n'a encore été émis");
    expect(s.el().querySelector('qrcode')).toBeNull();
  });

  it('renders no application shell chrome — the poster prints alone (ANO-QR-001)', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`).flush(ISSUED);
    s.fixture.detectChanges();
    const el = s.el();
    expect(el.querySelector('.shell__rail')).toBeNull();
    expect(el.querySelector('.shell__topbar')).toBeNull();
    expect(el.querySelector('mat-sidenav')).toBeNull();
    expect(el.querySelector('mat-nav-list')).toBeNull();
  });

  it('links back to the parent site with an absolute route', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`).flush(ISSUED);
    s.fixture.detectChanges();
    const back = s.el().querySelector('a.poster-page__back') as HTMLAnchorElement | null;
    expect(back?.getAttribute('href')).toBe(`/organization/sites/${SITE_ID}`);
  });

  it('shows an access-denied message on a 403', () => {
    const s = setup();
    s.http.expectOne(`/api/v1/rooms/${ROOM_ID}/static-qr`).flush(
      { status: 403, code: 'X', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    s.fixture.detectChanges();
    expect(s.text()).toContain("Vous n'êtes pas autorisé");
  });
});
