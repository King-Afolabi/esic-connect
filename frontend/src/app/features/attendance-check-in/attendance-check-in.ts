import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { RoleContextService } from '../../core/auth/role-context.service';
import { ConnectivityService } from '../../core/pwa/connectivity.service';
import { OfflineQueueService } from '../../core/pwa/offline-queue.service';
import { SessionsApiService } from '../sessions/sessions-api.service';
import { toSessionError } from '../sessions/session-errors';
import { AttendanceRecordResponse, formatInstantUtc } from '../sessions/sessions.models';
import { parseCheckInReference } from '../attendance/check-in-reference';
import { allowedInternalOrigins } from '../attendance/public-origin';
import { QrScanner } from '../attendance/qr-scanner/qr-scanner';

/** Longueur défensive du champ code court (le serveur revalide). */
const SHORT_CODE_MAX_LENGTH = 32;

/** Idem pour le jeton d'affiche de salle. */
const ROOM_REFERENCE_MAX_LENGTH = 128;

/** Message affiché quand un QR scanné n'est pas reconnu par ESIC Connect. */
const UNSUPPORTED_QR_MESSAGE =
  "Ce QR code n'est pas un code d'émargement ESIC Connect. Scannez le code affiché par votre " +
  'formateur ou sur l\'écriteau de la salle, ou saisissez un code court.';

type CheckInState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'success'; record: AttendanceRecordResponse }
  /**
   * Action mise en file faute de réseau (EF-PWA-003 ; RG-063, AC-031).
   * **Ce n'est pas un succès** : la présence n'existe pas tant que le
   * serveur ne l'a pas validée, et l'écran doit le dire sans ambiguïté.
   */
  | { kind: 'queued' }
  | { kind: 'error'; message: string };

/**
 * Écran d'émargement de l'apprenant.
 *
 * Trois entrées, une seule autorité — le serveur :
 * - **scan caméra** d'un QR (`app-qr-scanner`) : le contenu est analysé
 *   localement (`parseCheckInReference`) puis transmis **tel quel** à
 *   `POST /api/v1/attendance/validate` (jeton dynamique) ou
 *   `POST /api/v1/attendance/room-qr` (URL de salle). Le frontend ne
 *   décide jamais qu'une présence est valide ;
 * - **code court** affiché par le formateur (`POST /attendance/validate`) ;
 * - **code du QR fixe de salle** saisi à la main
 *   (`POST /attendance/room-qr`).
 *
 * Un lien profond `/attendance?ref=<opaque>` (QR fixe / tag NFC ouvert
 * par l'appareil photo système) pré-remplit le champ « code du QR de
 * salle » ; **rien n'est envoyé sans action de l'apprenant**.
 *
 * Le serveur détermine l'apprenant à partir du seul JWT : aucun
 * identifiant d'apprenant, d'inscription ni de salle « choisie » n'est
 * transmis. Rien n'est conservé (ni `localStorage`, ni `sessionStorage`).
 *
 * Saisie et soumission ne sont possibles que dans un **contexte de rôle
 * `STUDENT` effectif**. Quitter ce contexte efface immédiatement code,
 * récépissé, erreurs et ferme le scanner.
 */
@Component({
  selector: 'app-attendance-check-in',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    QrScanner,
  ],
  templateUrl: './attendance-check-in.html',
  styleUrl: './attendance-check-in.scss',
})
export class AttendanceCheckIn {
  private readonly api = inject(SessionsApiService);
  private readonly roleContext = inject(RoleContextService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly queue = inject(OfflineQueueService);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);

  protected readonly connectivity = inject(ConnectivityService);

