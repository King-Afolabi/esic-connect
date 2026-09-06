import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AttendanceManagementShell } from './attendance-management-shell';

@Component({ template: 'vue' })
class StubView {}

function configure() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        {
          path: 'attendance-management',
          component: AttendanceManagementShell,
          children: [
            { path: '', pathMatch: 'full', redirectTo: 'summary' },
            { path: 'summary', component: StubView },
            { path: 'sessions', component: StubView },
            { path: 'classes', component: StubView },
            { path: 'students', component: StubView },
            { path: 'justifications', component: StubView },
          ],
        },
      ]),
    ],
  });
}

function tabs() {
  const shell = document.querySelector('app-attendance-management-shell') as HTMLElement;
  return Array.from(shell.querySelectorAll('.esic-subnav__link')).map((a) => ({
    label: a.textContent?.trim(),
    active: a.getAttribute('aria-current') === 'page',
  }));
}

describe('AttendanceManagementShell', () => {
  it('titre unique + cinq vues, « Synthèse » listée et en premier', async () => {
    configure();
    await RouterTestingHarness.create('/attendance-management/summary');
    const shell = document.querySelector('app-attendance-management-shell') as HTMLElement;
    expect(shell.querySelectorAll('h1').length).toBe(1);
    expect(shell.querySelector('h1')?.textContent?.trim()).toBe("Suivi d'assiduité");
    expect(tabs().map((t) => t.label)).toEqual([
      'Synthèse',
      'Par séance',
      'Par classe',
      'Par apprenant',
      'Justificatifs',
    ]);
  });

  it('un seul onglet actif par vue, et il suit la navigation sans résidu', async () => {
    configure();
    const harness = await RouterTestingHarness.create('/attendance-management/summary');
    expect(tabs().filter((t) => t.active).map((t) => t.label)).toEqual(['Synthèse']);

    await harness.navigateByUrl('/attendance-management/classes');
    expect(tabs().filter((t) => t.active).map((t) => t.label)).toEqual(['Par classe']);

    await harness.navigateByUrl('/attendance-management/justifications');
    expect(tabs().filter((t) => t.active).map((t) => t.label)).toEqual(['Justificatifs']);

    // Retour vers « Synthèse » : aucun onglet précédent ne reste actif.
    await harness.navigateByUrl('/attendance-management/summary');
    expect(tabs().filter((t) => t.active).map((t) => t.label)).toEqual(['Synthèse']);
  });
});
