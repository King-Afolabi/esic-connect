import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { NotificationService } from '../../../core/notifications/notification.service';
import { InvitationsApiService } from '../invitations-api.service';
import { PendingInvitationReport } from './pending-invitation-report';

/**
 * Rapport des invitations non activées (EF-REP-010).
 *
 * Le point vérifié en priorité : l'écran n'affiche que l'adresse
 * masquée que le serveur lui donne, et n'en reconstruit aucune.
 */
describe('PendingInvitationReport', () => {
  let fixture: ComponentFixture<PendingInvitationReport>;

  const api = { listPendingInvitations: vi.fn(), exportPendingInvitations: vi.fn() };
  const notifications = { error: vi.fn(), info: vi.fn() };

  const rows = [
    {
      invitationPublicId: 'inv-1',
      userPublicId: 'u-1',
      firstName: 'Camille',
      lastName: 'Bernard',
      maskedEmail: 'c…e@e…c.test',
      lastSentAt: '2026-09-01T08:00:00Z',
      expiresAt: '2026-10-01T08:00:00Z',
      expired: false,
      daysPending: 4,
    },
    {
      invitationPublicId: 'inv-2',
      userPublicId: 'u-2',
      firstName: 'Alix',
      lastName: 'Martin',
      maskedEmail: 'a…x@e…c.test',
      lastSentAt: '2026-07-01T08:00:00Z',
      expiresAt: '2026-08-01T08:00:00Z',
      expired: true,
      daysPending: 66,
    },
  ];

  beforeEach(async () => {
    api.listPendingInvitations.mockReset().mockReturnValue(of(rows));
    api.exportPendingInvitations
      .mockReset()
      .mockReturnValue(of(new HttpResponse({ body: new Blob(['x']), status: 200 })));
    notifications.error.mockReset();

    await TestBed.configureTestingModule({
      imports: [PendingInvitationReport],
      providers: [
        { provide: InvitationsApiService, useValue: api },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PendingInvitationReport);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;
  const component = () =>
    fixture.componentInstance as unknown as { exportAs: (f: string) => void };

  it('lists pending accounts and counts the expired invitations', () => {
    expect(text()).toContain('Bernard');
    expect(text()).toContain('Martin');
    expect(text()).toContain('2 compte(s) en attente, dont 1 invitation(s) expirée(s).');
  });

  it('shows only the masked address, never a full email', () => {
    expect(text()).toContain('c…e@e…c.test');
    expect(text()).not.toContain('camille.bernard@');
  });

  it.each(['csv', 'xlsx', 'pdf'])('forwards the %s export format', (format) => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    component().exportAs(format);
    expect(api.exportPendingInvitations).toHaveBeenCalledWith(format);
    vi.restoreAllMocks();
  });

  it('renders a forbidden panel on 403', () => {
    api.listPendingInvitations.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' })),
    );
    const second = TestBed.createComponent(PendingInvitationReport);
    second.detectChanges();
    expect(second.nativeElement.textContent as string).toContain(
      "Vous n'êtes pas autorisé à consulter ce rapport.",
    );
  });
});
