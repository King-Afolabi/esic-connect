import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, forkJoin } from 'rxjs';

import { normalizeHttpError } from '../../../core/models/api-error';
import { OrganizationApiService } from '../../organization/organization-api.service';
import { SiteResponse } from '../../organization/organization.models';
import { AcademicApiService } from '../academic-api.service';
import {
  AcademicRecord,
  AcademicResourceSlug,
  AcademicYearResponse,
  PROGRAM_TYPES,
  ProgramLevelResponse,
  ProgramResponse,
  PromotionResponse,
  programTypeLabel,
} from '../academic.models';

type Mode = 'create' | 'edit';

type FormState =
  | { kind: 'ready' }
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'load-error'; message: string };

/** Option d'un `<mat-select>` (`value` = `public_id` réel). */
interface SelectOption {
  value: string;
  label: string;
}

const CODE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/;

/**
 * Création et modification du référentiel académique
 * (`com.esic.connect.academic`) — années scolaires, formations, niveaux,
 * promotions, classes (docs/02 §39.1 : *« l'administrateur crée une
 * formation, un niveau et une année ; le responsable crée une promotion
 * et une classe »*).
 *
 * Piloté par `route.data.resource` (comme la liste et la fiche détail) et
 * `route.data.mode` (`create` / `edit`). Le `code` et les rattachements
 * parent (formation, année, promotion, niveau, site) sont **immuables**
 * après création — l'API back-end ne les accepte pas dans `Update`, ils
 * sont donc masqués ou affichés en lecture seule en mode édition, jamais
 * soumis.
 *
 * Un niveau se crée **sous une formation** (`programPublicId` dans
 * l'URL, `/academic/programs/:programPublicId/levels/new`) : il n'a pas
 * de liste autonome (`AcademicResourceConfig.hasList === false`).
 *
 * Les erreurs serveur ne sont pas rattachées à un champ précis (pas de
 * mapping `field` exposé côté académique, contrairement à l'organisation) :
 * un message global suffit, cohérent avec le reste du module (lecture
 * seule jusqu'ici).
 */
