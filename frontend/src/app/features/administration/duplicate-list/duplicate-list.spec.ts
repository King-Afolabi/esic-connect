import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DuplicateComparisonResponse, DuplicateGroup } from '../administration.models';
import { DuplicateList } from './duplicate-list';

const LIST_URL = '/api/v1/users/duplicates';
const COMPARE_URL = '/api/v1/users/duplicates/compare';

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
    {
      userId: 'u-3',
      email: 'awa.d@esic.test',
      firstName: 'Awa',
      lastName: 'Diallo',
      status: 'ACTIVE',
      createdAt: '2026-08-03T10:00:00Z',
    },
  ],
};

const COMPARISON: DuplicateComparisonResponse = {
  first: {
    id: 'u-1',
    displayName: 'Awa Diallo',
    email: 'awa.diallo@esic.test',
    phone: null,
    status: 'ACTIVE',
    roles: ['STUDENT'],
    isStudent: true,
    studentNumber: 'ESIC-2026-00001',
    hasActiveEnrollment: true,
    hasLoginCredential: true,
    mfaConfigured: false,
    passkeys: 0,
    trustedDevices: 1,
    createdAt: '2026-08-01T10:00:00Z',
    lastLoginAt: null,
  },
  second: {
    id: 'u-2',
    displayName: 'Awa Diallo',
    email: 'a.diallo@esic.test',
    phone: null,
    status: 'PENDING_ACTIVATION',
    roles: [],
    isStudent: false,
    studentNumber: null,
    hasActiveEnrollment: false,
    hasLoginCredential: false,
    mfaConfigured: false,
    passkeys: 0,
    trustedDevices: 0,
    createdAt: '2026-08-02T10:00:00Z',
    lastLoginAt: null,
  },
  matchingFields: ['normalizedName'],
  differentFields: ['email', 'status'],
  conflicts: [],
  warnings: [{ code: 'DIFFERENT_EMAILS', detail: 'Les deux comptes ont une adresse différente.' }],
  consequences: ['Aucune fusion n\'est réalisée par ce parcours.'],
  dependencySummary: { enrollments: 2, attendanceRecords: 30, invitations: 1 },
  assessment: 'POTENTIALLY_SAFE',
  reasons: ['Identité concordante, aucun conflit.'],
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

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const expectList = () => http.expectOne((r) => r.url === LIST_URL);
  const checkboxes = () =>
    [...el().querySelectorAll('input.duplicates__checkbox')] as HTMLInputElement[];
  const compareButton = () =>
    [...el().querySelectorAll('button')].find((b) => (b.textContent ?? '').trim() === 'Comparer') as
      | HTMLButtonElement
      | undefined;

  const loadGroups = () => {
    expectList().flush([GROUP]);
    fixture.detectChanges();
  };

  const check = (index: number) => {
    const box = checkboxes()[index];
    box.checked = !box.checked;
    box.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

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
    loadGroups();
    expect(text()).toContain('Même nom et prénom, à la casse et aux accents près.');
    expect(text()).toContain('awa.diallo@esic.test');
    const links = [...el().querySelectorAll('a[href^="/administration/"]')];
    expect(links.map((a) => (a as HTMLAnchorElement).getAttribute('href'))).toEqual(
      expect.arrayContaining(['/administration/u-1', '/administration/u-2', '/administration/u-3']),
    );
  });

  it('counts the selection 0/2, 1/2 then 2/2 and only enables "Comparer" at exactly two', () => {
    loadGroups();
    expect(text()).toContain('0/2');
    expect(compareButton()?.disabled).toBe(true);

    check(0);
    expect(text()).toContain('1/2');
    expect(compareButton()?.disabled).toBe(true);

    check(1);
    expect(text()).toContain('2/2');
    expect(compareButton()?.disabled).toBe(false);
  });

  it('caps the selection at two — a third checkbox is disabled and cannot be checked', () => {
    loadGroups();
    check(0);
    check(1);
    const third = checkboxes()[2];
    expect(third.disabled).toBe(true);

    third.checked = true;
    third.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(text()).toContain('2/2');
    expect(third.checked).toBe(false);
  });

  it('POSTs the two selected UUIDs to the compare endpoint and renders the side-by-side result', () => {
    loadGroups();
    check(0);
    check(1);
    compareButton()!.click();

    const req = http.expectOne(COMPARE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ firstUserId: 'u-1', secondUserId: 'u-2' });
    req.flush(COMPARISON);
    fixture.detectChanges();

    expect(text()).toContain('Fusion potentiellement sûre');
    expect(text()).toContain('Correspondances');
    expect(text()).toContain('Différences');
    expect(text()).toContain('Conflits bloquants');
    expect(text()).toContain('Avertissements');
    expect(text()).toContain('ESIC-2026-00001');
    expect(text()).toContain('Présences enregistrées');
  });

  it('shows a loading state while the comparison is in flight', () => {
    loadGroups();
    check(0);
    check(1);
    compareButton()!.click();
    fixture.detectChanges();
    expect(text()).toContain('Comparaison en cours');
    http.expectOne(COMPARE_URL).flush(COMPARISON);
  });

  it('shows a comparison error with a retry that re-issues the POST', () => {
    loadGroups();
    check(0);
    check(1);
    compareButton()!.click();
    http.expectOne(COMPARE_URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');

    const retry = [...el().querySelectorAll('button')].find(
      (b) => (b.textContent ?? '').trim() === 'Réessayer',
    ) as HTMLButtonElement;
    retry.click();
    http.expectOne(COMPARE_URL).flush(COMPARISON);
    fixture.detectChanges();
    expect(text()).toContain('Fusion potentiellement sûre');
  });

  it('closes the comparison panel and restores focus to the "Comparer" trigger', () => {
    loadGroups();
    check(0);
    check(1);
    const trigger = compareButton()!;
    trigger.focus();
    trigger.click();
    http.expectOne(COMPARE_URL).flush(COMPARISON);
    fixture.detectChanges();

    const close = [...el().querySelectorAll('button')].find(
      (b) => (b.textContent ?? '').trim() === 'Fermer',
    ) as HTMLButtonElement;
    close.click();
    fixture.detectChanges();
    expect(el().querySelector('.duplicates__comparison')).toBeNull();
  });

  it('never exposes an ENABLED merge or delete action — signalling + simulation only (docs/02 §9.5)', () => {
    loadGroups();
    check(0);
    check(1);
    compareButton()!.click();
    http.expectOne(COMPARE_URL).flush(COMPARISON);
    fixture.detectChanges();

    const mergeButtons = [...el().querySelectorAll('button')].filter((b) =>
      /fusionner|supprimer/i.test(b.textContent ?? ''),
    );
    expect(mergeButtons.length).toBeGreaterThan(0);
    expect(mergeButtons.every((b) => (b as HTMLButtonElement).disabled)).toBe(true);
  });

  it('renders an access-denied panel on a 403 from the API', () => {
    expectList().flush(
      { status: 403, code: 'USER_OPERATION_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter la détection de doublons");
  });

  it('shows a generic list error with a retry that re-requests the list', () => {
    expectList().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');

    internals.retry();
    expectList().flush([GROUP]);
    fixture.detectChanges();
    expect(text()).toContain('awa.diallo@esic.test');
  });
});
