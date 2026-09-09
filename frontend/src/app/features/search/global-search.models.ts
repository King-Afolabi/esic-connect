/**
 * Recherche globale (EF-USER-009 ; docs/02 §22.7). Miroir exact du DTO
 * `com.esic.connect.search.internal.SearchResponses` — aucun champ, type
 * ni route n'est inventé.
 */

export type SearchHitType =
  | 'STUDENT'
  | 'TEACHER'
  | 'CLASS_GROUP'
  | 'PROGRAM'
  | 'ROOM'
  | 'SESSION';

export interface SearchHit {
  type: SearchHitType;
  publicId: string;
  label: string;
  secondary: string | null;
}

export interface GlobalSearchResponse {
  query: string;
  truncated: boolean;
  results: SearchHit[];
  notes: string[];
}

/** Libellé lisible d'un type de résultat. */
export function hitTypeLabel(type: SearchHitType): string {
  switch (type) {
    case 'STUDENT':
      return 'Apprenant';
    case 'TEACHER':
      return 'Formateur';
    case 'CLASS_GROUP':
      return 'Classe';
    case 'PROGRAM':
      return 'Formation';
    case 'ROOM':
      return 'Salle';
    case 'SESSION':
      return 'Séance';
    default:
      return type;
  }
}

/**
 * Route de l'écran qui affiche la ressource trouvée, ou `null` si aucun
 * écran ne la porte encore.
 *
 * Le chemin est calculé **côté client**, à partir d'une liste fermée :
 * le serveur ne transmet jamais un chemin d'interface (docs/02 §21.5).
 * Renvoyer `null` plutôt qu'un lien vers un écran inexistant évite
 * d'offrir un chemin qui finirait en « page introuvable ».
 */
export function hitRoute(hit: SearchHit): unknown[] | null {
  switch (hit.type) {
    case 'STUDENT':
      return ['/students', hit.publicId];
    case 'CLASS_GROUP':
      return ['/academic/class-groups', hit.publicId];
    case 'PROGRAM':
      return ['/academic/programs', hit.publicId];
    case 'SESSION':
      return ['/sessions', hit.publicId];
    case 'ROOM':
    case 'TEACHER':
    default:
      // Aucun écran de fiche salle ni de fiche formateur n'existe :
      // le résultat reste affiché, sans lien mort.
      return null;
  }
}

/** Nombre minimal de caractères, aligné sur `SearchPattern.MIN_LENGTH`. */
export const SEARCH_MIN_LENGTH = 2;
