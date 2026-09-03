/** Contrats du référentiel des matières (EF-ACA-006), alignés sur `SubjectResponse`. */

export interface ProgramRef {
  id: string;
  code: string;
  name: string;
}

export interface SubjectResponse {
  id: string;
  code: string;
  name: string;
  description: string | null;
  /** Volume horaire indicatif : cadrage pédagogique, jamais un calcul d'assiduité. */
  hourlyVolume: number | null;
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
  programs: ProgramRef[];
  createdAt: string;
  updatedAt: string;
}

export interface SubjectPage {
  content: SubjectResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SubjectListQuery {
  q?: string;
  status?: string;
  programId?: string;
  sort?: string;
  page?: number;
  size?: number;
}

/** Le code est absent de la mise à jour : il est immuable après création. */
export interface CreateSubjectRequest {
  code: string;
  name: string;
  description?: string | null;
  hourlyVolume?: number | null;
  programPublicIds?: string[];
}

export interface UpdateSubjectRequest {
  name: string;
  description?: string | null;
  hourlyVolume?: number | null;
  programPublicIds?: string[];
}