@Component({
  selector: 'app-academic-reference-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './academic-reference-form.html',
  styleUrl: './academic-reference-form.scss',
})
export class AcademicReferenceForm {
  private readonly api = inject(AcademicApiService);
  private readonly orgApi = inject(OrganizationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly resource = this.route.snapshot.data['resource'] as AcademicResourceSlug;
  protected readonly mode: Mode = this.route.snapshot.data['mode'] === 'edit' ? 'edit' : 'create';
  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
  /** Formation parente — niveaux uniquement, présente seulement en création. */
  private readonly parentProgramPublicId =
    this.route.snapshot.paramMap.get('programPublicId') ?? '';

  protected readonly programTypes = PROGRAM_TYPES;
  protected readonly programTypeLabel = programTypeLabel;

  protected readonly state = signal<FormState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  /** Options des sélecteurs dépendant d'une autre ressource (formations, années, promotions, sites…). */
  protected readonly programOptions = signal<SelectOption[]>([]);
  protected readonly academicYearOptions = signal<SelectOption[]>([]);
  protected readonly promotionOptions = signal<SelectOption[]>([]);
  protected readonly siteOptions = signal<SelectOption[]>([]);
  protected readonly levelOptions = signal<SelectOption[]>([]);
  protected readonly levelsLoading = signal(false);
  /** Programme de la promotion sélectionnée (classes) — connu une fois choisie. */
  private promotionProgramId: string | null = null;

  protected readonly form: FormGroup = this.buildForm();

  protected readonly title = computed(() => {
    const titles: Record<AcademicResourceSlug, { create: string; edit: string }> = {
      'academic-years': { create: 'Nouvelle année scolaire', edit: 'Modifier une année scolaire' },
      programs: { create: 'Nouvelle formation', edit: 'Modifier une formation' },
      'program-levels': { create: 'Nouveau niveau', edit: 'Modifier un niveau' },
      promotions: { create: 'Nouvelle promotion', edit: 'Modifier une promotion' },
      'class-groups': { create: 'Nouvelle classe', edit: 'Modifier une classe' },
    };
    return titles[this.resource][this.mode];
  });

  protected readonly loadErrorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'load-error' ? current.message : null;
  });

  /** Lien « annuler » — vers la liste (création) ou la fiche (édition). */
  protected readonly cancelLink = computed<readonly unknown[]>(() => {
    if (this.mode === 'edit' && this.publicId) {
      return ['/academic', this.resource, this.publicId];
    }
    if (this.resource === 'program-levels' && this.parentProgramPublicId) {
      return ['/academic', 'programs', this.parentProgramPublicId];
    }
    return ['/academic', this.resource];
  });

  constructor() {
    this.loadOptionsThenForm();
  }

  protected retryLoad(): void {
    this.loadOptionsThenForm();
  }

  /** Rechargement des niveaux d'une promotion choisie (classes, création). */
  protected onPromotionChange(promotionPublicId: string): void {
    this.form.get('programLevelPublicId')?.setValue('');
    this.levelOptions.set([]);
    const promotion = this.lastPromotions.find((p) => p.publicId === promotionPublicId);
    if (!promotion) {
      return;
    }
    this.promotionProgramId = promotion.programPublicId;
    this.levelsLoading.set(true);
    this.api.listProgramLevels(promotion.programPublicId, { size: 100, sort: 'sequenceNumber,asc' }).subscribe({
      next: (page) => {
        this.levelsLoading.set(false);
        this.levelOptions.set(
          page.content
            .filter((l) => l.status === 'ACTIVE')
            .map((l) => ({ value: l.publicId, label: `${l.code} — ${l.name}` })),
        );
      },
      error: () => this.levelsLoading.set(false),
    });
  }

  private lastPromotions: PromotionResponse[] = [];

  protected submit(): void {
    this.submitError.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const raw = this.form.getRawValue() as Record<string, string | number | null>;
    const call = this.buildSubmitCall(raw);
    call.subscribe({
      next: (record) => this.onSaved(record),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.submitError.set(normalizeHttpError(error).message);
      },
    });
  }

  private buildSubmitCall(raw: Record<string, string | number | null>): Observable<AcademicRecord> {
    const trimmed = (key: string): string => String(raw[key] ?? '').trim();
    const optionalDate = (key: string): string | null => trimmed(key) || null;
    const optionalText = (key: string): string | null => trimmed(key) || null;
    const optionalNumber = (key: string): number | null =>
      raw[key] === null || raw[key] === '' || raw[key] === undefined ? null : Number(raw[key]);

    switch (this.resource) {
      case 'academic-years':
        return this.mode === 'create'
          ? this.api.createAcademicYear({
              code: trimmed('code'),
              name: trimmed('name'),
              startDate: trimmed('startDate'),
              endDate: trimmed('endDate'),
            })
          : this.api.updateAcademicYear(this.publicId, {
              name: trimmed('name'),
              startDate: trimmed('startDate'),
              endDate: trimmed('endDate'),
            });
      case 'programs':
        return this.mode === 'create'
          ? this.api.createProgram({
              code: trimmed('code'),
              name: trimmed('name'),
              programType: trimmed('programType') as ProgramResponse['programType'],
              description: optionalText('description'),
            })
          : this.api.updateProgram(this.publicId, {
              name: trimmed('name'),
              programType: trimmed('programType') as ProgramResponse['programType'],
              description: optionalText('description'),
            });
      case 'program-levels':
        return this.mode === 'create'
          ? this.api.createProgramLevel(this.parentProgramPublicId, {
              code: trimmed('code'),
              name: trimmed('name'),
              sequenceNumber: Number(raw['sequenceNumber']),
            })
          : this.api.updateProgramLevel(this.publicId, {
              name: trimmed('name'),
              sequenceNumber: Number(raw['sequenceNumber']),
            });
      case 'promotions':
        return this.mode === 'create'
          ? this.api.createPromotion({
              programPublicId: trimmed('programPublicId'),
              academicYearPublicId: trimmed('academicYearPublicId'),
              code: trimmed('code'),
              name: trimmed('name'),
              startDate: optionalDate('startDate'),
              endDate: optionalDate('endDate'),
            })
          : this.api.updatePromotion(this.publicId, {
              name: trimmed('name'),
              startDate: optionalDate('startDate'),
              endDate: optionalDate('endDate'),
            });
      case 'class-groups':
        return this.mode === 'create'
          ? this.api.createClassGroup({
              promotionPublicId: trimmed('promotionPublicId'),
              programLevelPublicId: trimmed('programLevelPublicId'),
              sitePublicId: trimmed('sitePublicId'),
              code: trimmed('code'),
              name: trimmed('name'),
              capacity: optionalNumber('capacity'),
            })
          : this.api.updateClassGroup(this.publicId, {
              name: trimmed('name'),
              capacity: optionalNumber('capacity'),
            });
    }
  }

  private onSaved(record: AcademicRecord): void {
    this.submitting.set(false);
    void this.router.navigate(['/academic', this.resource, record.publicId]);
  }

  private buildForm(): FormGroup {
    const fb = this.formBuilder;
    const codeValidators = [
      Validators.required,
      Validators.maxLength(80),
      Validators.pattern(CODE_PATTERN),
    ];
    switch (this.resource) {
      case 'academic-years':
        return fb.group({
          code: fb.control('', codeValidators),
          name: fb.control('', [Validators.required, Validators.maxLength(100)]),
          startDate: fb.control('', Validators.required),
          endDate: fb.control('', Validators.required),
        });
      case 'programs':
        return fb.group({
          code: fb.control('', codeValidators),
          name: fb.control('', [Validators.required, Validators.maxLength(191)]),
          programType: fb.control('BTS', Validators.required),
          description: fb.control('', Validators.maxLength(5000)),
        });
      case 'program-levels':
        return fb.group({
          code: fb.control('', codeValidators),
          name: fb.control('', [Validators.required, Validators.maxLength(100)]),
          sequenceNumber: fb.control<number | null>(1, [
            Validators.required,
            Validators.min(1),
            Validators.max(9999),
          ]),
        });
      case 'promotions':
        return fb.group({
          programPublicId: fb.control('', Validators.required),
          academicYearPublicId: fb.control('', Validators.required),
          code: fb.control('', codeValidators),
          name: fb.control('', [Validators.required, Validators.maxLength(191)]),
          startDate: fb.control(''),
          endDate: fb.control(''),
        });
      case 'class-groups':
        return fb.group({
          promotionPublicId: fb.control('', Validators.required),
          programLevelPublicId: fb.control('', Validators.required),
          sitePublicId: fb.control('', Validators.required),
          code: fb.control('', codeValidators),
          name: fb.control('', [Validators.required, Validators.maxLength(191)]),
          capacity: fb.control<number | null>(null, Validators.min(1)),
        });
    }
  }

  /**
   * Charge d'abord les options des sélecteurs (formations, années,
   * promotions, sites — toujours actives) puis, en édition, la fiche à
   * modifier pour préremplir le formulaire. Tout est chargé avant
   * d'afficher le formulaire : un sélecteur vide le temps d'un
   * chargement partiel serait trompeur.
   */
  private loadOptionsThenForm(): void {
    this.state.set({ kind: 'loading' });
    const optionLoaders: Observable<unknown>[] = [];

    if (this.resource === 'promotions') {
      optionLoaders.push(
        this.api.listPrograms({ status: 'ACTIVE', size: 200, sort: 'code,asc' }),
        this.api.listAcademicYears({ status: 'ACTIVE', size: 200, sort: 'code,asc' }),
      );
    }
    if (this.resource === 'class-groups') {
      optionLoaders.push(
        this.api.listPromotions({ status: 'ACTIVE', size: 200, sort: 'code,asc' }),
        this.orgApi.listSites({ status: 'ACTIVE', size: 200, sort: 'code,asc' }),
      );
    }

    if (optionLoaders.length === 0) {
      this.afterOptionsLoaded();
      return;
    }

    forkJoin(optionLoaders).subscribe({
      next: (results) => {
        if (this.resource === 'promotions') {
          const [programs, years] = results as [
            { content: ProgramResponse[] },
            { content: AcademicYearResponse[] },
          ];
          this.programOptions.set(
            programs.content.map((p) => ({ value: p.publicId, label: `${p.code} — ${p.name}` })),
          );
          this.academicYearOptions.set(
            years.content.map((y) => ({ value: y.publicId, label: `${y.code} — ${y.name}` })),
          );
        }
        if (this.resource === 'class-groups') {
          const [promotions, sites] = results as [
            { content: PromotionResponse[] },
            { content: SiteResponse[] },
          ];
          this.lastPromotions = promotions.content;
          this.promotionOptions.set(
            promotions.content.map((p) => ({ value: p.publicId, label: `${p.code} — ${p.name}` })),
          );
          this.siteOptions.set(sites.content.map((s) => ({ value: s.publicId, label: s.name })));
        }
        this.afterOptionsLoaded();
      },
      error: (error: unknown) => {
        this.state.set({ kind: 'load-error', message: normalizeHttpError(error).message });
      },
    });
  }

  private afterOptionsLoaded(): void {
    if (this.mode !== 'edit') {
      this.state.set({ kind: 'ready' });
      return;
    }
    this.loadForEdit();
  }

  private loadForEdit(): void {
    const loader = this.editLoader();
    loader.subscribe({
      next: (record) => {
        this.fillFormForEdit(record);
        this.state.set({ kind: 'ready' });
      },
      error: (error: unknown) => {
        const normalized = normalizeHttpError(error);
        if (normalized.status === 404) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        if (normalized.status === 403) {
          this.state.set({ kind: 'forbidden' });
          return;
        }
        this.state.set({ kind: 'load-error', message: normalized.message });
      },
    });
  }

  private editLoader(): Observable<AcademicRecord> {
    switch (this.resource) {
      case 'academic-years':
        return this.api.getAcademicYear(this.publicId);
      case 'programs':
        return this.api.getProgram(this.publicId);
      case 'program-levels':
        return this.api.getProgramLevel(this.publicId);
      case 'promotions':
        return this.api.getPromotion(this.publicId);
      case 'class-groups':
        return this.api.getClassGroup(this.publicId);
    }
  }

  private fillFormForEdit(record: AcademicRecord): void {
    switch (this.resource) {
      case 'academic-years': {
        const r = record as AcademicYearResponse;
        this.form.patchValue({ code: r.code, name: r.name, startDate: r.startDate, endDate: r.endDate });
        this.form.get('code')?.disable();
        return;
      }
      case 'programs': {
        const r = record as ProgramResponse;
        this.form.patchValue({
          code: r.code,
          name: r.name,
          programType: r.programType,
          description: r.description ?? '',
        });
        this.form.get('code')?.disable();
        return;
      }
      case 'program-levels': {
        const r = record as ProgramLevelResponse;
        this.form.patchValue({ code: r.code, name: r.name, sequenceNumber: r.sequenceNumber });
        this.form.get('code')?.disable();
        return;
      }
      case 'promotions': {
        const r = record as PromotionResponse;
        this.form.patchValue({
          programPublicId: r.programPublicId,
          academicYearPublicId: r.academicYearPublicId,
          code: r.code,
          name: r.name,
          startDate: r.startDate ?? '',
          endDate: r.endDate ?? '',
        });
        this.form.get('code')?.disable();
        this.form.get('programPublicId')?.disable();
        this.form.get('academicYearPublicId')?.disable();
        return;
      }
      case 'class-groups': {
        const r = record as import('../academic.models').ClassGroupResponse;
        this.form.patchValue({
          promotionPublicId: r.promotionPublicId,
          programLevelPublicId: r.programLevelPublicId,
          sitePublicId: r.sitePublicId ?? '',
          code: r.code,
          name: r.name,
          capacity: r.capacity,
        });
        this.form.get('code')?.disable();
        this.form.get('promotionPublicId')?.disable();
        this.form.get('programLevelPublicId')?.disable();
        this.form.get('sitePublicId')?.disable();
        return;
      }
    }
  }
}
