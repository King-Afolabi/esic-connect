import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NotificationsShell } from './notifications-shell';

@Component({ template: 'liste' })
class StubList {}
@Component({ template: 'prefs' })
class StubPrefs {}

function configure() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        {
          path: 'notifications',
          component: NotificationsShell,
          children: [
            { path: '', pathMatch: 'full', redirectTo: 'centre' },
            { path: 'centre', component: StubList },
            { path: 'preferences', component: StubPrefs },
          ],
        },
      ]),
    ],
  });
}

/** Onglets `.esic-subnav__link` de la coquille avec leur état actif. */
function tabs() {
  const shell = document.querySelector('app-notifications-shell') as HTMLElement;
  return Array.from(shell.querySelectorAll('.esic-subnav__link')).map((a) => ({
    label: a.textContent?.trim(),
    active: a.getAttribute('aria-current') === 'page',
  }));
}

describe('NotificationsShell', () => {
  it('rend un seul titre de page et deux onglets', async () => {
    configure();
    await RouterTestingHarness.create('/notifications');
    const shell = document.querySelector('app-notifications-shell') as HTMLElement;
    expect(shell.querySelectorAll('h1').length).toBe(1);
    expect(shell.querySelector('h1')?.textContent?.trim()).toBe('Notifications');
    expect(tabs().map((t) => t.label)).toEqual(['Notifications', 'Préférences']);
  });

  it('sur /notifications (→ centre), seul « Notifications » est actif', async () => {
    configure();
    await RouterTestingHarness.create('/notifications');
    expect(tabs()).toEqual([
      { label: 'Notifications', active: true },
      { label: 'Préférences', active: false },
    ]);
  });

  it('sur /notifications/preferences, seul « Préférences » est actif', async () => {
    configure();
    const harness = await RouterTestingHarness.create('/notifications/centre');
    await harness.navigateByUrl('/notifications/preferences');
    expect(tabs()).toEqual([
      { label: 'Notifications', active: false },
      { label: 'Préférences', active: true },
    ]);
  });

  it('retour de préférences vers la liste : l’état actif suit, aucun résidu', async () => {
    configure();
    const harness = await RouterTestingHarness.create('/notifications/preferences');
    await harness.navigateByUrl('/notifications/centre');
    expect(tabs()).toEqual([
      { label: 'Notifications', active: true },
      { label: 'Préférences', active: false },
    ]);
  });
});
