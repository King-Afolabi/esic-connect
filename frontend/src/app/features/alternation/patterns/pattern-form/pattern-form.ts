import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  FormArray,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';

import { NotificationService } from '../../../../core/notifications/notification.service';
import { AlternationApiService } from '../../alternation-api.service';
import { fieldForAlternationCode, toAlternationError } from '../../alternation-errors';
import {
  CreatePatternRequest,
  UpdatePatternRequest,
  WEEKDAYS,
  WEEKDAY_LABELS,
  WORK_STUDY_PATTERN_TYPES,
  Weekday,
  WorkStudyPatternType,
  readCanonicalConfiguration,
  workStudyPatternTypeLabel,
} from '../../alternation.models';
import {
  PatternConfigFormValue,
  buildPatternConfiguration,
  emptyPatternConfigFormValue,
  formValueFromCanonical,
} from '../../pattern-config';
import { CyclePreview } from '../../shared/cycle-preview/cycle-preview';

type FormState =
  | { kind: 'ready' }
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'load-error'; message: string };

/**
 * Formulaire de **création** et de **modification** d'un modèle de
 * rythme. Le mode vient de `route.data.mode`. En édition, `code` et
 * `type` sont figés (le back-end les refuse : `Update` ne les porte pas).
 *
 * La `configuration` est assemblée localement par
 * {@link buildPatternConfiguration} et prévisualisée en direct, mais la
 * validation qui fait foi est celle du back-end
 * (`AlternationConfigParser` → `400 ALT_INVALID_CONFIGURATION`). Les
 * règles strictes ne sont pas relâchées côté client : les tableaux de
 * jours ne sont envoyés que lorsqu'ils sont pertinents ; `companyDays`
 * est transmis explicitement (même vide) pour un rythme `CUSTOM`.
 */
