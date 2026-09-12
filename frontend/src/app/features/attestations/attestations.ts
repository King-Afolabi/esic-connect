import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { NotificationService } from '../../core/notifications/notification.service';
import { AttendanceApiService, triggerCsvDownload } from '../attendance/attendance-api.service';
import { toAttendanceError } from '../attendance/attendance-errors';
import { ReportDocumentCheck, ReportDocumentSummary } from '../attendance/attendance.models';

/**
 * Attestations d'assiduité (EF-REP-006 ; AC-033).
 *
 * Trois usages sur un même écran : **émettre** une attestation pour un
 * apprenant sur une période, **consulter le registre** des documents
 * émis, et **vérifier** un identifiant présenté par un tiers.
 *
 * La vérification est délibérément pauvre : elle dit qu'un identifiant
 * correspond à une attestation émise, par qui et quand — jamais le taux
 * d'assiduité de la personne. Quiconque détient le papier ne doit pas
 * pouvoir en apprendre davantage que ce que le papier porte déjà.
 */
@Component({
  selector: 'app-attestations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './attestations.html',
  styleUrl: './attestations.scss',
})
export class Attestations {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  protected readonly issuing = signal(false);
  protected readonly loadingRegistry = signal(true);
  protected readonly forbidden = signal(false);
  protected readonly registry = signal<ReportDocumentSummary[]>([]);
  protected readonly lastDocumentId = signal<string | null>(null);
  protected readonly verification = signal<ReportDocumentCheck | null>(null);
  protected readonly verificationFailed = signal(false);

  protected readonly issueForm = this.fb.nonNullable.group({
    student: ['', [Validators.required]],
    from: '',
    to: '',
  });

  protected readonly verifyForm = this.fb.nonNullable.group({
    documentId: ['', [Validators.required]],
  });

  constructor() {
    this.loadRegistry();
  }

  protected issue(): void {
    const raw = this.issueForm.getRawValue();
    const student = raw.student.trim();
    // `Validators.required` accepte une chaîne d'espaces : sans ce
    // contrôle, un identifiant vide partirait au serveur, qui répondrait
    // « aucun apprenant ne correspond » — un message trompeur pour ce
    // qui est en réalité un champ non rempli.
    if (!student || this.issuing()) {
      this.issueForm.markAllAsTouched();
      return;
    }
    this.issuing.set(true);
    this.api
      .issueAttestation(student, isoStart(raw.from), isoEnd(raw.to))
      .subscribe({
        next: (response) => {
          this.issuing.set(false);
          // L'identifiant arrive en en-tête : l'afficher évite d'obliger
          // à ouvrir le PDF pour savoir ce qui vient d'être émis.
          this.lastDocumentId.set(response.headers.get('x-document-id'));
          triggerCsvDownload(response, 'attestation.pdf');
          this.loadRegistry();
        },
        error: (error: unknown) => {
          this.issuing.set(false);
          this.notifications.error(toAttendanceError(error).message);
        },
      });
  }

  protected verify(): void {
    const documentId = this.verifyForm.getRawValue().documentId.trim();
    if (!documentId) {
      this.verifyForm.markAllAsTouched();
      return;
    }
    this.verification.set(null);
    this.verificationFailed.set(false);
    this.api.verifyAttestation(documentId).subscribe({
      next: (check) => this.verification.set(check),
      error: () => this.verificationFailed.set(true),
    });
  }

  private loadRegistry(): void {
    this.loadingRegistry.set(true);
    this.api.listAttestations(20).subscribe({
      next: (rows) => {
        this.registry.set(rows);
        this.loadingRegistry.set(false);
      },
      error: (error: unknown) => {
        this.loadingRegistry.set(false);
        this.forbidden.set((error as { status?: number } | null)?.status === 403);
      },
    });
  }
}

function isoStart(day: string): string | null {
  return day ? new Date(`${day}T00:00:00Z`).toISOString() : null;
}

function isoEnd(day: string): string | null {
  return day ? new Date(`${day}T23:59:59.999Z`).toISOString() : null;
}
