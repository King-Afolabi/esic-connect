import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { ClaimThread } from './claim-thread';

const URL = '/api/v1/claims/c-1';

function thread(status: string, events: unknown[] = []) {
  return {
    claim: {
      publicId: 'c-1',
      authorPublicId: 'u-1',
      category: 'ATTENDANCE',
      subject: 'Absence du 10 septembre',
      status,
      audience: 'PEDAGOGICAL_MANAGER',
      sessionPublicId: null,
      classGroupPublicId: null,
      periodStart: null,
      periodEnd: null,
      closedAt: status === 'CLOSED' ? '2026-09-11T08:00:00Z' : null,
      createdAt: '2026-09-10T08:00:00Z',
      updatedAt: '2026-09-10T08:00:00Z',
    },
    messages: [
      {
        publicId: 'm-1',
        authorPublicId: 'u-1',
        authorRole: 'STUDENT',
        body: "J'ai bien émargé ce jour-là.",
        createdAt: '2026-09-10T08:00:00Z',
      },
    ],
    events,
  };
}

interface Internals {
  toggle: (panel: 'transfer' | 'decide' | 'reopen') => void;
  messageForm: { patchValue: (v: Record<string, unknown>) => void };
  sendMessage: () => void;
  transferForm: { patchValue: (v: Record<string, unknown>) => void };
  transfer: () => void;
  reopenForm: { patchValue: (v: Record<string, unknown>) => void };
  reopen: () => void;
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
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: new Map([['publicId', 'c-1']]) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(ClaimThread);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http, internals: fixture.componentInstance as unknown as Internals };
}

describe('ClaimThread', () => {
  let fixture: ComponentFixture<ClaimThread>;
  let http: HttpTestingController;
  let internals: Internals;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('renders the conversation with the role held at write time', () => {
    ({ fixture, http } = setup());
    http.expectOne(URL).flush(thread('OPEN'));
    fixture.detectChanges();

    expect(text()).toContain('Absence du 10 septembre');
    expect(text()).toContain("J'ai bien émargé ce jour-là.");
    expect(text()).toContain('Apprenant');
  });

  it('keeps decisions apart from the conversation, motive included', () => {
    ({ fixture, http } = setup());
    http.expectOne(URL).flush(
      thread('TRANSFERRED', [
        {
          publicId: 'e-1',
          eventType: 'TRANSFERRED',
          fromStatus: 'OPEN',
          toStatus: 'TRANSFERRED',
          fromAudience: 'TEACHER',
          toAudience: 'PEDAGOGICAL_MANAGER',
          motive: 'relève du responsable',
          createdAt: '2026-09-10T09:00:00Z',
        },
      ]),
    );
    fixture.detectChanges();

    expect(text()).toContain('Transférée');
    expect(text()).toContain('relève du responsable');
    expect(text()).toContain('vers Responsable pédagogique');
  });

  it('adds a message to an open thread', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(URL).flush(thread('OPEN'));
    fixture.detectChanges();

    internals.messageForm.patchValue({ body: 'Merci de vérifier.' });
    internals.sendMessage();
    const posted = http.expectOne(`${URL}/messages`);
    expect(posted.request.body).toEqual({ body: 'Merci de vérifier.' });
    posted.flush(thread('OPEN'));
    fixture.detectChanges();
  });

  it('offers a reopening — not a message — once the thread is closed', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(URL).flush(thread('CLOSED'));
    fixture.detectChanges();

    expect(text()).toContain('Cette réclamation est close');
    expect(text()).toContain('Rouvrir');

    internals.toggle('reopen');
    internals.reopenForm.patchValue({ motive: 'élément nouveau' });
    internals.reopen();
    const reopened = http.expectOne(`${URL}/reopen`);
    expect(reopened.request.body).toEqual({ motive: 'élément nouveau' });
    reopened.flush(thread('REOPENED'));
    fixture.detectChanges();
    expect(text()).toContain('Rouverte');
  });

  it('does not offer transfer or decision to the author', () => {
    ({ fixture, http } = setup(['STUDENT']));
    http.expectOne(URL).flush(thread('OPEN'));
    fixture.detectChanges();

    expect(text()).not.toContain('Transférer');
    expect(text()).not.toContain('Décider');
  });

  it('offers transfer and decision to an intervenant', () => {
    ({ fixture, http, internals } = setup(['PEDAGOGICAL_MANAGER']));
    http.expectOne(URL).flush(thread('OPEN'));
    fixture.detectChanges();

    expect(text()).toContain('Transférer');
    expect(text()).toContain('Décider');

    internals.toggle('transfer');
    internals.transferForm.patchValue({
      audience: 'SCHOOL_ADMINISTRATION',
      motive: 'relève de la scolarité',
    });
    internals.transfer();
    const transferred = http.expectOne(`${URL}/transfer`);
    expect(transferred.request.body).toEqual({
      audience: 'SCHOOL_ADMINISTRATION',
      motive: 'relève de la scolarité',
    });
    transferred.flush(thread('TRANSFERRED'));
    fixture.detectChanges();
  });

  it("renders a 404 as 'introuvable' — a claim of someone else must not be distinguishable", () => {
    ({ fixture, http } = setup());
    http.expectOne(URL).flush(
      {
        timestamp: '2026-09-10T08:00:00Z',
        status: 404,
        code: 'CLAIM_NOT_FOUND',
        message: 'Cette réclamation est introuvable.',
        path: URL,
        correlationId: null,
        details: [],
      },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    expect(text()).toContain('Aucune réclamation ne correspond');
    expect(text()).not.toContain('Accès refusé');
  });
});
