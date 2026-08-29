import { Observable } from 'rxjs';

import { AcademicApiService } from './academic-api.service';
import {
  AcademicListQuery,
  AcademicRecord,
  AcademicResourceSlug,
  AcademicYearResponse,
  ClassGroupResponse,
  PageResponse,
  ProgramLevelResponse,
  ProgramResponse,
  PromotionResponse,
  academicStatusLabel,
  formatIsoDate,
  formatPeriod,
  programTypeLabel,
} from './academic.models';

/** Une colonne du tableau de liste. */
export interface AcademicColumn {
  /** `matColumnDef`. */
  key: string;
  header: string;
  /** En-tête triable — la clé doit alors appartenir à {@link AcademicResourceConfig.sortFields}. */
  sortable?: boolean;
  /** Valeur d'affichage, déjà formatée (`—` pour une valeur absente). */
  value: (row: AcademicRecord) => string;
}

/** Une ligne `<dl>` de la fiche détail. */
export interface AcademicFact {
  label: string;
  value: string;
}

/** Un lien vers la fiche d'une entité parente. */
export interface AcademicParentLink {
  label: string;
  /** Commandes `routerLink` absolues. */
  commands: readonly unknown[];
}

/**
 * Une section « enfants » de la fiche détail : une sous-liste chargée
 * depuis un endpoint réel avec un filtre existant.
 */
export interface AcademicChildSection {
  title: string;
  /** Slug de la ressource enfant, pour la cible du lien « Consulter ». */
  detailSlug: AcademicResourceSlug;
  emptyLabel: string;
  /** Libellé de la barre de progression (accessibilité). */
  loadingAriaLabel: string;
  load: (
    api: AcademicApiService,
    parentPublicId: string,
  ) => Observable<PageResponse<AcademicRecord>>;
}

/** Configuration d'affichage d'une ressource académique. */
export interface AcademicResourceConfig {
  slug: AcademicResourceSlug;
  /** Titre pluriel (onglet, en-tête de liste). */
  listTitle: string;
  /** Titre singulier (fiche détail). */
  singularTitle: string;
  /** Vrai si la ressource a une route de liste autonome (pas les niveaux). */
  hasList: boolean;
  /** Champs réellement triables (sous-ensemble de la liste blanche du service). */
  sortFields: readonly string[];
  /** `champ,asc|desc` par défaut (aligné sur le `DEFAULT_SORT` du service). */
  defaultSort: string;
  columns: readonly AcademicColumn[];
  facts: (row: AcademicRecord) => readonly AcademicFact[];
  parentLinks?: (row: AcademicRecord) => readonly AcademicParentLink[];
  children: readonly AcademicChildSection[];
  loadOne: (api: AcademicApiService, publicId: string) => Observable<AcademicRecord>;
  loadList?: (
    api: AcademicApiService,
    query: AcademicListQuery,
  ) => Observable<PageResponse<AcademicRecord>>;
}

/** Restreint une fonction typée à la lecture d'un {@link AcademicRecord}. */
function pick<T extends AcademicRecord>(fn: (row: T) => string): (row: AcademicRecord) => string {
  return (row) => fn(row as T);
}

const STATUS_COLUMN: AcademicColumn = {
  key: 'status',
  header: 'Statut',
  value: (row) => academicStatusLabel(row.status),
};

/** Motif d'archivage, ajouté aux faits uniquement si l'entité est archivée. */
function archiveFact(row: AcademicRecord): readonly AcademicFact[] {
  return row.status === 'ARCHIVED' && row.archiveReason
    ? [{ label: "Motif d'archivage", value: row.archiveReason }]
    : [];
}

const ACADEMIC_YEARS: AcademicResourceConfig = {
  slug: 'academic-years',
  listTitle: 'Années scolaires',
  singularTitle: 'Année scolaire',
  hasList: true,
  sortFields: ['code', 'name', 'startDate', 'endDate', 'createdAt'],
  defaultSort: 'code,asc',
  columns: [
    { key: 'code', header: 'Code', sortable: true, value: pick<AcademicYearResponse>((r) => r.code) },
    { key: 'name', header: 'Nom', sortable: true, value: pick<AcademicYearResponse>((r) => r.name) },
    {
      key: 'startDate',
      header: 'Début',
      sortable: true,
      value: pick<AcademicYearResponse>((r) => formatIsoDate(r.startDate)),
    },
    {
      key: 'endDate',
      header: 'Fin',
      sortable: true,
      value: pick<AcademicYearResponse>((r) => formatIsoDate(r.endDate)),
    },
    STATUS_COLUMN,
  ],
  facts: pickFacts<AcademicYearResponse>((r) => [
    { label: 'Code', value: r.code },
    { label: 'Nom', value: r.name },
    { label: 'Période', value: formatPeriod(r.startDate, r.endDate) },
    { label: 'Statut', value: academicStatusLabel(r.status) },
    { label: 'Créé le', value: formatIsoDate(r.createdAt) },
  ]),
  children: [
    {
      title: 'Promotions de cette année',
      detailSlug: 'promotions',
      emptyLabel: "Aucune promotion n'est rattachée à cette année scolaire.",
      loadingAriaLabel: 'Chargement des promotions',
      load: (api, id) => api.listPromotions({ academicYear: id, sort: 'code,asc', size: 100 }),
    },
  ],
  loadOne: (api, id) => api.getAcademicYear(id),
  loadList: (api, query) => api.listAcademicYears(query),
};

