import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { NotificationService } from '../../core/notifications/notification.service';
import { AttendanceApiService } from '../attendance/attendance-api.service';
import { Attestations } from './attestations';

/**
 * Attestations d'assiduité (EF-REP-006 ; AC-033).
 *
 * Vérifie que l'identifiant du document est affiché après émission, et
 * que la vérification ne montre rien de plus que ce que le serveur
 * renvoie — jamais de donnée d'assiduité.
 */
describe('Attestations', () => {
  let fixture: ComponentFixture<Attestations>;

  const api = {
    issueAttestation: vi.fn(),
    listAttestations: vi.fn(),
    verifyAttestation: vi.fn(),
  };
  const notifications = { error: vi.fn(), info: vi.fn() };

  beforeEach(async () => {
    api.listAttestations.mockReset().mockReturnValue(
      of([
        {
          publicId: 'doc-1',
          documentId: 'ESIC-ATT-2026-ABCDEFGHJK',
          documentType: 'ATTENDANCE_CERTIFICATE',
          subject: 'Bernard Camille (ESIC-2026-1)',
          issuedAt: '2026-09-05T08:00:00Z',
          issuedBy: 'Alix Martin',
          revoked: false,
        },
      ]),
    );
    api.issueAttestation.mockReset().mockReturnValue(
      of(
        new HttpResponse({
          body: new Blob(['%PDF-']),
          status: 200,
          headers: new HttpHeaders({ 'x-document-id': 'ESIC-ATT-2026-NEWONE1234' }),
        }),
      ),
    );
    api.verifyAttestation.mockReset().mockReturnValue(
      of({
        documentId: 'ESIC-ATT-2026-ABCDEFGHJK',
        documentType: 'ATTENDANCE_CERTIFICATE',
        issuedAt: '2026-09-05T08:00:00Z',
        issuedBy: 'Alix Martin',
        periodStart: '2026-09-01',
        periodEnd: '2026-09-30',
        revoked: false,
      }),
    );
    notifications.error.mockReset();

    await TestBed.configureTestingModule({
      imports: [Attestations],
      providers: [
        { provide: AttendanceApiService, useValue: api },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Attestations);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;
  const component = () =>
    fixture.componentInstance as unknown as {
      issueForm: { setValue: (v: Record<string, string>) => void };
      verifyForm: { setValue: (v: Record<string, string>) => void };
      issue: () => void;
      verify: () => void;
    };

  it('shows the registry of issued documents', () => {
    expect(text()).toContain('ESIC-ATT-2026-ABCDEFGHJK');
    expect(text()).toContain('Alix Martin');
  });

  it('shows the document identifier returned in the response header (AC-033)', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    component().issueForm.setValue({ student: 'u-1', from: '2026-09-01', to: '2026-09-30' });
    component().issue();
    fixture.detectChanges();

    expect(api.issueAttestation).toHaveBeenCalledWith(
      'u-1',
      '2026-09-01T00:00:00.000Z',
      '2026-09-30T23:59:59.999Z',
    );
    expect(text()).toContain('ESIC-ATT-2026-NEWONE1234');
    vi.restoreAllMocks();
  });

  /**
   * `Validators.required` accepte une chaîne d'espaces. Sans contrôle
   * supplémentaire, un champ « rempli » d'espaces partait au serveur, qui
   * répondait « aucun apprenant ne correspond » — un message trompeur
   * pour ce qui est un champ non rempli.
   */
  it.each(['', '   '])('never issues on a blank student identifier (%j)', (value) => {
    component().issueForm.setValue({ student: value, from: '', to: '' });
    component().issue();
    expect(api.issueAttestation).not.toHaveBeenCalled();
  });

  it('verification shows the issuance facts and no attendance data', () => {
    component().verifyForm.setValue({ documentId: 'ESIC-ATT-2026-ABCDEFGHJK' });
    component().verify();
    fixture.detectChanges();
    expect(text()).toContain('Valide');
    expect(text()).toContain('Alix Martin');
    expect(text()).toContain("La vérification ne révèle aucune donnée d'assiduité");
    expect(text()).not.toContain('Taux');
  });

  it('says plainly when no document matches', () => {
    api.verifyAttestation.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );
    component().verifyForm.setValue({ documentId: 'INCONNU' });
    component().verify();
    fixture.detectChanges();
    expect(text()).toContain('Aucune attestation ne correspond à cet identifiant.');
  });
});
