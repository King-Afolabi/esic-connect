import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DuplicateGroup } from '../administration.models';
import { DuplicateList } from './duplicate-list';

const URL = '/api/v1/users/duplicates';

interface Internals {
  retry: () => void;
}

const GROUP: DuplicateGroup = {
  signature: 'awa diallo',
  reason: 'Même nom et prénom, à la casse et aux accents près.',
  accounts: [
    {
      userId: 'u-1',
      email: 'awa.diallo@esic.test',
      firstName: 'Awa',
      lastName: 'Diallo',
      status: 'ACTIVE',
      createdAt: '2026-08-01T10:00:00Z',
    },
    {
      userId: 'u-2',
      email: 'a.diallo@esic.test',
      firstName: 'Awa',
      lastName: 'Diallo',
      status: 'PENDING_ACTIVATION',
      createdAt: '2026-08-02T10:00:00Z',
    },
  ],
};

describe('DuplicateList', () => {
  let fixture: ComponentFixture<DuplicateList>;
  let http: HttpTestingController;
  let internals: Internals;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(DuplicateList);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as Internals;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const expectList = () => http.expectOne((r) => r.url === URL);

  it('GETs /api/v1/users/duplicates on load and shows a loading state', () => {
    const req = expectList();
    expect(req.request.method).toBe('GET');
    expect(text()).toContain('Chargement des doublons');
    req.flush([]);
  });

  it('shows the empty state when no group is returned', () => {
    expectList().flush([]);
    fixture.detectChanges();
    expect(text()).toContain('Aucun doublon détecté.');
  });

  it('renders each group with its reason and every candidate, linked to its own detail page', () => {
    expectList().flush([GROUP]);
    fixture.detectChanges();

    expect(text()).toContain('Même nom et prénom, à la casse et aux accents près.');
    expect(text()).toContain('awa.diallo@esic.test');
    expect(text()).toContain('a.diallo@esic.test');
    const links = [...(fixture.nativeElement as HTMLElement).querySelectorAll('a[href^="/administration/"]')];
    expect(links.map((a) => (a as HTMLAnchorElement).getAttribute('href'))).toEqual(
      expect.arrayContaining(['/administration/u-1', '/administration/u-2']),
    );
  });

  it('never proposes a merge or delete action — signalling only (docs/02 §9.5)', () => {
    expectList().flush([GROUP]);
    fixture.detectChanges();
    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].map(
      (b) => b.textContent ?? '',
    );
    expect(buttons.some((label) => /fusion|supprimer/i.test(label))).toBe(false);
  });

  it('renders an access-denied panel on a 403 from the API', () => {
    expectList().flush(
      { status: 403, code: 'USER_OPERATION_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter la détection de doublons");
  });

  it('shows a generic error with a retry that re-requests the list', () => {
    expectList().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');

    internals.retry();
    expectList().flush([GROUP]);
    fixture.detectChanges();
    expect(text()).toContain('awa.diallo@esic.test');
  });
});