const PROGRAMS: AcademicResourceConfig = {
  slug: 'programs',
  listTitle: 'Formations',
  singularTitle: 'Formation',
  hasList: true,
  sortFields: ['code', 'name', 'createdAt'],
  defaultSort: 'code,asc',
  columns: [
    { key: 'code', header: 'Code', sortable: true, value: pick<ProgramResponse>((r) => r.code) },
    { key: 'name', header: 'Nom', sortable: true, value: pick<ProgramResponse>((r) => r.name) },
    {
      key: 'programType',
      header: 'Type',
      value: pick<ProgramResponse>((r) => programTypeLabel(r.programType)),
    },
    STATUS_COLUMN,
  ],
  facts: pickFacts<ProgramResponse>((r) => [
    { label: 'Code', value: r.code },
    { label: 'Nom', value: r.name },
    { label: 'Type', value: programTypeLabel(r.programType) },
    { label: 'Description', value: r.description || '—' },
    { label: 'Statut', value: academicStatusLabel(r.status) },
    { label: 'Créé le', value: formatIsoDate(r.createdAt) },
  ]),
  children: [
    {
      title: 'Niveaux de cette formation',
      detailSlug: 'program-levels',
      emptyLabel: "Aucun niveau n'est défini pour cette formation.",
      loadingAriaLabel: 'Chargement des niveaux',
      load: (api, id) => api.listProgramLevels(id, { sort: 'sequenceNumber,asc', size: 100 }),
    },
    {
      title: 'Promotions de cette formation',
      detailSlug: 'promotions',
      emptyLabel: "Aucune promotion n'est rattachée à cette formation.",
      loadingAriaLabel: 'Chargement des promotions',
      load: (api, id) => api.listPromotions({ program: id, sort: 'code,asc', size: 100 }),
    },
  ],
  loadOne: (api, id) => api.getProgram(id),
  loadList: (api, query) => api.listPrograms(query),
};

const PROGRAM_LEVELS: AcademicResourceConfig = {
  slug: 'program-levels',
  listTitle: 'Niveaux',
  singularTitle: 'Niveau',
  hasList: false,
  sortFields: [],
  defaultSort: 'sequenceNumber,asc',
  columns: [],
  facts: pickFacts<ProgramLevelResponse>((r) => [
    { label: 'Code', value: r.code },
    { label: 'Nom', value: r.name },
    { label: 'Ordre', value: String(r.sequenceNumber) },
    { label: 'Statut', value: academicStatusLabel(r.status) },
    { label: 'Créé le', value: formatIsoDate(r.createdAt) },
  ]),
  parentLinks: pickLinks<ProgramLevelResponse>((r) => [
    { label: 'Voir la formation', commands: ['/academic', 'programs', r.programPublicId] },
  ]),
  children: [
    {
      title: 'Classes de ce niveau',
      detailSlug: 'class-groups',
      emptyLabel: "Aucune classe n'est rattachée à ce niveau.",
      loadingAriaLabel: 'Chargement des classes',
      load: (api, id) => api.listClassGroups({ programLevel: id, sort: 'code,asc', size: 100 }),
    },
  ],
  loadOne: (api, id) => api.getProgramLevel(id),
};

