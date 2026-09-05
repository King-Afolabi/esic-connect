import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';

import { Role } from '../../core/models/role';
import { RoleContextService } from '../../core/auth/role-context.service';
import { ConnectivityService } from '../../core/pwa/connectivity.service';
import { OfflineQueueService } from '../../core/pwa/offline-queue.service';
import { AttendanceCheckIn } from './attendance-check-in';

const URL = '/api/v1/attendance/validate';

interface CheckInInternals {
  form: FormGroup;
  submit: () => void;
}

/**
 * Émargement hors ligne (EF-PWA-003 ; RG-063, AC-031).
 *
 * <p>Le point vérifié n'est pas que « ça marche hors ligne » — c'est
 * l'inverse : que l'écran ne prétende <strong>jamais</strong> avoir
 * enregistré une présence que le serveur n'a pas validée.
 */
function setup(online: boolean) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal<Role[]>(['STUDENT']);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles } },
    ],
  });
  const connectivity = TestBed.inject(ConnectivityService);
  if (!online) {
    connectivity.reportNetworkFailure();
  } else {
    connectivity.reportNetworkSuccess();
  }
  const queue = TestBed.inject(OfflineQueueService);
  const fixture = TestBed.createComponent(AttendanceCheckIn);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return {
    fixture,
    http,
    queue,
    connectivity,
    internals: fixture.componentInstance as unknown as CheckInInternals,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
  };
}

describe('AttendanceCheckIn — hors ligne', () => {
  it('met l’émargement en file et annonce « en attente de confirmation »', () => {
    const { internals, http, queue, fixture, text } = setup(false);

    internals.form.setValue({ shortCode: 'ABC123', remote: false });
    internals.submit();
    fixture.detectChanges();

    // Aucune requête n'est tentée : le réseau est connu indisponible.
    http.verify();
    expect(queue.pendingCount()).toBe(1);
    expect(text()).toContain('En attente de confirmation');
    // Le mot « enregistrée » seul induirait en erreur : la présence
    // n'existe pas tant que le serveur ne l'a pas validée.
    expect(text()).not.toContain('Présence enregistrée');
  });

  it('prévient, avant la saisie, que l’émargement sera mis en attente', () => {
    const { text } = setup(false);
    expect(text()).toContain('sera mis en attente');
  });

  it('envoie normalement lorsque le réseau est disponible', () => {
    const { internals, http, queue, fixture, text } = setup(true);

    internals.form.setValue({ shortCode: 'ABC123', remote: false });
    internals.submit();
    const request = http.expectOne(URL);
    expect(request.request.body).toEqual({ shortCode: 'ABC123', remote: null });
    request.flush({
      publicId: 'a-1',
      recordedAt: '2026-09-10T08:05:00Z',
      sessionTitle: 'Cours',
      status: 'PRESENT',
    });
    fixture.detectChanges();

    expect(queue.pendingCount()).toBe(0);
    expect(text()).toContain('Présence enregistrée');
  });

  it('confirme l’action mise en file une fois le serveur revenu', async () => {
    const { internals, http, queue, fixture } = setup(false);
    internals.form.setValue({ shortCode: 'ABC123', remote: false });
    internals.submit();
    fixture.detectChanges();
    expect(queue.pendingCount()).toBe(1);

    const replay = queue.replay();
    http.expectOne(URL).flush('{}');
    await replay;

    expect(queue.pendingCount()).toBe(0);
    expect(queue.actions()[0].status).toBe('CONFIRMED');
  });

  it('n’écrit aucun code d’émargement dans le stockage du navigateur (RG-093)', () => {
    const { internals, fixture } = setup(false);
    internals.form.setValue({ shortCode: 'CODE-SECRET', remote: false });
    internals.submit();
    fixture.detectChanges();

    expect(JSON.stringify({ ...localStorage })).not.toContain('CODE-SECRET');
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('CODE-SECRET');
  });
});
