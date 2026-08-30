/**
 * Types de la gestion et de la consultation de l'**alternance**,
 * strictement alignés sur le contrat du module back-end `alternation`
 * (`com.esic.connect.alternation.internal`) — aucun champ, endpoint,
 * statut HTTP, rôle ni code `ALT_*` n'est inventé.
 *
 * Modèles de rythme (`/api/v1/alternation/patterns`) :
 * - `GET    /patterns`                     → `PageResponse<WorkStudyPatternResponse>`
 * - `GET    /patterns/{publicId}`          → `WorkStudyPatternResponse`
 * - `POST   /patterns`                     → `WorkStudyPatternResponse` (201)
 * - `PATCH  /patterns/{publicId}`          → `WorkStudyPatternResponse`
 * - `POST   /patterns/{publicId}/archive`  → 204
 * - `POST   /patterns/{publicId}/restore`  → 204
 *
 * Affectations de rythme à une classe :
 * - `POST   /alternation/class-assignments`                        → `ClassAssignmentResponse` (201)
 * - `GET    /alternation/class-assignments`                        → `PageResponse<ClassAssignmentResponse>`
 * - `GET    /alternation/class-assignments/{publicId}`             → `ClassAssignmentResponse`
 * - `POST   /alternation/class-assignments/{publicId}/close`       → 204
 * - `GET    /alternation/classes/{classPublicId}/assignments`      → `PageResponse<ClassAssignmentResponse>`
 * - `GET    /alternation/classes/{classPublicId}/context?date=…`   → `AlternationContextResponse`
 *
 * Exceptions individuelles :
 * - `POST   /alternation/student-exceptions`                             → `StudentExceptionResponse` (201)
 * - `GET    /alternation/student-exceptions/{publicId}`                  → `StudentExceptionResponse`
 * - `POST   /alternation/student-exceptions/{publicId}/cancel`           → 204
 * - `GET    /alternation/enrollments/{enrollmentPublicId}/exceptions`    → `PageResponse<StudentExceptionResponse>`
 * - `GET    /alternation/enrollments/{enrollmentPublicId}/context?date=…`→ `EnrollmentContextResponse`
 *
 * Rôles (`AlternationWeb`) : lecture des modèles ouverte à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` / `PEDAGOGICAL_MANAGER` ;
 * écriture des modèles réservée à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` ; affectations et
 * exceptions ouvertes aux quatre rôles, le `PEDAGOGICAL_MANAGER` étant
 * restreint à son périmètre **côté serveur** (`AcademicScopeDirectory`,
 * jamais d'après un paramètre client) — un hors-périmètre renvoie
 * `403 ALT_FORBIDDEN`.
 */

/** Enveloppe de pagination stable (`PageResponse<T>` côté serveur). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type SortDirection = 'asc' | 'desc';

// ---------------------------------------------------------------------------
// Énumérations (valeurs exactes du back-end)
// ---------------------------------------------------------------------------

/** `WorkStudyPatternType`. */
export const WORK_STUDY_PATTERN_TYPES = [
  'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY',
  'ONE_WEEK_SCHOOL_OUT_OF_FOUR',
  'TWO_WEEKS_SCHOOL_OUT_OF_FOUR',
  'CUSTOM',
] as const;
export type WorkStudyPatternType = (typeof WORK_STUDY_PATTERN_TYPES)[number];

export const WORK_STUDY_PATTERN_TYPE_LABELS: Record<WorkStudyPatternType, string> = {
  THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY: '3 jours école / 2 jours entreprise',
  ONE_WEEK_SCHOOL_OUT_OF_FOUR: '1 semaine école sur 4',
  TWO_WEEKS_SCHOOL_OUT_OF_FOUR: '2 semaines école sur 4',
  CUSTOM: 'Personnalisé',
};

export function workStudyPatternTypeLabel(type: string): string {
  return (WORK_STUDY_PATTERN_TYPE_LABELS as Record<string, string>)[type] ?? type;
}

/** `WorkStudyPatternStatus`. */
export const WORK_STUDY_PATTERN_STATUSES = ['ACTIVE', 'ARCHIVED'] as const;
export type WorkStudyPatternStatus = (typeof WORK_STUDY_PATTERN_STATUSES)[number];

export const WORK_STUDY_PATTERN_STATUS_LABELS: Record<WorkStudyPatternStatus, string> = {
  ACTIVE: 'Actif',
  ARCHIVED: 'Archivé',
};