const PROMOTIONS: AcademicResourceConfig = {
  slug: 'promotions',
  listTitle: 'Promotions',
  singularTitle: 'Promotion',
  hasList: true,
  sortFields: ['code', 'name', 'createdAt'],
  defaultSort: 'code,asc',
  columns: [
    { key: 'code', header: 'Code', sortable: true, value: pick<PromotionResponse>((r) => r.code) },
    { key: 'name', header: 'Nom', sortable: true, value: pick<PromotionResponse>((r) => r.name) },
    {
      key: 'startDate',
      header: 'Début',
      value: pick<PromotionResponse>((r) => formatIsoDate(r.startDate)),
    },
    {
      key: 'endDate',
      header: 'Fin',
      value: pick<PromotionResponse>((r) => formatIsoDate(r.endDate)),
    },
    STATUS_COLUMN,
  ],
  facts: pickFacts<PromotionResponse>((r) => [
    { label: 'Code', value: r.code },
    { label: 'Nom', value: r.name },
    { label: 'Période', value: formatPeriod(r.startDate, r.endDate) },
    { label: 'Statut', value: academicStatusLabel(r.status) },
    { label: 'Créé le', value: formatIsoDate(r.createdAt) },
  ]),
  parentLinks: pickLinks<PromotionResponse>((r) => [
    { label: 'Voir la formation', commands: ['/academic', 'programs', r.programPublicId] },
    {
      label: "Voir l'année scolaire",
      commands: ['/academic', 'academic-years', r.academicYearPublicId],
    },
  ]),
  children: [
    {
      title: 'Classes de cette promotion',
      detailSlug: 'class-groups',
      emptyLabel: "Aucune classe n'est rattachée à cette promotion.",
      loadingAriaLabel: 'Chargement des classes',
      load: (api, id) => api.listClassGroups({ promotion: id, sort: 'code,asc', size: 100 }),
    },
  ],
  loadOne: (api, id) => api.getPromotion(id),
  loadList: (api, query) => api.listPromotions(query),
};

const CLASS_GROUPS: AcademicResourceConfig = {
  slug: 'class-groups',
  listTitle: 'Classes',
  singularTitle: 'Classe',
  hasList: true,
  sortFields: ['code', 'name', 'createdAt'],
  defaultSort: 'code,asc',
  columns: [
    { key: 'code', header: 'Code', sortable: true, value: pick<ClassGroupResponse>((r) => r.code) },
    { key: 'name', header: 'Nom', sortable: true, value: pick<ClassGroupResponse>((r) => r.name) },
    {
      key: 'capacity',
      header: 'Capacité',
      value: pick<ClassGroupResponse>((r) => (r.capacity == null ? '—' : String(r.capacity))),
    },
    STATUS_COLUMN,
  ],
  facts: pickFacts<ClassGroupResponse>((r) => [
    { label: 'Code', value: r.code },
    { label: 'Nom', value: r.name },
    { label: 'Capacité', value: r.capacity == null ? '—' : String(r.capacity) },
    { label: 'Site', value: r.sitePublicId ? 'Rattachée à un site' : 'Non renseigné' },
    { label: 'Statut', value: academicStatusLabel(r.status) },
    { label: 'Créé le', value: formatIsoDate(r.createdAt) },
  ]),
  parentLinks: pickLinks<ClassGroupResponse>((r) => [
    { label: 'Voir la promotion', commands: ['/academic', 'promotions', r.promotionPublicId] },
    { label: 'Voir le niveau', commands: ['/academic', 'program-levels', r.programLevelPublicId] },
  ]),
  children: [],
  loadOne: (api, id) => api.getClassGroup(id),
  loadList: (api, query) => api.listClassGroups(query),
};

/** Toutes les configurations, indexées par slug de ressource. */
export const ACADEMIC_RESOURCES: Record<AcademicResourceSlug, AcademicResourceConfig> = {
  'academic-years': ACADEMIC_YEARS,
  programs: PROGRAMS,
  'program-levels': PROGRAM_LEVELS,
  promotions: PROMOTIONS,
  'class-groups': CLASS_GROUPS,
};

/** Onglets de navigation entre les listes autonomes (les niveaux n'en ont pas). */
export const ACADEMIC_LIST_TABS: readonly { slug: AcademicResourceSlug; label: string }[] = [
  { slug: 'academic-years', label: 'Années scolaires' },
  { slug: 'programs', label: 'Formations' },
  { slug: 'promotions', label: 'Promotions' },
  { slug: 'class-groups', label: 'Classes' },
];

function pickFacts<T extends AcademicRecord>(
  fn: (row: T) => readonly AcademicFact[],
): (row: AcademicRecord) => readonly AcademicFact[] {
  return (row) => [...fn(row as T), ...archiveFact(row)];
}

function pickLinks<T extends AcademicRecord>(
  fn: (row: T) => readonly AcademicParentLink[],
): (row: AcademicRecord) => readonly AcademicParentLink[] {
  return (row) => fn(row as T);
}
