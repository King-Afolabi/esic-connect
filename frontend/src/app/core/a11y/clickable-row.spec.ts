import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClickableRow } from './clickable-row';

@Component({
  standalone: true,
  imports: [ClickableRow],
  template: `
    <table>
      <tbody>
        <tr appClickableRow [appClickableRowDisabled]="disabled">
          <td>{{ label }}</td>
          <td><a href="/rows/1" (click)="onLinkClick($event)">Consulter</a></td>
          <td><button type="button" (click)="onButtonClick()">Action</button></td>
          <td><input type="checkbox" (click)="onCheckboxClick()" /></td>
        </tr>
      </tbody>
    </table>
  `,
})
class HostComponent {
  disabled = false;
  label = 'row';
  linkClicks = 0;
  buttonClicks = 0;
  checkboxClicks = 0;
  onLinkClick(event: Event): void {
    event.preventDefault(); // never actually navigate the test browser
    this.linkClicks++;
  }
  onButtonClick(): void {
    this.buttonClicks++;
  }
  onCheckboxClick(): void {
    this.checkboxClicks++;
  }
}

describe('ClickableRow (Lot 2)', () => {
  let fixture: ComponentFixture<HostComponent>;
  let row: HTMLTableRowElement;
  let link: HTMLAnchorElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    row = fixture.nativeElement.querySelector('tr');
    link = fixture.nativeElement.querySelector('a');
  });

  it('navigates via the real inner link on a click on non-interactive row content', () => {
    const linkClickSpy = vi.spyOn(link, 'click');
    const cell = fixture.nativeElement.querySelector('td') as HTMLElement;
    cell.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(linkClickSpy).toHaveBeenCalledTimes(1);
  });

  it('ignores a click originating from the link itself (no double navigation)', () => {
    const linkClickSpy = vi.spyOn(link, 'click');
    link.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    // La directive ne relaie pas le clic ; le lien natif gère seul son
    // activation (l'appel `click()` compté ici est le clic simulé
    // lui-même, jamais un second déclenché par la directive).
    expect(linkClickSpy).toHaveBeenCalledTimes(0);
    expect(fixture.componentInstance.linkClicks).toBe(1);
  });

  it('ignores a click on a button, an input, or a descendant of either', () => {
    const linkClickSpy = vi.spyOn(link, 'click');
    const button = fixture.nativeElement.querySelector('button') as HTMLElement;
    button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    const checkbox = fixture.nativeElement.querySelector('input') as HTMLElement;
    checkbox.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(linkClickSpy).not.toHaveBeenCalled();
    expect(fixture.componentInstance.buttonClicks).toBe(1);
    expect(fixture.componentInstance.checkboxClicks).toBe(1);
  });

  it('activates on Enter and on Space pressed on non-interactive row content', () => {
    const linkClickSpy = vi.spyOn(link, 'click');
    const cell = fixture.nativeElement.querySelector('td') as HTMLElement;
    cell.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    cell.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
    expect(linkClickSpy).toHaveBeenCalledTimes(2);
  });

  it('does not intercept Enter pressed while focus is on the inner link', () => {
    const linkClickSpy = vi.spyOn(link, 'click');
    link.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(linkClickSpy).not.toHaveBeenCalled();
  });

  it('is keyboard-focusable (a real tabindex), never overriding the link itself', () => {
    expect(row.getAttribute('tabindex')).toBe('0');
  });

  it('never navigates and drops its tabindex once bulk mode disables it', () => {
    // Fixture dédiée : `disabled` est vrai dès la toute première détection,
    // pour ne jamais faire varier l'expression entre deux vérifications.
    const bulkFixture = TestBed.createComponent(HostComponent);
    bulkFixture.componentInstance.disabled = true;
    bulkFixture.detectChanges();
    const bulkRow = bulkFixture.nativeElement.querySelector('tr') as HTMLTableRowElement;
    const bulkLink = bulkFixture.nativeElement.querySelector('a') as HTMLAnchorElement;
    const linkClickSpy = vi.spyOn(bulkLink, 'click');

    expect(bulkRow.getAttribute('tabindex')).toBeNull();
    const cell = bulkFixture.nativeElement.querySelector('td') as HTMLElement;
    cell.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(linkClickSpy).not.toHaveBeenCalled();
  });
});
