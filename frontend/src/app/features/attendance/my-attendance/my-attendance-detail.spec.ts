import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { MyAttendanceDetail } from './my-attendance-detail';

const ATT = '/api/v1/me/attendance/a-1';
const JUS = '/api/v1/me/attendance/justifications/j-1/attachment';

const DETAIL = {
  row: {
    attendancePublicId: 'a-1',
    sessionPublicId: 's-1',
    sessionTitle: 'Atelier',
    sessionStartsAt: '2026-09-10T08:00:00Z',
    checkpointPublicId: 'cp-1',
    checkpointLabel: 'Arrivée',
    checkpointType: 'START',
    checkpointRequired: true,
    classCode: 'C1',
    status: 'ABSENT',
    lateMinutes: null,
    comment: null,
    recordedAt: null,
    justificationPublicId: 'j-1',
    justificationStatus: 'PENDING',
    canJustify: true,
  },
  history: [],
  justification: {
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
    checkpointPublicId: 'cp-1',
    checkpointLabel: 'Arrivée',
    classCode: 'C1',
    studentProfilePublicId: null,
    studentNumber: null,
    firstName: null,
    lastName: null,
    attendanceStatus: 'ABSENT',
  },
};

const META = {
  publicId: 'att-1',
  fileName: 'certificat.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  sha256: 'a'.repeat(64),
  uploadedAt: '2026-09-10T10:00:00Z',
};

interface Internals {
  onFileSelected: (e: Event) => void;
  uploadAttachment: () => void;
  downloadAttachment: () => void;
  removeAttachment: () => void;
  attachment: () => unknown;
  uploadError: () => string | null;
  attachmentError: () => string | null;
  pendingFile: () => File | null;
  canAmend: () => boolean;
}

function fileInput(file: File | null): Event {
  const input = document.createElement('input');
  Object.defineProperty(input, 'files', { value: file ? [file] : [] });
  return { target: input } as unknown as Event;
}

function setup(roles: Role[] = ['STUDENT']) {
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
      { provide: RoleContextService, useValue: { effectiveRoles } },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', 'a-1']]) } } },
    ],
  });
  const fixture = TestBed.createComponent(MyAttendanceDetail);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http, internals: fixture.componentInstance as unknown as Internals, effectiveRoles };
}

describe('MyAttendanceDetail — pièces jointes (G1-E)', () => {
  afterEach(() => vi.restoreAllMocks());

  it('loads the attachment metadata after the justification and shows it', () => {
    const { fixture, http } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(META);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('certificat.pdf');
    http.verify();
  });

  it('treats a 404 on the attachment as "no attachment" and offers the upload control', () => {
    const { fixture, http, internals } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(internals.attachment()).toBe('none');
    expect(internals.canAmend()).toBe(true);
    http.verify();
  });

  it('rejects a wrong type client-side before any HTTP call', () => {
    const { http, internals } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(null, { status: 404, statusText: 'Not Found' });
    internals.onFileSelected(fileInput(new File(['x'], 'note.txt', { type: 'text/plain' })));
    expect(internals.pendingFile()).toBeNull();
    expect(internals.uploadError()).toContain('Format non autorisé');
    internals.uploadAttachment();
    http.expectNone(JUS);
    http.verify();
  });

  it('rejects an oversized file client-side before any HTTP call', () => {
    const { http, internals } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(null, { status: 404, statusText: 'Not Found' });
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.pdf', { type: 'application/pdf' });
    internals.onFileSelected(fileInput(big));
    expect(internals.uploadError()).toContain('5 Mo');
    http.expectNone(JUS);
    http.verify();
  });

  it('uploads a valid file and shows the stored attachment', () => {
    const { fixture, http, internals } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(null, { status: 404, statusText: 'Not Found' });
    internals.onFileSelected(fileInput(new File(['%PDF-1.4'], 'c.pdf', { type: 'application/pdf' })));
    internals.uploadAttachment();
    const req = http.expectOne(JUS);
    expect(req.request.method).toBe('POST');
    req.flush(META);
    fixture.detectChanges();
    expect(internals.attachment()).toEqual(META);
    http.verify();
  });

  it('renders the server error on a 409 (already exists)', () => {
    const { http, internals } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(null, { status: 404, statusText: 'Not Found' });
    internals.onFileSelected(fileInput(new File(['%PDF-1.4'], 'c.pdf', { type: 'application/pdf' })));
    internals.uploadAttachment();
    http.expectOne(JUS).flush(
      {
        status: 409,
        code: 'ATT_ATTACHMENT_ALREADY_EXISTS',
        message: 'Une pièce jointe est déjà associée à ce justificatif.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    expect(internals.uploadError()).toContain('déjà associée');
    http.verify();
  });

  it('downloads the attachment via an object URL that is revoked', () => {
    const { http, internals } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(META);

    // Les espions DOM sont posés APRÈS la création du composant (TestBed en a besoin).
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const realCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) =>
      tag === 'a'
        ? ({ href: '', download: '', click: vi.fn(), remove: vi.fn() } as unknown as HTMLElement)
        : realCreateElement(tag),
    );
    vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n as never);

    internals.downloadAttachment();
    http.expectOne(`${JUS}/download`).flush(new Blob(['%PDF-1.4']));
    expect(create).toHaveBeenCalledOnce();
    expect(revoke).toHaveBeenCalledWith('blob:x');
    vi.restoreAllMocks();
    http.verify();
  });

  it('removes the attachment and clears it', () => {
    const { fixture, http, internals } = setup();
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(META);
    internals.removeAttachment();
    http.expectOne(JUS).flush(null);
    fixture.detectChanges();
    expect(internals.attachment()).toBe('none');
    http.verify();
  });

  it('hides upload/remove for a non-student context', () => {
    const { http, internals } = setup(['TEACHER']);
    http.expectOne(ATT).flush(DETAIL);
    http.expectOne(JUS).flush(META);
    expect(internals.canAmend()).toBe(false);
    http.verify();
  });
});
