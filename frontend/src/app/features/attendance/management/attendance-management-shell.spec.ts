import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AttendanceManagementShell } from './attendance-management-shell';

function setup() {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  const fixture: ComponentFixture<AttendanceManagementShell> =
    TestBed.createComponent(AttendanceManagementShell);
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

describe('AttendanceManagementShell', () => {
  it('rend un seul titre de page « Suivi d\'assiduité »', () => {
    const { el } = setup();
    const headings = el.querySelectorAll('h1');
    expect(headings.length).toBe(1);
    expect(headings[0].textContent?.trim()).toBe("Suivi d'assiduité");
  });

  it('affiche les cinq vues comme navigation, « Synthèse » comprise et en premier', () => {
    const { el } = setup();
    const tabs = Array.from(el.querySelectorAll('.esic-subnav__link')) as HTMLAnchorElement[];
    expect(tabs.map((a) => a.textContent?.trim())).toEqual([
      'Synthèse',
      'Par séance',
      'Par classe',
      'Par apprenant',
      'Justificatifs',
    ]);
  });

  it('chaque onglet est un vrai lien de route vers sa vue', () => {
    const { el } = setup();
    const tabs = Array.from(el.querySelectorAll('.esic-subnav__link')) as HTMLAnchorElement[];
    expect(tabs.map((a) => a.getAttribute('href'))).toEqual([
      '/summary',
      '/sessions',
      '/classes',
      '/students',
      '/justifications',
    ]);
  });

  it('monte un exutoire de route pour la vue active', () => {
    const { el } = setup();
    expect(el.querySelector('router-outlet')).not.toBeNull();
  });
});
