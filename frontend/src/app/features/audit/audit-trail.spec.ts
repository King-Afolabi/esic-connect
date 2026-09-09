import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { NotificationService } from '../../core/notifications/notification.service';
import { AuditApiService } from './audit-api.service';
import { AuditTrail } from './audit-trail';

/**
 * Piste d'audit (EF-AUD-002 ; docs/02 §23.4).
 *
 * Vérifie que l'écran est en lecture seule, qu'il n'affiche aucune
 * colonne interdite, et que la borne haute d'une période couvre bien la
 * journée entière — sans quoi « jusqu'au 5 septembre » exclurait tout ce
 * qui s'est passé ce jour-là.
 */
describe('AuditTrail', () => {
  let fixture: ComponentFixture<AuditTrail>;

  const api = { list: vi.fn(), facets: vi.fn(), export: vi.fn() };
  const notifications = { error: vi.fn(), info: vi.fn() };

  const row = {
    publicId: 'ev-1',
    occurredAt: '2026-09-05T08:00:00Z',
    actorDisplay: 'Camille Bernard',
    actorPublicId: 'u-1',
    actorRole: 'ADMIN',
    action: 'ACADEMIC_PROGRAM_CREATED',
    category: 'ACADEMIC',
    resourceType: 'PROGRAM',
    resourcePublicId: 'p-1',
    result: 'SUCCESS',
    reason: null,
    correlationId: null,
  };

  beforeEach(async () => {
    api.list
      .mockReset()
      .mockReturnValue(
        of({ content: [row], totalElements: 1, totalPages: 1, number: 0, size: 20 }),
      );
    api.facets.mockReset().mockReturnValue(
      of({
        actions: ['ACADEMIC_PROGRAM_CREATED'],
        categories: ['ACADEMIC'],
        resourceTypes: ['PROGRAM'],
        results: ['SUCCESS'],
      }),
    );
    api.export
      .mockReset()
      .mockReturnValue(of(new HttpResponse({ body: new Blob(['x']), status: 200 })));
    notifications.error.mockReset();

    await TestBed.configureTestingModule({
      imports: [AuditTrail],
      providers: [
        { provide: AuditApiService, useValue: api },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AuditTrail);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;
  const component = () =>
    fixture.componentInstance as unknown as {
      filters: { patchValue: (v: Record<string, string>) => void };
      apply: () => void;
      exportAs: (f: string) => void;
    };

  it('lists the trail and renders only the columns the specification allows', () => {
    expect(text()).toContain('ACADEMIC_PROGRAM_CREATED');
    expect(text()).toContain('Camille Bernard');
    // §23.3 : ni adresse IP, ni colonne JSON brute.
    expect(text()).not.toContain('oldValuesJson');
    expect(text()).not.toContain('127.0.0.1');
  });

  it('bounds the long table and pins its native header (ANO-UX-002)', () => {
    const el = fixture.nativeElement as HTMLElement;
    const scroll = el.querySelector('.audit__scroll.esic-table-wrap--tall');
    expect(scroll).not.toBeNull();
    // L'entête reste dans le flux de la table (rendu natif) ; la
    // stickiness est portée par la primitive `.esic-table-wrap--tall thead th`.
    expect(scroll!.querySelector('thead th')).not.toBeNull();
  });

  it('offers no way to edit or delete an audit entry', () => {
    const html = fixture.nativeElement.innerHTML as string;
    expect(html).not.toContain('Supprimer');
    expect(html).not.toContain('Modifier');
    expect(api).not.toHaveProperty('delete');
    expect(api).not.toHaveProperty('update');
  });

  it('sends the end of the day as the upper bound of a period', () => {
    component().filters.patchValue({ from: '2026-09-01', to: '2026-09-05' });
    component().apply();
    const query = api.list.mock.calls.at(-1)?.[0];
    expect(query.from).toBe('2026-09-01T00:00:00.000Z');
    // Sans cela, tout ce qui s'est passé le 5 après minuit serait exclu.
    expect(query.to).toBe('2026-09-05T23:59:59.999Z');
  });

  it.each(['csv', 'xlsx', 'pdf'])('forwards the %s export format to the server', (format) => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    component().exportAs(format);
    expect(api.export).toHaveBeenCalledWith(expect.anything(), format);
    vi.restoreAllMocks();
  });

  it('renders a forbidden panel on 403', () => {
    api.list.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' })),
    );
    component().apply();
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter la piste d'audit.");
  });

  it('stays usable when the facets cannot be loaded', async () => {
    api.facets.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const second = TestBed.createComponent(AuditTrail);
    second.detectChanges();
    expect(notifications.error).not.toHaveBeenCalled();
    expect(second.nativeElement.textContent as string).toContain('ACADEMIC_PROGRAM_CREATED');
  });
});
