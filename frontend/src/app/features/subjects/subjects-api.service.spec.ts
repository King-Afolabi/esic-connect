import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { SubjectsApiService } from './subjects-api.service';

describe('SubjectsApiService', () => {
  let service: SubjectsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SubjectsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('omet les filtres vides plutôt que d’envoyer des paramètres inutiles', () => {
    service.list({ status: 'ACTIVE', q: '', size: 100 }).subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/api/v1/subjects');
    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.params.get('size')).toBe('100');
    request.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it("n'envoie jamais de champ formateur à la création", () => {
    service.create({ code: 'MAT-1', name: 'Algorithmique' }).subscribe();

    const request = http.expectOne('/api/v1/subjects');
    // docs/02 §6.4 : l'affectation d'un formateur se fait au niveau de la
    // séance, jamais de la matière.
    expect(Object.keys(request.request.body as object)).not.toContain('teacherId');
    request.flush({});
  });

  it('archive et restaure par les routes dédiées', () => {
    service.archive('sujet-1', 'obsolète').subscribe();
    const archive = http.expectOne('/api/v1/subjects/sujet-1/archive');
    expect(archive.request.body).toEqual({ reason: 'obsolète' });
    archive.flush({});

    service.restore('sujet-1').subscribe();
    http.expectOne('/api/v1/subjects/sujet-1/restore').flush({});
  });
});
