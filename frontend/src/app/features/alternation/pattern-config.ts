import {
  AlternationContext,
  CanonicalPatternConfiguration,
  WEEKDAYS,
  Weekday,
  WorkStudyPatternType,
} from './alternation.models';

/**
 * Aides **pures** (aucune I/O) pour composer et prévisualiser la
 * `configuration` d'un modèle de rythme.
 *
 * - {@link buildPatternConfiguration} assemble le corps `configuration`
 *   attendu par le back-end selon le type, en signalant les incohérences
 *   ergonomiques évidentes (intersections, jours non classifiés…). Le
 *   back-end reste l'autorité de validation
 *   (`AlternationConfigParser` → `400 ALT_INVALID_CONFIGURATION`).
 * - {@link buildCyclePreview} produit une **représentation** de la
 *   configuration canonique (grille semaine × jour). Ce n'est PAS une
 *   résolution de contexte pour une date réelle : la position dans le
 *   cycle (ancre, modulo) et la décision effective d'une date viennent
 *   uniquement de l'endpoint de contexte du back-end.
 */

/** Saisie du formulaire, indépendante du type choisi. */
export interface PatternConfigFormValue {
  /** Rôle de chaque jour MON..FRI pour le type 3 jours / 2 jours. */
  threeTwoDays: Record<Weekday, 'SCHOOL' | 'COMPANY'>;
  /** Semaine (1..4) marquée « école » pour le type « 1 semaine sur 4 ». */
  oneWeekSchoolWeek: number;
  /** Semaines (1..4) marquées « école » pour le type « 2 semaines sur 4 ». */
  twoWeeksSchoolWeeks: number[];
  /**
   * Jours « école » optionnels pour les rythmes semaine/4 : `null` = ne
   * pas envoyer la clé (défaut serveur MON..FRI).
   */
  weeksOutOfFourSchoolDays: Weekday[] | null;
  /** Longueur du cycle pour un rythme CUSTOM (strictement positive). */
  customCycleLengthWeeks: number;
  /** Rôle de chaque semaine 1..N d'un rythme CUSTOM. */
  customWeekRoles: ('SCHOOL' | 'COMPANY' | 'UNCLASSIFIED')[];
  /** Jours « école » d'un rythme CUSTOM (défaut MON..FRI si non modifié). */
  customSchoolDays: Weekday[];
  /** Jours « entreprise » d'un rythme CUSTOM (peut être vide). */
  customCompanyDays: Weekday[];
}

export interface BuiltPatternConfiguration {
  /** Corps JSON de la clé `configuration`. */
  configuration: Record<string, unknown>;
  /** Valeur à envoyer dans `cycleLengthWeeks` (ou `null` pour l'omettre). */
  cycleLengthWeeks: number | null;
  /** Messages d'incohérence détectés côté client (vide = prêt à envoyer). */
  errors: string[];
  /**
   * Forme canonique équivalente, pour la prévisualisation locale du
   * cycle. Reproduit les valeurs par défaut appliquées par le back-end
   * (`schoolDays` défaut MON..FRI pour les rythmes semaine/4 ;
   * `schoolWeeks:[1]` pour le rythme 3 jours / 2 jours). Ne remplace
   * jamais la résolution de contexte serveur.
   */
  preview: CanonicalPatternConfiguration;
}

function weekList(count: number): number[] {
  return Array.from({ length: Math.max(0, count) }, (_, i) => i + 1);
}

/**
 * Assemble la `configuration` pour un type donné. Ne relâche jamais les
 * règles strictes des requêtes clientes : les tableaux de jours ne sont
 * envoyés que lorsqu'ils sont pertinents, et jamais vides là où le
 * back-end exige une liste non vide (rythme 3 jours / 2 jours).
 */
export function buildPatternConfiguration(
  type: WorkStudyPatternType,
  value: PatternConfigFormValue,
): BuiltPatternConfiguration {
  switch (type) {
    case 'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY':
      return buildThreeTwo(value);
    case 'ONE_WEEK_SCHOOL_OUT_OF_FOUR':
      return buildWeeksOutOfFour(value, 1);
    case 'TWO_WEEKS_SCHOOL_OUT_OF_FOUR':
      return buildWeeksOutOfFour(value, 2);
    case 'CUSTOM':
      return buildCustom(value);
  }
}

