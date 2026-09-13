import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { Role } from '../../../core/models/role';
import { SubjectPage, SubjectResponse } from '../subjects.models';
import { SubjectList } from './subject-list';

const LIST_URL = '/api/v1/subjects';

const SUBJECT: SubjectResponse = {
  id: 'sub-1',
  code: 'MATH101',
  name: 'Mathématiques',
  description: null,
  hourlyVolume: 40,
  status: 'ACTIVE',
  programs: [],
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

function page(content: SubjectResponse[]): SubjectPage {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

function setup(effectiveRoles: Role[]) {
  localStorage.clear();
  sessionStorage.clear();

  const roles = signal<Role[]>(effectiveRoles);

  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles: roles } },
    ],
  });

  const fixture: ComponentFixture<SubjectList> = TestBed.createComponent(SubjectList);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();

  return {
    fixture,
    http,
    roles,
    el: () => fixture.nativeElement as HTMLElement,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    listReq: (): TestRequest => http.expectOne((r) => r.url === LIST_URL),
  };
}

describe('SubjectList — write-control visibility (Lot 5)', () => {
  it('shows the add form and archive/restore actions to ADMIN', () => {
    const { fixture, listReq, text, http } = setup(['ADMIN']);
    listReq().flush(page([SUBJECT]));
    fixture.detectChanges();

    expect(text()).toContain('Ajouter');
    expect(text()).toContain('Archiver');
    http.verify();
  });

  it('shows the add form and archive/restore actions to SUPER_ADMIN', () => {
    const { fixture, listReq, text, http } = setup(['SUPER_ADMIN']);
    listReq().flush(page([SUBJECT]));
    fixture.detectChanges();

    expect(text()).toContain('Ajouter');
    expect(text()).toContain('Archiver');
    http.verify();
  });

  it('shows the add form and archive/restore actions to PEDAGOGICAL_MANAGER', () => {
    const { fixture, listReq, text, http } = setup(['PEDAGOGICAL_MANAGER']);
    listReq().flush(page([SUBJECT]));
    fixture.detectChanges();

    expect(text()).toContain('Ajouter');
    expect(text()).toContain('Archiver');
    http.verify();
  });

  it('lets TEACHER read the catalogue but hides every write control', () => {
    const { fixture, listReq, text, el, http } = setup(['TEACHER']);
    listReq().flush(page([SUBJECT]));
    fixture.detectChanges();

    expect(text()).toContain('Mathématiques');
    expect(text()).not.toContain('Ajouter');
    expect(text()).not.toContain('Archiver');
    expect(text()).not.toContain('Restaurer');
    expect(el().querySelector('form')).toBeNull();
    http.verify();
  });

  it('lets SCHOOL_ADMINISTRATION read the catalogue but hides every write control', () => {
    const { fixture, listReq, text, el, http } = setup(['SCHOOL_ADMINISTRATION']);
    listReq().flush(page([SUBJECT]));
    fixture.detectChanges();

    expect(text()).toContain('Mathématiques');
    expect(text()).not.toContain('Ajouter');
    expect(el().querySelector('form')).toBeNull();
    http.verify();
  });

  it('never hides the list itself, the read-only view, or the archived filter', () => {
    const { fixture, listReq, text, el, http } = setup(['TEACHER']);
    listReq().flush(page([SUBJECT]));
    fixture.detectChanges();

    expect(el().querySelector('table')).not.toBeNull();
    expect(text()).toContain('Afficher les matières archivées');
    http.verify();
  });

  it('recomputes write-control visibility when the effective role context changes (multi-role account)', () => {
    const { fixture, listReq, roles, text, http } = setup(['TEACHER']);
    listReq().flush(page([SUBJECT]));
    fixture.detectChanges();
    expect(text()).not.toContain('Ajouter');

    roles.set(['ADMIN']);
    fixture.detectChanges();
    expect(text()).toContain('Ajouter');
    http.verify();
  });
});
