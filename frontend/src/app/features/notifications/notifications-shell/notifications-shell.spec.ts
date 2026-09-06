import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { NotificationsShell } from './notifications-shell';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideRouter([])],
  });
  const fixture: ComponentFixture<NotificationsShell> =
    TestBed.createComponent(NotificationsShell);
  fixture.detectChanges();
  return {
    fixture,
    el: fixture.nativeElement as HTMLElement,
  };
}

describe('NotificationsShell', () => {
  it('rend un seul titre de page « Notifications »', () => {
    const { el } = setup();
    const headings = el.querySelectorAll('h1');
    expect(headings.length).toBe(1);
    expect(headings[0].textContent?.trim()).toBe('Notifications');
  });

  it('expose exactement deux onglets : Notifications et Préférences', () => {
    const { el } = setup();
    const tabs = Array.from(el.querySelectorAll('.esic-subnav__link')) as HTMLAnchorElement[];
    expect(tabs.map((a) => a.textContent?.trim())).toEqual(['Notifications', 'Préférences']);
  });

  it('l’onglet « Notifications » pointe sur la vue racine avec correspondance exacte', () => {
    const { el } = setup();
    const [list, prefs] = Array.from(
      el.querySelectorAll('.esic-subnav__link'),
    ) as HTMLAnchorElement[];
    // `routerLink="."` : la vue liste, sans rester actif sur /preferences.
    expect(list.getAttribute('href')).toBe('/');
    expect(prefs.getAttribute('href')).toBe('/preferences');
  });

  it('monte un exutoire de route pour la vue interne active', () => {
    const { el } = setup();
    expect(el.querySelector('router-outlet')).not.toBeNull();
  });
});