export function workStudyPatternStatusLabel(status: string): string {
  return (WORK_STUDY_PATTERN_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/** `ClassPatternStatus` (statut d'une affectation à une classe). */
export const CLASS_PATTERN_STATUSES = ['ACTIVE', 'CLOSED'] as const;
export type ClassPatternStatus = (typeof CLASS_PATTERN_STATUSES)[number];

export const CLASS_PATTERN_STATUS_LABELS: Record<ClassPatternStatus, string> = {
  ACTIVE: 'En vigueur',
  CLOSED: 'Clôturée',
};

export function classPatternStatusLabel(status: string): string {
  return (CLASS_PATTERN_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/** `ScheduleExceptionType`. */
export const SCHEDULE_EXCEPTION_TYPES = [
  'REMOTE_ALLOWED',
  'ON_SITE_REQUIRED',
  'COMPANY_PERIOD',
  'VALIDATED_UNAVAILABILITY',
] as const;
export type ScheduleExceptionType = (typeof SCHEDULE_EXCEPTION_TYPES)[number];

export const SCHEDULE_EXCEPTION_TYPE_LABELS: Record<ScheduleExceptionType, string> = {
  REMOTE_ALLOWED: 'Suivi à distance autorisé',
  ON_SITE_REQUIRED: 'Présence à l’école requise',
  COMPANY_PERIOD: 'Période en entreprise',
  VALIDATED_UNAVAILABILITY: 'Indisponibilité validée',
};

/**
 * Effet **structurel** de chaque type sur l'axe SCHOOL / COMPANY, tel que
 * documenté par le back-end (`ScheduleExceptionType`,
 * `AlternationContextService`). Sert uniquement à afficher une aide ; la
 * décision effective vient toujours de l'endpoint de contexte.
 */
export const SCHEDULE_EXCEPTION_TYPE_EFFECT: Record<ScheduleExceptionType, string> = {
  REMOTE_ALLOWED: 'N’agit pas sur l’axe école / entreprise (suivi à distance).',
  ON_SITE_REQUIRED: 'Impose le contexte « école » à la date couverte.',
  COMPANY_PERIOD: 'Impose le contexte « entreprise » à la date couverte.',
  VALIDATED_UNAVAILABILITY:
    'N’agit pas sur l’axe école / entreprise dans ce périmètre (aucun calcul d’assiduité).',
};

export function scheduleExceptionTypeLabel(type: string): string {
  return (SCHEDULE_EXCEPTION_TYPE_LABELS as Record<string, string>)[type] ?? type;
}

/** `ScheduleExceptionStatus`. */
export const SCHEDULE_EXCEPTION_STATUSES = ['ACTIVE', 'CANCELLED'] as const;
export type ScheduleExceptionStatus = (typeof SCHEDULE_EXCEPTION_STATUSES)[number];

export const SCHEDULE_EXCEPTION_STATUS_LABELS: Record<ScheduleExceptionStatus, string> = {
  ACTIVE: 'Active',
  CANCELLED: 'Annulée',
};

export function scheduleExceptionStatusLabel(status: string): string {
  return (SCHEDULE_EXCEPTION_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/** `AlternationContext` — jamais recalculé côté client. */
export const ALTERNATION_CONTEXTS = ['SCHOOL', 'COMPANY', 'UNKNOWN'] as const;
export type AlternationContext = (typeof ALTERNATION_CONTEXTS)[number];

export const ALTERNATION_CONTEXT_LABELS: Record<AlternationContext, string> = {
  SCHOOL: 'École',
  COMPANY: 'Entreprise',
  UNKNOWN: 'Indéterminé',
};

export function alternationContextLabel(context: string): string {
  return (ALTERNATION_CONTEXT_LABELS as Record<string, string>)[context] ?? context;
}

/** `ContextSource`. */
export type ContextSource = 'PATTERN' | 'INDIVIDUAL_EXCEPTION' | 'NONE';

export const CONTEXT_SOURCE_LABELS: Record<ContextSource, string> = {
  PATTERN: 'Rythme affecté à la classe',
  INDIVIDUAL_EXCEPTION: 'Exception individuelle',
  NONE: 'Aucune information exploitable',
};

export function contextSourceLabel(source: string): string {
  return (CONTEXT_SOURCE_LABELS as Record<string, string>)[source] ?? source;
}

// ---------------------------------------------------------------------------
// Jours de la semaine (`DayOfWeek.name()` côté serveur)
// ---------------------------------------------------------------------------

export const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] as const;
export type Weekday = (typeof WEEKDAYS)[number];

export const WEEKDAY_LABELS: Record<Weekday, string> = {
  MONDAY: 'Lundi',
  TUESDAY: 'Mardi',
  WEDNESDAY: 'Mercredi',
  THURSDAY: 'Jeudi',
  FRIDAY: 'Vendredi',
};

export const WEEKDAY_SHORT_LABELS: Record<Weekday, string> = {
  MONDAY: 'Lun',
  TUESDAY: 'Mar',
  WEDNESDAY: 'Mer',
  THURSDAY: 'Jeu',
  FRIDAY: 'Ven',
};

export function weekdayLabel(day: string): string {
  return (WEEKDAY_LABELS as Record<string, string>)[day] ?? day;
}

// ---------------------------------------------------------------------------
// Configuration canonique d'un modèle de rythme
// ---------------------------------------------------------------------------

/**
 * Forme **canonique** de `configuration` renvoyée par l'API après
 * validation (cinq clés, semaines et jours triés). Le back-end la produit
 * pour les quatre types ; on l'affiche telle quelle, sans transformation
 * silencieuse (les tableaux `schoolDays` / `companyDays` peuvent être
 * vides — c'est légitime).
 */
export interface CanonicalPatternConfiguration {
  cycleLengthWeeks: number;
  schoolWeeks: number[];
  companyWeeks: number[];
  schoolDays: string[];
  companyDays: string[];
}

/**
 * Lit prudemment la `configuration` (JSON libre côté transport) en forme
 * canonique. Toute clé manquante est comblée par une valeur vide : la
 * fonction n'invente rien et ne réordonne pas les valeurs fournies.
 */
export function readCanonicalConfiguration(raw: unknown): CanonicalPatternConfiguration {
  const source = (raw ?? {}) as Record<string, unknown>;
  const numberArray = (value: unknown): number[] =>
    Array.isArray(value) ? value.filter((v): v is number => typeof v === 'number') : [];
  const stringArray = (value: unknown): string[] =>
    Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [];
  const cycle = typeof source['cycleLengthWeeks'] === 'number' ? source['cycleLengthWeeks'] : 1;
  return {
    cycleLengthWeeks: cycle,
    schoolWeeks: numberArray(source['schoolWeeks']),
    companyWeeks: numberArray(source['companyWeeks']),
    schoolDays: stringArray(source['schoolDays']),
    companyDays: stringArray(source['companyDays']),
  };
}

// ---------------------------------------------------------------------------
// Réponses API
// ---------------------------------------------------------------------------

/** `WorkStudyPatternResponse`. */
export interface WorkStudyPatternResponse {
  publicId: string;
  code: string;
  name: string;
  description: string | null;
  type: WorkStudyPatternType;
  cycleLengthWeeks: number | null;
  /** `configuration_json` sous sa forme canonique normalisée. */
  configuration: unknown;
  status: WorkStudyPatternStatus;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `ClassAssignmentResponse`. */
export interface ClassAssignmentResponse {
  publicId: string;
  classGroupPublicId: string | null;
  classGroupCode: string | null;
  workStudyPatternPublicId: string;
  workStudyPatternCode: string;
  workStudyPatternType: WorkStudyPatternType;
  /** `LocalDate` (`yyyy-MM-dd`). */
  cycleStartDate: string;
  validFrom: string;
  validUntil: string | null;
  status: ClassPatternStatus;
  closeReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `StudentExceptionResponse`. */
export interface StudentExceptionResponse {
  publicId: string;
  enrollmentPublicId: string | null;
  studentProfilePublicId: string | null;
  classGroupPublicId: string | null;
  type: ScheduleExceptionType;
  /** `Instant` ISO-8601 (avec fuseau / `Z`). */
  startAt: string;
  endAt: string;
  timeZoneId: string;
  reason: string;
  status: ScheduleExceptionStatus;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `AlternationContextResponse` — contexte d'une classe à une date. */
export interface AlternationContextResponse {
  classGroupPublicId: string;
  date: string;
  context: AlternationContext;
  source: ContextSource;
  classAssignmentPublicId: string | null;
  workStudyPatternPublicId: string | null;
  workStudyPatternCode: string | null;
  cycleWeekIndex: number | null;
  dayOfWeek: string;
}

/** `EnrollmentContextResponse` — contexte effectif d'une inscription. */
export interface EnrollmentContextResponse {
  enrollmentPublicId: string;
  classGroupPublicId: string | null;
  date: string;
  patternContext: AlternationContext;
  effectiveContext: AlternationContext;
  source: ContextSource;
  coveringExceptionTypes: ScheduleExceptionType[];
}

// ---------------------------------------------------------------------------
// Requêtes (corps POST / PATCH) — noms exacts des `record` back-end
// ---------------------------------------------------------------------------

/** `WorkStudyPatternRequests.Create`. */
export interface CreatePatternRequest {
  code: string;
  name: string;
  description?: string | null;
  type: WorkStudyPatternType;
  cycleLengthWeeks?: number | null;
  configuration: unknown;
}

/** `WorkStudyPatternRequests.Update` (le `code` et le `type` restent figés). */
export interface UpdatePatternRequest {
  name: string;
  description?: string | null;
  cycleLengthWeeks?: number | null;
  configuration: unknown;
}

/** `WorkStudyPatternRequests.Archive`. */
export interface ArchivePatternRequest {
  reason: string;
}

/** `ClassAssignmentRequests.Assign`. */
export interface AssignClassRequest {
  classGroupPublicId: string;
  workStudyPatternPublicId: string;
  cycleStartDate: string;
  validFrom: string;
  validUntil?: string | null;
}

/** `ClassAssignmentRequests.Close`. */
export interface CloseAssignmentRequest {
  reason: string;
  effectiveDate?: string | null;
}

/** `StudentExceptionRequests.Create`. */
export interface CreateExceptionRequest {
  enrollmentPublicId: string;
  type: ScheduleExceptionType;
  startAt: string;
  endAt: string;
  timeZoneId: string;
  reason: string;
}

/** `StudentExceptionRequests.Cancel`. */
export interface CancelExceptionRequest {
  reason: string;
}

// ---------------------------------------------------------------------------
// Paramètres de liste (uniquement ceux réellement exposés)
// ---------------------------------------------------------------------------

/**
 * `GET /api/v1/alternation/patterns` — liste blanche de tri
 * `WorkStudyPatternService.SORTABLE`.
 */
export const PATTERN_SORT_FIELDS = ['code', 'name', 'createdAt', 'updatedAt'] as const;
export type PatternSortField = (typeof PATTERN_SORT_FIELDS)[number];

export interface PatternListQuery {
  /** `q` — sous-chaîne de **code ou nom**. */
  q?: string | null;
  status?: WorkStudyPatternStatus | null;
  type?: WorkStudyPatternType | null;
  sort?: string | null;
  page?: number;
  size?: number;
}

/**
 * `GET /alternation/class-assignments` et
 * `GET /alternation/classes/{id}/assignments` — liste blanche de tri
 * `ClassWorkStudyPatternService.SORTABLE`.
 */
export const ASSIGNMENT_SORT_FIELDS = ['validFrom', 'validUntil', 'createdAt'] as const;
export type AssignmentSortField = (typeof ASSIGNMENT_SORT_FIELDS)[number];

export interface AssignmentListQuery {
  /** `class` — `public_id` d'une classe (endpoint plat uniquement). */
  class?: string | null;
  status?: ClassPatternStatus | null;
  sort?: string | null;
  page?: number;
  size?: number;
}

/**
 * `GET /alternation/enrollments/{id}/exceptions` — liste blanche de tri
 * `StudentScheduleExceptionService.SORTABLE`.
 */
export const EXCEPTION_SORT_FIELDS = ['startAt', 'endAt', 'createdAt'] as const;
export type ExceptionSortField = (typeof EXCEPTION_SORT_FIELDS)[number];

export interface ExceptionListQuery {
  sort?: string | null;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// Formatage (déterministe, UTC — comme le reste du front)
// ---------------------------------------------------------------------------

/** `yyyy-MM-dd` ou `Instant` → `jj/mm/aaaa` (UTC). `—` si absent / illisible. */
export function formatIsoDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const day = String(date.getUTCDate()).padStart(2, '0');
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${date.getUTCFullYear()}`;
}

/**
 * `Instant` ISO-8601 → `jj/mm/aaaa hh:mm UTC`. Les instants d'une
 * exception sont affichés en UTC **et** accompagnés du `timeZoneId`
 * déclaré : aucune conversion silencieuse vers le fuseau du navigateur.
 */
export function formatInstantUtc(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const hh = String(date.getUTCHours()).padStart(2, '0');
  const mm = String(date.getUTCMinutes()).padStart(2, '0');
  return `${formatIsoDate(value)} ${hh}:${mm} UTC`;
}

/** `validFrom` – `validUntil` (inclusifs) ; `validUntil` absent → `… ouverte`. */
export function formatAssignmentPeriod(validFrom: string, validUntil: string | null): string {
  return `${formatIsoDate(validFrom)} – ${validUntil ? formatIsoDate(validUntil) : 'ouverte'}`;
}
