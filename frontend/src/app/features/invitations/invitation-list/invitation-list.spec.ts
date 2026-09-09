import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { InvitationsApiService } from '../invitations-api.service';
import { InvitationList } from './invitation-list';

describe('InvitationList', () => {
  let fixture: ComponentFixture<InvitationList>;

  const api = {
    list: vi.fn(),
    resend: vi.fn(),
    listDeliveries: vi.fn(),
  };

  const invitation = {
    id: 'inv-1',
    userId: 'user-1',
    email: 'camille.bernard@esic-connect.test',
    firstName: 'Camille',
    lastName: 'Bernard',
    status: 'PENDING' as const,
    expired: false,
    expiresAt: '2026-10-03T08:00:00Z',
    usedAt: null,
    createdAt: '2026-09-03T08:00:00Z',
  };

  const delivery = {
    id: 'del-1',
    recipientMasked: 'c…e@e…c.test',
    messageType: 'ACCOUNT_INVITATION',
    internalStatus: 'SENT_TO_PROVIDER' as const,
    providerStatus: 'UNKNOWN' as const,
    attempts: 1,
    lastAttemptAt: '2026-09-03T08:00:01Z',
    lastError: null,
    createdAt: '2026-09-03T08:00:00Z',
  };

  beforeEach(async () => {
    api.list.mockReset().mockReturnValue(
      of({ content: [invitation], page: 0, size: 50, totalElements: 1, totalPages: 1 }),
    );
    api.listDeliveries.mockReset().mockReturnValue(
      of({ content: [delivery], page: 0, size: 50, totalElements: 1, totalPages: 1 }),
    );
    api.resend.mockReset().mockReturnValue(of({ publicId: 'inv-2', expiresAt: '2026-11-03' }));

    await TestBed.configureTestingModule({
      imports: [InvitationList],
      providers: [provideRouter([]), { provide: InvitationsApiService, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(InvitationList);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;

  it('liste les invitations en attente', () => {
    expect(text()).toContain('Camille');
    expect(text()).toContain('En attente');
  });

  it("distingue « remis au serveur » de « délivré »", () => {
    // docs/02 §11.3 : confondre les deux ferait croire qu'une invitation
    // est arrivée alors que l'adresse est erronée.
    expect(text()).toContain('Remis au serveur de messagerie');
    expect(text()).toContain('Non renseigné par le fournisseur');
    expect(text()).not.toContain('Délivré');
  });

  it("n'affiche que des adresses masquées dans le journal d'envoi", () => {
    expect(text()).toContain('c…e@e…c.test');
  });

  it('réémet une invitation et annonce que le lien précédent ne marche plus', () => {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    buttons.find((button) => button.textContent?.includes('Réémettre'))!.click();
    fixture.detectChanges();

    expect(api.resend).toHaveBeenCalledWith('inv-1');
    expect(text()).toContain('Le lien précédent ne fonctionne plus');
  });

  it('explique un refus de réémission sur un compte déjà activé', () => {
    // `normalizeHttpError` ne reconnaît qu'une vraie HttpErrorResponse :
    // un objet nu retomberait sur le message générique.
    api.resend.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'INVITATION_TARGET_NOT_PENDING', message: 'x' },
          }),
      ),
    );
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    buttons.find((button) => button.textContent?.includes('Réémettre'))!.click();
    fixture.detectChanges();

    expect(text()).toContain("n'est plus en attente d'activation");
  });
});