function buildThreeTwo(value: PatternConfigFormValue): BuiltPatternConfiguration {
  const schoolDays = WEEKDAYS.filter((d) => value.threeTwoDays[d] === 'SCHOOL');
  const companyDays = WEEKDAYS.filter((d) => value.threeTwoDays[d] === 'COMPANY');
  const errors: string[] = [];
  if (schoolDays.length === 0 || companyDays.length === 0) {
    errors.push('Classez chaque jour du lundi au vendredi en « école » ou « entreprise ».');
  }
  return {
    configuration: { schoolDays, companyDays },
    cycleLengthWeeks: null,
    errors,
    preview: {
      cycleLengthWeeks: 1,
      schoolWeeks: [1],
      companyWeeks: [],
      schoolDays,
      companyDays,
    },
  };
}

function buildWeeksOutOfFour(
  value: PatternConfigFormValue,
  expectedSchoolWeeks: number,
): BuiltPatternConfiguration {
  const all = weekList(4);
  const schoolWeeks =
    expectedSchoolWeeks === 1
      ? [value.oneWeekSchoolWeek]
      : [...value.twoWeeksSchoolWeeks].sort((a, b) => a - b);
  const companyWeeks = all.filter((w) => !schoolWeeks.includes(w));
  const errors: string[] = [];
  if (new Set(schoolWeeks).size !== expectedSchoolWeeks || schoolWeeks.some((w) => w < 1 || w > 4)) {
    errors.push(
      expectedSchoolWeeks === 1
        ? 'Choisissez la semaine (1 à 4) passée à l’école.'
        : 'Choisissez exactement deux semaines (parmi 1 à 4) passées à l’école.',
    );
  }
  const configuration: Record<string, unknown> = { schoolWeeks, companyWeeks };
  const customDays =
    value.weeksOutOfFourSchoolDays && value.weeksOutOfFourSchoolDays.length > 0
      ? WEEKDAYS.filter((d) => value.weeksOutOfFourSchoolDays!.includes(d))
      : null;
  if (customDays) {
    configuration['schoolDays'] = customDays;
  }
  return {
    configuration,
    cycleLengthWeeks: null,
    errors,
    preview: {
      cycleLengthWeeks: 4,
      schoolWeeks,
      companyWeeks,
      schoolDays: customDays ?? [...WEEKDAYS],
      companyDays: [],
    },
  };
}

function buildCustom(value: PatternConfigFormValue): BuiltPatternConfiguration {
  const errors: string[] = [];
  const cycle = value.customCycleLengthWeeks;
  if (!Number.isInteger(cycle) || cycle <= 0) {
    errors.push('La longueur du cycle doit être un entier strictement positif.');
  }
  const roles = value.customWeekRoles.slice(0, Math.max(0, cycle));
  const schoolWeeks: number[] = [];
  const companyWeeks: number[] = [];
  roles.forEach((role, index) => {
    if (role === 'SCHOOL') {
      schoolWeeks.push(index + 1);
    } else if (role === 'COMPANY') {
      companyWeeks.push(index + 1);
    }
  });
  if (schoolWeeks.length === 0 && companyWeeks.length === 0) {
    errors.push('Classez au moins une semaine du cycle en « école » ou « entreprise ».');
  }
  const schoolDays = WEEKDAYS.filter((d) => value.customSchoolDays.includes(d));
  const companyDays = WEEKDAYS.filter((d) => value.customCompanyDays.includes(d));
  const overlapDays = schoolDays.filter((d) => companyDays.includes(d));
  if (overlapDays.length > 0) {
    errors.push('Un jour ne peut pas être à la fois « école » et « entreprise ».');
  }
  // `companyDays` est envoyé explicitement même vide : le back-end
  // l'accepte pour un rythme CUSTOM (défaut vide) et la forme canonique
  // le conserve.
  const configuration: Record<string, unknown> = {
    cycleLengthWeeks: cycle,
    schoolWeeks,
    companyWeeks,
    schoolDays,
    companyDays,
  };
  return {
    configuration,
    cycleLengthWeeks: cycle,
    errors,
    preview: {
      cycleLengthWeeks: Number.isInteger(cycle) && cycle > 0 ? cycle : 1,
      schoolWeeks,
      companyWeeks,
      schoolDays,
      companyDays,
    },
  };
}

/** Valeurs de formulaire par défaut (création). */
export function emptyPatternConfigFormValue(): PatternConfigFormValue {
  return {
    threeTwoDays: {
      MONDAY: 'SCHOOL',
      TUESDAY: 'SCHOOL',
      WEDNESDAY: 'SCHOOL',
      THURSDAY: 'COMPANY',
      FRIDAY: 'COMPANY',
    },
    oneWeekSchoolWeek: 1,
    twoWeeksSchoolWeeks: [1, 2],
    weeksOutOfFourSchoolDays: null,
    customCycleLengthWeeks: 4,
    customWeekRoles: ['SCHOOL', 'COMPANY', 'COMPANY', 'COMPANY'],
    customSchoolDays: [...WEEKDAYS],
    customCompanyDays: [],
  };
}

