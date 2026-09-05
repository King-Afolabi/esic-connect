import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { GlobalSearchApiService } from './global-search-api.service';
import { GlobalSearch } from './global-search';
import { hitRoute, hitTypeLabel } from './global-search.models';

/**
 * Recherche globale (EF-USER-009). Le périmètre est appliqué côté
 * serveur : ces tests vérifient que l'écran n'en transmet aucun, qu'il
 * affiche les mentions du serveur telles quelles, et qu'il n'offre pas
 * de lien vers un écran qui n'existe pas.
 */
describe('GlobalSearch', () => {
  let fixture: ComponentFixture<GlobalSearch>;

  const api = { search: vi.fn() };

  const response = {
    query: 'bts',
    truncated: false,
    results: [
      { type: 'CLASS_GROUP' as const, publicId: 'c-1', label: 'BTS1-A', secondary: 'PRG-1 — 2026' },
      { type: 'ROOM' as const, publicId: 'r-1', label: 'B204', secondary: 'Salle 204 — Bât. B' },
    ],
    notes: ['Résultats limités à votre périmètre pédagogique.'],
  };

  beforeEach(async () => {
    api.search.mockReset().mockReturnValue(of(response));
    await TestBed.configureTestingModule({
      imports: [GlobalSearch],
      providers: [provideRouter([]), { provide: GlobalSearchApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(GlobalSearch);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;

  function searchFor(query: string): void {
    const component = fixture.componentInstance as unknown as {
      form: { setValue: (v: { query: string }) => void };
      submit: () => void;
    };
    component.form.setValue({ query });
    component.submit();
    fixture.detectChanges();
  }

  it('sends only the query — never a scope filter', () => {
    searchFor('bts');
    expect(api.search).toHaveBeenCalledWith('bts');
    expect(api.search).toHaveBeenCalledTimes(1);
  });

  it('renders the results and the server notes verbatim', () => {
    searchFor('bts');
    expect(text()).toContain('BTS1-A');
    expect(text()).toContain('B204');
    expect(text()).toContain('Résultats limités à votre périmètre pédagogique.');
  });

  it('refuses a fragment shorter than the minimum without calling the server', () => {
    searchFor('b');
    expect(api.search).not.toHaveBeenCalled();
    expect(text()).toContain('Saisissez au moins 2 caractères.');
  });

  it('renders a forbidden panel on 403 rather than an empty result list', () => {
    api.search.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' })),
    );
    searchFor('bts');
    expect(text()).toContain("Vous n'êtes pas autorisé à utiliser la recherche globale.");
  });

  it('offers no link for resource types that have no screen', () => {
    // Un lien vers un écran inexistant est pire qu'une absence de lien :
    // il finit en « page introuvable ».
    expect(hitRoute({ type: 'ROOM', publicId: 'r-1', label: 'B204', secondary: null })).toBeNull();
    expect(hitRoute({ type: 'TEACHER', publicId: 't-1', label: 'X', secondary: null })).toBeNull();
    expect(hitRoute({ type: 'SESSION', publicId: 's-1', label: 'X', secondary: null })).toEqual([
      '/sessions',
      's-1',
    ]);
  });

  it('labels every hit type in French', () => {
    expect(hitTypeLabel('STUDENT')).toBe('Apprenant');
    expect(hitTypeLabel('CLASS_GROUP')).toBe('Classe');
    expect(hitTypeLabel('PROGRAM')).toBe('Formation');
    expect(hitTypeLabel('ROOM')).toBe('Salle');
  });
});
