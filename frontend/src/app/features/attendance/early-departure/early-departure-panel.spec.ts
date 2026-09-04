import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NotificationService } from '../../../core/notifications/notification.service';
import { EarlyDeparturePanel } from './early-departure-panel';

const URL = '/api/v1/sessions/s-1/attendance/early-departures';

function dossier(status: string, teacherOpinion: string | null = null) {
  return {
    publicId: 'ed-1',
    sessionPublicId: 's-1',
    sessionTitle: 'Atelier',
    sessionStartsAt: '2026-09-10T08:00:00Z',
    enrollmentPublicId: 'e-1',
    classCode: 'C1',
    departureAt: '2026-09-10T09:00:00Z',
    reason: 'rendez-vous',
    status,
    effect: status === 'ACCEPTED' ? 'EXCUSED_PARTIAL' : 'TO_CONFIRM',
    requestedAt: '2026-09-10T08:30:00Z',
    teacherOpinion,
    teacherOpinionComment: null,
    teacherOpinionAt: teacherOpinion ? '2026-09-10T08:40:00Z' : null,
    decidedByRole: null,
    decidedAt: null,
    decisionComment: null,
  };
}

@Component({
  imports: [EarlyDeparturePanel],
  template:
    '<app-early-departure-panel [sessionId]="id()" [canDecideForwarded]="canDecide()" />',
})
class Host {
  readonly id = signal('s-1');
  readonly canDecide = signal(false);
}

interface PanelInternals {
  toggle: (id: string, mode: 'forward' | 'decide') => void;
  forwardForm: { patchValue: (v: Record<string, unknown>) => void };
  submitForward: (id: string) => void;
  decideForm: { patchValue: (v: Record<string, unknown>) => void };
  submitDecision: (id: string) => void;
}

function setup() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(Host);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  const panel = fixture.debugElement.children[0].componentInstance as unknown as PanelInternals;
  return { fixture, http, panel };
}

describe('EarlyDeparturePanel', () => {
  let fixture: ComponentFixture<Host>;
  let http: HttpTestingController;
  let panel: PanelInternals;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('lists the dossiers of the session with their effect', () => {
    ({ fixture, http } = setup());
    http.expectOne(URL).flush([dossier('REQUESTED')]);
    fixture.detectChanges();

    expect(text()).toContain('Signalé');
    expect(text()).toContain('À confirmer');
    expect(text()).toContain('rendez-vous');
  });

  it('offers both forwarding and decision on a fresh dossier', () => {
    ({ fixture, http } = setup());
    http.expectOne(URL).flush([dossier('REQUESTED')]);
    fixture.detectChanges();

    expect(text()).toContain('Transmettre au responsable');
    expect(text()).toContain('Décider');
  });

  it('hides the decision from a teacher once the dossier has been forwarded', () => {
    ({ fixture, http } = setup());
    http.expectOne(URL).flush([dossier('FORWARDED', 'FAVOURABLE')]);
    fixture.detectChanges();

    // « Transmettre n'est pas décider » : proposer une action que le
    // serveur refusera (403) n'aiderait personne.
    expect(text()).toContain('Avis favorable');
    expect(text()).not.toContain('Transmettre au responsable');
    expect(text()).not.toContain('Décider');
  });

  it('offers the decision on a forwarded dossier to a responsable', () => {
    ({ fixture, http } = setup());
    fixture.componentInstance.canDecide.set(true);
    http.expectOne(URL).flush([dossier('FORWARDED')]);
    fixture.detectChanges();

    expect(text()).toContain('Décider');
  });

  it('forwards with an optional opinion and reloads', () => {
    ({ fixture, http, panel } = setup());
    http.expectOne(URL).flush([dossier('REQUESTED')]);
    fixture.detectChanges();

    panel.toggle('ed-1', 'forward');
    panel.forwardForm.patchValue({ opinion: 'FAVOURABLE', comment: 'avis favorable' });
    panel.submitForward('ed-1');

    const forwarded = http.expectOne('/api/v1/attendance/early-departures/ed-1/forward');
    expect(forwarded.request.body).toEqual({
      opinion: 'FAVOURABLE',
      comment: 'avis favorable',
    });
    forwarded.flush(dossier('FORWARDED', 'FAVOURABLE'));
    http.expectOne(URL).flush([dossier('FORWARDED', 'FAVOURABLE')]);
    fixture.detectChanges();
  });

  it('requires a motive before recording a decision', () => {
    ({ fixture, http, panel } = setup());
    http.expectOne(URL).flush([dossier('REQUESTED')]);
    fixture.detectChanges();

    panel.toggle('ed-1', 'decide');
    panel.decideForm.patchValue({ accepted: true, comment: '' });
    panel.submitDecision('ed-1');
    http.expectNone('/api/v1/attendance/early-departures/ed-1/decision');
  });

  it('records an accepted decision with its motive', () => {
    ({ fixture, http, panel } = setup());
    http.expectOne(URL).flush([dossier('REQUESTED')]);
    fixture.detectChanges();

    panel.toggle('ed-1', 'decide');
    panel.decideForm.patchValue({ accepted: true, comment: 'accord donné' });
    panel.submitDecision('ed-1');

    const decided = http.expectOne('/api/v1/attendance/early-departures/ed-1/decision');
    expect(decided.request.body).toEqual({ accepted: true, comment: 'accord donné' });
    decided.flush(dossier('ACCEPTED'));
    http.expectOne(URL).flush([dossier('ACCEPTED')]);
    fixture.detectChanges();
  });

  it('surfaces the reserved-decision refusal without exposing anything else', () => {
    ({ fixture, http, panel } = setup());
    http.expectOne(URL).flush([dossier('REQUESTED')]);
    fixture.detectChanges();

    panel.toggle('ed-1', 'decide');
    panel.decideForm.patchValue({ accepted: true, comment: 'je décide' });
    panel.submitDecision('ed-1');
    http.expectOne('/api/v1/attendance/early-departures/ed-1/decision').flush(
      {
        timestamp: '2026-09-10T09:00:00Z',
        status: 403,
        code: 'ATT_EARLY_DEPARTURE_DECISION_RESERVED',
        message:
          'Ce dossier a été transmis au responsable pédagogique : la décision lui revient.',
        path: '/api/v1/attendance/early-departures/ed-1/decision',
        correlationId: null,
        details: [],
      },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();

    expect(text()).toContain('la décision lui revient');
  });
});