  protected readonly shortCodeMaxLength = SHORT_CODE_MAX_LENGTH;
  protected readonly roomReferenceMaxLength = ROOM_REFERENCE_MAX_LENGTH;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly form = this.formBuilder.group({
    shortCode: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(SHORT_CODE_MAX_LENGTH),
    ]),
    /**
     * Suivi à distance déclaré (EF-ENR-004 ; docs/02 §15.3). Sur une
     * séance présentielle, le serveur exige une autorisation active.
     */
    remote: this.formBuilder.control(false),
  });

  /**
   * QR fixe de salle (EF-ATT-010) — parcours distinct : le jeton vient de
   * l'affiche, pas du formateur, et n'est accepté que depuis le réseau de
   * l'établissement (EF-ATT-008).
   */
  protected readonly roomForm = this.formBuilder.group({
    roomReference: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(ROOM_REFERENCE_MAX_LENGTH),
    ]),
  });

  protected readonly state = signal<CheckInState>({ kind: 'idle' });
  /** Scanner caméra ouvert (uniquement après clic — jamais au chargement). */
  protected readonly scannerOpen = signal(false);
  /** Un lien profond `?ref=` a pré-rempli le champ « QR de salle ». */
  protected readonly prefilledFromLink = signal(false);

  /** L'émargement n'est possible qu'en contexte de rôle `STUDENT` effectif. */
  protected readonly canCheckIn = computed(() => this.roleContext.effectiveRoles().includes('STUDENT'));

  protected readonly submitting = computed(() => this.state().kind === 'submitting');
  protected readonly successRecord = computed(() => {
    const current = this.state();
    return current.kind === 'success' ? current.record : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly queued = computed(() => this.state().kind === 'queued');

  constructor() {
    // Sortie du contexte STUDENT : on efface tout et on coupe le scanner.
    effect(() => {
      if (!this.canCheckIn()) {
        this.state.set({ kind: 'idle' });
        this.scannerOpen.set(false);
        this.prefilledFromLink.set(false);
        this.form.reset({ shortCode: '', remote: false });
        this.roomForm.reset({ roomReference: '' });
      }
    });

    // Lien profond `/attendance?ref=<opaque>` (QR fixe / tag NFC ouvert
    // par l'appareil photo système) : on pré-remplit le champ « QR de
    // salle », sans jamais soumettre automatiquement.
    const ref = this.route.snapshot.queryParamMap.get('ref');
    if (ref) {
      const parsed = parseCheckInReference(ref, {
        allowedOrigins: allowedInternalOrigins(),
        bareStringIsRoomReference: true,
      });
      if (parsed.kind === 'STATIC_ROOM_REFERENCE') {
        this.roomForm.controls.roomReference.setValue(parsed.roomReference);
        this.prefilledFromLink.set(true);
      }
    }
  }

  // --- Scanner caméra -------------------------------------------------

  protected openScanner(): void {
    if (!this.canCheckIn()) {
      return;
    }
    this.state.set({ kind: 'idle' });
    this.scannerOpen.set(true);
  }

  protected closeScanner(): void {
    this.scannerOpen.set(false);
  }

  /**
   * L'utilisateur choisit la saisie manuelle depuis le scanner. Le
   * scanner se referme et le focus revient sur le champ « Code court »
   * (accessibilité : après la fermeture d'un composant plein écran, le
   * focus doit atterrir sur l'élément d'action suivant, jamais rester
   * « nulle part »). `afterNextRender` — et non `queueMicrotask` — parce
   * que le champ n'est réinséré dans le DOM qu'au prochain rendu (zoneless).
   */
  protected scannerFallback(): void {
    this.scannerOpen.set(false);
    afterNextRender(
      () => document.getElementById('checkin-short-code')?.focus(),
      { injector: this.injector },
    );
  }

  /** Un QR a été décodé : analyse locale puis appel de l'API existante. */
  protected onScanned(raw: string): void {
    this.scannerOpen.set(false);
    if (!this.canCheckIn()) {
      return;
    }
    const parsed = parseCheckInReference(raw, { allowedOrigins: allowedInternalOrigins() });
    switch (parsed.kind) {
      case 'DYNAMIC_ATTENDANCE_TOKEN':
        this.runValidation({ token: parsed.token }, 'Émargement par QR dynamique');
        break;
      case 'STATIC_ROOM_REFERENCE':
        this.runRoomQr(parsed.roomReference);
        break;
      default:
        this.state.set({ kind: 'error', message: UNSUPPORTED_QR_MESSAGE });
    }
  }

  // --- Soumissions --------------------------------------------------

  protected submit(): void {
    if (!this.canCheckIn()) {
      return;
    }
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const shortCode = normalizeShortCode(this.form.getRawValue().shortCode);
    if (!shortCode) {
      this.form.controls.shortCode.setErrors({ required: true });
      this.form.markAllAsTouched();
      return;
    }
    const remote = this.form.getRawValue().remote || null;
    this.runValidation({ shortCode, remote }, 'Émargement par code court', () =>
      this.form.reset({ shortCode: '', remote: false }),
    );
  }

  /** Émargement par le QR fixe affiché dans la salle (EF-ATT-010). */
  protected submitRoomQr(): void {
    if (!this.canCheckIn()) {
      return;
    }
    if (this.roomForm.invalid || this.submitting()) {
      this.roomForm.markAllAsTouched();
      return;
    }
    const roomReference = this.roomForm.getRawValue().roomReference.trim();
    if (!roomReference) {
      this.roomForm.controls.roomReference.setErrors({ required: true });
      this.roomForm.markAllAsTouched();
      return;
    }
    this.runRoomQr(roomReference);
  }

  protected reset(): void {
    this.state.set({ kind: 'idle' });
    this.prefilledFromLink.set(false);
    this.form.reset({ shortCode: '', remote: false });
    this.roomForm.reset({ roomReference: '' });
  }

  // --- Cœur partagé : une seule voie de validation ------------------

  /**
   * Appelle `POST /api/v1/attendance/validate` (jeton dynamique **ou**
   * code court). Hors ligne, l'action est mise en file et rejouée au
   * retour du réseau — l'écran annonce « en attente de confirmation »,
   * jamais un émargement acquis (RG-063, AC-031).
   */
  private runValidation(
    body: { token?: string; shortCode?: string; remote?: boolean | null },
    label: string,
    onSuccessReset?: () => void,
  ): void {
    if (!this.connectivity.online()) {
      this.queue.enqueue(label, '/v1/attendance/validate', body);
      this.state.set({ kind: 'queued' });
      onSuccessReset?.();
      return;
    }
    this.state.set({ kind: 'submitting' });
    this.api.validateAttendance(body).subscribe({
      next: (record) => {
        if (!this.canCheckIn()) {
          return;
        }
        this.state.set({ kind: 'success', record });
        onSuccessReset?.();
      },
      error: (error: unknown) => {
        if (!this.canCheckIn()) {
          return;
        }
        this.state.set({ kind: 'error', message: toSessionError(error).message });
      },
    });
  }

  /** Appelle `POST /api/v1/attendance/room-qr` — le serveur applique la plage réseau. */
  private runRoomQr(roomReference: string): void {
    this.state.set({ kind: 'submitting' });
    this.api.validateRoomQr({ roomReference }).subscribe({
      next: (record) => {
        if (!this.canCheckIn()) {
          return;
        }
        this.state.set({ kind: 'success', record });
        this.prefilledFromLink.set(false);
        this.roomForm.reset({ roomReference: '' });
      },
      error: (error: unknown) => {
        if (!this.canCheckIn()) {
          return;
        }
        this.state.set({ kind: 'error', message: toSessionError(error).message });
      },
    });
  }
}

/** Majuscules, sans espaces ni séparateurs — cohérent avec le back-end. */
function normalizeShortCode(value: string): string {
  return value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}