@Component({
  selector: 'app-pattern-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    CyclePreview,
  ],
  templateUrl: './pattern-form.html',
  styleUrl: './pattern-form.scss',
})
export class PatternForm {
  private readonly api = inject(AlternationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly mode: 'create' | 'edit' =
    this.route.snapshot.data['mode'] === 'edit' ? 'edit' : 'create';
  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly patternTypes = WORK_STUDY_PATTERN_TYPES;
  protected readonly typeLabel = workStudyPatternTypeLabel;
  protected readonly weekdays = WEEKDAYS;
  protected readonly weekdayLabel = (day: string): string =>
    (WEEKDAY_LABELS as Record<string, string>)[day] ?? day;
  protected readonly weekChoices = [1, 2, 3, 4];

  protected readonly state = signal<FormState>({ kind: this.mode === 'edit' ? 'loading' : 'ready' });
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  private readonly formTick = signal(0);

  protected readonly form = this.formBuilder.group({
    code: this.formBuilder.control('', [Validators.required, Validators.maxLength(80)]),
    name: this.formBuilder.control('', [Validators.required, Validators.maxLength(191)]),
    description: this.formBuilder.control('', [Validators.maxLength(500)]),
    type: this.formBuilder.control<WorkStudyPatternType>('THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY', [
      Validators.required,
    ]),
    threeTwo: this.formBuilder.group(
      Object.fromEntries(
        WEEKDAYS.map((day) => [day, this.formBuilder.control<'SCHOOL' | 'COMPANY'>('SCHOOL')]),
      ) as Record<Weekday, FormControl<'SCHOOL' | 'COMPANY'>>,
    ),
    oneWeekSchoolWeek: this.formBuilder.control(1),
    twoWeeksSchoolWeeks: this.formBuilder.control<number[]>([1, 2]),
    useCustomWeekDays: this.formBuilder.control(false),
    weeksOutOfFourSchoolDays: this.formBuilder.control<Weekday[]>([...WEEKDAYS]),
    customCycleLengthWeeks: this.formBuilder.control(4, [
      Validators.required,
      Validators.min(1),
      Validators.max(52),
    ]),
    customWeekRoles: this.formBuilder.array<FormControl<'SCHOOL' | 'COMPANY' | 'UNCLASSIFIED'>>([]),
    customSchoolDays: this.formBuilder.control<Weekday[]>([...WEEKDAYS]),
    customCompanyDays: this.formBuilder.control<Weekday[]>([]),
  });

  protected readonly selectedType = signal<WorkStudyPatternType>('THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY');

  /** Configuration assemblée + erreurs ergonomiques + aperçu du cycle. */
  protected readonly built = computed(() => {
    this.formTick();
    return buildPatternConfiguration(this.selectedType(), this.readFormValue());
  });
  protected readonly clientErrors = computed(() => this.built().errors);
  protected readonly loadErrorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'load-error' ? current.message : null;
  });

  constructor() {
    this.resetCustomWeekRoles(4);
    this.form.valueChanges.subscribe(() => this.formTick.update((n) => n + 1));
    this.form.controls.type.valueChanges.subscribe((type) => this.selectedType.set(type));
    this.form.controls.customCycleLengthWeeks.valueChanges.subscribe((length) => {
      if (Number.isInteger(length) && length > 0 && length <= 52) {
        this.resetCustomWeekRoles(length);
      }
    });
    if (this.mode === 'edit') {
      this.loadForEdit();
    } else {
      this.applyFormValue(emptyPatternConfigFormValue());
    }
  }

  protected get customWeekRoles(): FormArray<FormControl<'SCHOOL' | 'COMPANY' | 'UNCLASSIFIED'>> {
    return this.form.controls.customWeekRoles;
  }

  protected retryLoad(): void {
    this.loadForEdit();
  }

  protected submit(): void {
    this.submitError.set(null);
    if (this.form.controls.name.invalid || (this.mode === 'create' && this.form.controls.code.invalid)) {
      this.form.markAllAsTouched();
      return;
    }
    const built = this.built();
    if (built.errors.length > 0 || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const raw = this.form.getRawValue();
    const description = raw.description.trim() ? raw.description.trim() : null;

    if (this.mode === 'edit') {
      const body: UpdatePatternRequest = {
        name: raw.name.trim(),
        description,
        configuration: built.configuration,
      };
      if (built.cycleLengthWeeks !== null) {
        body.cycleLengthWeeks = built.cycleLengthWeeks;
      }
      this.api.updatePattern(this.publicId, body).subscribe({
        next: (pattern) => this.onSaved(pattern.publicId, 'Modèle de rythme mis à jour.'),
        error: (error: unknown) => this.onSubmitError(error),
      });
      return;
    }

    const body: CreatePatternRequest = {
      code: raw.code.trim(),
      name: raw.name.trim(),
      description,
      type: raw.type,
      configuration: built.configuration,
    };
    if (built.cycleLengthWeeks !== null) {
      body.cycleLengthWeeks = built.cycleLengthWeeks;
    }
    this.api.createPattern(body).subscribe({
      next: (pattern) => this.onSaved(pattern.publicId, 'Modèle de rythme créé.'),
      error: (error: unknown) => this.onSubmitError(error),
    });
  }

  private onSaved(publicId: string, message: string): void {
    this.submitting.set(false);
    this.notifications.info(message);
    void this.router.navigate(['/alternation/patterns', publicId]);
  }

  private onSubmitError(error: unknown): void {
    this.submitting.set(false);
    const view = toAlternationError(error);
    const field = fieldForAlternationCode(view.code);
    if (field === 'code' && this.mode === 'create') {
      this.form.controls.code.setErrors({ server: view.message });
    }
    this.submitError.set(view.message);
  }

  private loadForEdit(): void {
    this.state.set({ kind: 'loading' });
    this.api.getPattern(this.publicId).subscribe({
      next: (pattern) => {
        this.form.controls.code.setValue(pattern.code);
        this.form.controls.code.disable();
        this.form.controls.name.setValue(pattern.name);
        this.form.controls.description.setValue(pattern.description ?? '');
        this.form.controls.type.setValue(pattern.type);
        this.form.controls.type.disable();
        this.selectedType.set(pattern.type);
        this.applyFormValue(
          formValueFromCanonical(pattern.type, readCanonicalConfiguration(pattern.configuration)),
        );
        this.state.set({ kind: 'ready' });
      },
      error: (error: unknown) => {
        const view = toAlternationError(error);
        if (view.notFound) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        this.state.set(
          view.forbidden
            ? { kind: 'forbidden' }
            : { kind: 'load-error', message: view.message },
        );
      },
    });
  }

  private resetCustomWeekRoles(length: number): void {
    const array = this.form.controls.customWeekRoles;
    const current = array.getRawValue();
    array.clear({ emitEvent: false });
    for (let i = 0; i < length; i += 1) {
      const value = current[i] ?? (i === 0 ? 'SCHOOL' : 'COMPANY');
      array.push(this.formBuilder.control<'SCHOOL' | 'COMPANY' | 'UNCLASSIFIED'>(value), {
        emitEvent: false,
      });
    }
    this.formTick.update((n) => n + 1);
  }

  private readFormValue(): PatternConfigFormValue {
    const raw = this.form.getRawValue();
    return {
      threeTwoDays: raw.threeTwo as Record<Weekday, 'SCHOOL' | 'COMPANY'>,
      oneWeekSchoolWeek: raw.oneWeekSchoolWeek,
      twoWeeksSchoolWeeks: raw.twoWeeksSchoolWeeks,
      weeksOutOfFourSchoolDays: raw.useCustomWeekDays ? raw.weeksOutOfFourSchoolDays : null,
      customCycleLengthWeeks: raw.customCycleLengthWeeks,
      customWeekRoles: raw.customWeekRoles,
      customSchoolDays: raw.customSchoolDays,
      customCompanyDays: raw.customCompanyDays,
    };
  }

  private applyFormValue(value: PatternConfigFormValue): void {
    this.form.controls.threeTwo.setValue(value.threeTwoDays, { emitEvent: false });
    this.form.controls.oneWeekSchoolWeek.setValue(value.oneWeekSchoolWeek, { emitEvent: false });
    this.form.controls.twoWeeksSchoolWeeks.setValue(value.twoWeeksSchoolWeeks, {
      emitEvent: false,
    });
    this.form.controls.useCustomWeekDays.setValue(value.weeksOutOfFourSchoolDays !== null, {
      emitEvent: false,
    });
    this.form.controls.weeksOutOfFourSchoolDays.setValue(
      value.weeksOutOfFourSchoolDays ?? [...WEEKDAYS],
      { emitEvent: false },
    );
    this.form.controls.customCycleLengthWeeks.setValue(value.customCycleLengthWeeks, {
      emitEvent: false,
    });
    this.resetCustomWeekRoles(value.customCycleLengthWeeks);
    this.customWeekRoles.setValue(
      value.customWeekRoles.slice(0, value.customCycleLengthWeeks),
      { emitEvent: false },
    );
    this.form.controls.customSchoolDays.setValue(value.customSchoolDays, { emitEvent: false });
    this.form.controls.customCompanyDays.setValue(value.customCompanyDays, { emitEvent: false });
    this.formTick.update((n) => n + 1);
  }
}