/**
 * Reconstitue une saisie de formulaire à partir de la configuration
 * canonique d'un modèle existant (mode édition). Lecture fidèle, aucune
 * valeur inventée.
 */
export function formValueFromCanonical(
  type: WorkStudyPatternType,
  config: CanonicalPatternConfiguration,
): PatternConfigFormValue {
  const base = emptyPatternConfigFormValue();
  const asWeekdays = (values: string[]): Weekday[] =>
    WEEKDAYS.filter((d) => values.includes(d));
  const isFullWeek = (days: string[]): boolean => WEEKDAYS.every((d) => days.includes(d));

  if (type === 'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY') {
    const threeTwoDays = { ...base.threeTwoDays };
    for (const day of WEEKDAYS) {
      if (config.companyDays.includes(day)) {
        threeTwoDays[day] = 'COMPANY';
      } else if (config.schoolDays.includes(day)) {
        threeTwoDays[day] = 'SCHOOL';
      }
    }
    return { ...base, threeTwoDays };
  }

  if (type === 'ONE_WEEK_SCHOOL_OUT_OF_FOUR') {
    return {
      ...base,
      oneWeekSchoolWeek: config.schoolWeeks[0] ?? 1,
      weeksOutOfFourSchoolDays: isFullWeek(config.schoolDays)
        ? null
        : asWeekdays(config.schoolDays),
    };
  }

  if (type === 'TWO_WEEKS_SCHOOL_OUT_OF_FOUR') {
    return {
      ...base,
      twoWeeksSchoolWeeks: [...config.schoolWeeks].sort((a, b) => a - b),
      weeksOutOfFourSchoolDays: isFullWeek(config.schoolDays)
        ? null
        : asWeekdays(config.schoolDays),
    };
  }

  const cycle = Number.isInteger(config.cycleLengthWeeks) && config.cycleLengthWeeks > 0
    ? config.cycleLengthWeeks
    : 1;
  const customWeekRoles: ('SCHOOL' | 'COMPANY' | 'UNCLASSIFIED')[] = Array.from(
    { length: cycle },
    (_, i) => {
      const week = i + 1;
      if (config.schoolWeeks.includes(week)) {
        return 'SCHOOL';
      }
      if (config.companyWeeks.includes(week)) {
        return 'COMPANY';
      }
      return 'UNCLASSIFIED';
    },
  );
  return {
    ...base,
    customCycleLengthWeeks: cycle,
    customWeekRoles,
    customSchoolDays: asWeekdays(config.schoolDays),
    customCompanyDays: asWeekdays(config.companyDays),
  };
}

// ---------------------------------------------------------------------------
// Prévisualisation du cycle (représentation, pas de résolution de date)
// ---------------------------------------------------------------------------

export interface CyclePreviewCell {
  day: Weekday;
  context: AlternationContext;
}

export interface CyclePreviewWeek {
  weekIndex: number;
  cells: CyclePreviewCell[];
}

/**
 * Classe le couple (semaine, jour) **exactement** comme
 * `PatternConfiguration.resolve` côté serveur (hors week-end, non
 * représenté ici) : c'est une lecture fidèle de la configuration stockée,
 * pas un calcul lié à une date, une ancre ou un modulo de cycle.
 */
export function classifyCyclePreviewCell(
  config: CanonicalPatternConfiguration,
  weekIndex: number,
  day: Weekday,
): AlternationContext {
  if (config.schoolWeeks.includes(weekIndex)) {
    if (config.schoolDays.includes(day)) {
      return 'SCHOOL';
    }
    if (config.companyDays.includes(day)) {
      return 'COMPANY';
    }
    return 'UNKNOWN';
  }
  if (config.companyWeeks.includes(weekIndex)) {
    return 'COMPANY';
  }
  return 'UNKNOWN';
}

/** Grille semaine × jour de la configuration canonique. */
export function buildCyclePreview(
  config: CanonicalPatternConfiguration,
): CyclePreviewWeek[] {
  const cycle = Number.isInteger(config.cycleLengthWeeks) && config.cycleLengthWeeks > 0
    ? config.cycleLengthWeeks
    : 1;
  return weekList(cycle).map((weekIndex) => ({
    weekIndex,
    cells: WEEKDAYS.map((day) => ({
      day,
      context: classifyCyclePreviewCell(config, weekIndex, day),
    })),
  }));
}
