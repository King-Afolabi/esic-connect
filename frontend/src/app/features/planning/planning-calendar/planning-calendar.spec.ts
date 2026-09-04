import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AcademicApiService } from '../../academic/academic-api.service';
import { PlanningApiService } from '../planning-api.service';
import { PlanningCalendarView, PlanningJobResponse } from '../planning.models';
import { PlanningCalendar } from './planning-calendar';

const CLASS_ID = '11111111-1111-4111-8111-111111111111';

function view(overrides: Partial<PlanningCalendarView> = {}): PlanningCalendarView {
  return {
    classGroupPublicId: CLASS_ID,
    from: '2026-11-01',
    to: '2026-11-30',
    publishedVersionNumber: null,
    draftJobPublicId: 'job-1',
    published: [],
    draft: [
      {
        publicId: 'row-1',
        slotKey: 'CAL-1',
        day: '2026-11-09',
        startsAt: '2026-11-09T08:00:00Z',
        endsAt: '2026-11-09T11:30:00Z',
        timeZoneId: 'Europe/Paris',
        title: 'Réseaux',
        teacherPublicId: null,
        roomCode: 'A1',
        origin: 'DRAFT',
        rowStatus: 'VALID',
      },
    ],
    ...overrides,
  };
}

function job(overrides: Partial<PlanningJobResponse> = {}): PlanningJobResponse {
  return {
    publicId: 'job-1',
    status: 'SIMULATED',
    classGroupPublicId: CLASS_ID,
    academicYearPublicId: null,
    originalFileName: 'calendrier-interactif',
    fileSizeBytes: 0,
    csvSeparator: ',',
    totalRows: 1,
    validRows: 1,
    warningRows: 0,
    errorRows: 0,
    addedRows: 1,
    modifiedRows: 0,
    unchangedRows: 0,
    removedEntries: 0,
    confirmable: true,
    simulatedAt: '2026-11-01T10:00:00Z',
    expiresAt: '2026-11-02T10:00:00Z',
    publishedAt: null,
    publishedVersionPublicId: null,
    failureReason: null,
    createdAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

describe('PlanningCalendar', () => {
  let fixture: ComponentFixture<PlanningCalendar>;
  let api: {
    calendar: ReturnType<typeof vi.fn>;
    addSlot: ReturnType<typeof vi.fn>;
    updateSlot: ReturnType<typeof vi.fn>;
    removeSlot: ReturnType<typeof vi.fn>;
    duplicateWeek: ReturnType<typeof vi.fn>;
    repeatSlot: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      calendar: vi.fn().mockReturnValue(of(view())),
      addSlot: vi.fn().mockReturnValue(of(job())),
      updateSlot: vi.fn().mockReturnValue(of(job())),
      removeSlot: vi.fn().mockReturnValue(of(job({ totalRows: 0 }))),
      duplicateWeek: vi.fn().mockReturnValue(of(job({ totalRows: 2 }))),
      repeatSlot: vi.fn().mockReturnValue(of(job({ totalRows: 4 }))),
    };

    await TestBed.configureTestingModule({
      imports: [PlanningCalendar],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PlanningApiService, useValue: api },
        {
          provide: AcademicApiService,
          useValue: {
            listClassGroups: vi.fn().mockReturnValue(
              of({
                content: [{ publicId: CLASS_ID, code: 'C1', name: 'Classe 1' }],
                page: 0,
                size: 20,
                totalElements: 1,
                totalPages: 1,
              }),
            ),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PlanningCalendar);
    fixture.detectChanges();
  });

  function component(): PlanningCalendar {
    return fixture.componentInstance as PlanningCalendar;
  }

  /**
   * Les membres de l'écran sont `protected` : le test passe par une vue
   * indexée plutôt que d'élargir leur visibilité pour lui plaire.
   */
  function internals(): Record<string, unknown> {
    return component() as unknown as Record<string, unknown>;
  }

  function control(name: string): FormGroup<Record<string, FormControl<string>>> {
    return internals()[name] as FormGroup<Record<string, FormControl<string>>>;
  }

  function call(name: string, ...args: unknown[]): void {
    (internals()[name] as (...values: unknown[]) => void)(...args);
  }

  function signalValue<T>(name: string): T {
    return (internals()[name] as () => T)();
  }

  it('charge le calendrier dès qu’une classe est choisie', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);

    // La fenêtre par défaut est le mois courant : on vérifie la classe et
    // le format des bornes, pas des dates figées qui périmeraient le test.
    expect(api.calendar).toHaveBeenCalledWith(
      CLASS_ID,
      expect.stringMatching(/^\d{4}-\d{2}-01$/),
      expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
    );
  });

  it('n’ajoute rien tant que le formulaire est incomplet', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);
    call('addSlot');

    // `title` et `sessionDate` sont obligatoires : aucun appel réseau.
    expect(api.addSlot).not.toHaveBeenCalled();
  });

  it('ajoute un créneau puis recharge le calendrier', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);
    api.calendar.mockClear();
    control('slotForm').patchValue({ sessionDate: '2026-11-09', title: 'Réseaux' });
    call('addSlot');

    expect(api.addSlot).toHaveBeenCalledWith(
      CLASS_ID,
      expect.objectContaining({ sessionDate: '2026-11-09', title: 'Réseaux' }),
    );
    // Le calendrier est rechargé : le serveur seul connaît l'état réel.
    expect(api.calendar).toHaveBeenCalled();
  });

  it('un champ vide est transmis comme absent, jamais comme chaîne vide', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);
    control('slotForm').patchValue({ sessionDate: '2026-11-09', title: 'Réseaux' });
    call('addSlot');

    expect(api.addSlot).toHaveBeenCalledWith(
      CLASS_ID,
      expect.objectContaining({ teacherPublicId: null, roomCode: null }),
    );
  });

  it('modifie un créneau du brouillon par son identifiant de ligne', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);
    call('editSlot', view().draft[0]);
    call('saveEdit');

    expect(api.updateSlot).toHaveBeenCalledWith('job-1', 'row-1', expect.any(Object));
  });

  it('retire un créneau du brouillon', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);
    call('removeSlot', view().draft[0]);

    expect(api.removeSlot).toHaveBeenCalledWith('job-1', 'row-1');
  });

  it('affiche le message du serveur quand une action échoue', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);
    api.addSlot.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'PLAN_CALENDAR_NOTHING_TO_COPY', message: 'Rien à dupliquer.' },
          }),
      ),
    );
    control('slotForm').patchValue({ sessionDate: '2026-11-09', title: 'Réseaux' });
    call('addSlot');

    expect(signalValue('actionError')).toBeTruthy();
  });

  it('sépare le publié du brouillon', () => {
    control('filters').controls['classGroupPublicId'].setValue(CLASS_ID);

    expect(signalValue('draft')).toHaveLength(1);
    expect(signalValue('published')).toHaveLength(0);
  });
});
