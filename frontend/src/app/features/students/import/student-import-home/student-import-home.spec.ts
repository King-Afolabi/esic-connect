import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { signal, WritableSignal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';

import { Role } from '../../../../core/models/role';
import { RoleContextService } from '../../../../core/auth/role-context.service';
import { StudentImportHome } from './student-import-home';

interface Internals {
  onFileSelected: (event: Event) => void;
  launch: () => void;
  selectedFile: () => File | null;
  fileError: () => string | null;
  canSubmit: () => boolean;
  errorMessage: () => string | null;
}

const LIST_URL = '/api/v1/student-imports';

function fileEvent(file: File | null): Event {
  return { target: { files: file ? [file] : [] } } as unknown as Event;
}

function setup(roles: Role[] = ['ADMIN']) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  const navigate = vi.fn().mockResolvedValue(true);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles } },
    ],
  });
  const router = TestBed.inject(Router);
  router.navigate = navigate as unknown as Router['navigate'];
  const fixture = TestBed.createComponent(StudentImportHome);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  http.expectOne((r) => r.url === LIST_URL && r.method === 'GET').flush({
    content: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  });
  return {
    fixture,
    http,
    navigate,
    effectiveRoles,
    internals: fixture.componentInstance as unknown as Internals,
  };
}

describe('StudentImportHome', () => {
  it('rejects a non-csv file and an oversized file on the client', () => {
    const { internals } = setup();
    internals.onFileSelected(fileEvent(new File(['x'], 'liste.xlsx')));
    expect(internals.selectedFile()).toBeNull();
    expect(internals.fileError()).toContain('.csv');

    const big = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.csv', { type: 'text/csv' });
    internals.onFileSelected(fileEvent(big));
    expect(internals.selectedFile()).toBeNull();
    expect(internals.fileError()).toContain('2 Mo');
  });

  it('enables the submit only with a valid file and navigates to the review on success', () => {
    const { internals, http, navigate } = setup();
    expect(internals.canSubmit()).toBe(false);

    internals.onFileSelected(fileEvent(new File(['last_name\n'], 'ok.csv', { type: 'text/csv' })));
    expect(internals.canSubmit()).toBe(true);

    internals.launch();
    const req = http.expectOne((r) => r.url === LIST_URL && r.method === 'POST');
    req.flush({ publicId: 'job-1' });
    expect(navigate).toHaveBeenCalledWith(['/students/import', 'job-1']);
  });

  it('maps a global anomaly to a controlled inline message (never the raw body)', () => {
    const { internals, http } = setup();
    internals.onFileSelected(fileEvent(new File(['x'], 'bad.csv', { type: 'text/csv' })));
    internals.launch();
    http.expectOne((r) => r.method === 'POST').flush(
      {
        timestamp: 't',
        status: 400,
        code: 'IMP_MISSING_COLUMN',
        message: 'Une ou plusieurs colonnes obligatoires sont absentes de l’en-tête.',
        path: '/api/v1/student-imports',
        correlationId: null,
        details: ['email'],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    expect(internals.errorMessage()).toContain('colonnes obligatoires');
    expect(internals.errorMessage()).toContain('email');
  });

  it('disables the form when the active role context cannot import', () => {
    const { internals, effectiveRoles } = setup(['TEACHER']);
    expect(internals.canSubmit()).toBe(false);
    effectiveRoles.set(['ADMIN']);
    internals.onFileSelected(fileEvent(new File(['x'], 'ok.csv', { type: 'text/csv' })));
    expect(internals.canSubmit()).toBe(true);
  });

  it('submits the real form element: ngSubmit reaches the simulation without a native reload', () => {
    const { fixture, internals, http, navigate } = setup();
    internals.onFileSelected(fileEvent(new File(['last_name\n'], 'ok.csv', { type: 'text/csv' })));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form.upload') as HTMLFormElement;
    expect(form).not.toBeNull();
    // Le `[formGroup]` doit porter sur le <form> lui-même : sans lui, Angular
    // n'attache aucune FormGroupDirective et `(ngSubmit)` ne se déclenche jamais
    // — le clic partirait en soumission native (rechargement de page).
    expect(form.getAttribute('novalidate')).not.toBeNull();

    // Un vrai événement `submit`, annulable, tel que l'émet le navigateur.
    const event = new Event('submit', { bubbles: true, cancelable: true });
    form.dispatchEvent(event);

    // FormGroupDirective appelle preventDefault : aucune navigation native.
    expect(event.defaultPrevented).toBe(true);

    const req = http.expectOne((r) => r.url === LIST_URL && r.method === 'POST');
    req.flush({ publicId: 'job-submit' });
    expect(navigate).toHaveBeenCalledWith(['/students/import', 'job-submit']);
  });

  it('submits through a click on the submit button', () => {
    const { fixture, internals, http, navigate } = setup();
    internals.onFileSelected(fileEvent(new File(['last_name\n'], 'ok.csv', { type: 'text/csv' })));
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button).not.toBeNull();
    expect(button.disabled).toBe(false);
    button.click();

    const req = http.expectOne((r) => r.url === LIST_URL && r.method === 'POST');
    req.flush({ publicId: 'job-click' });
    expect(navigate).toHaveBeenCalledWith(['/students/import', 'job-click']);
  });

  it('never touches browser storage', () => {
    setup();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
