import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { QrDisplay } from './qr-display';

@Component({
  imports: [QrDisplay],
  template: `<app-qr-display [value]="value()"></app-qr-display>`,
})
class Host {
  readonly value = signal<string | null>(null);
}

function render(value: string | null) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ imports: [Host] });
  const fixture = TestBed.createComponent(Host);
  fixture.componentInstance.value.set(value);
  fixture.detectChanges();
  return fixture;
}

describe('QrDisplay', () => {
  it('shows a neutral message and no <qrcode> when there is no value', () => {
    const el = render(null).nativeElement as HTMLElement;
    expect(el.querySelector('qrcode')).toBeNull();
    expect(el.textContent).toContain('Aucun QR code disponible');
  });

  it('renders a <qrcode> that encodes the opaque value without exposing it as text', () => {
    const el = render('OPAQUE-SERVER-TOKEN').nativeElement as HTMLElement;
    expect(el.querySelector('qrcode')).not.toBeNull();
    // La valeur du jeton n'apparaît jamais en texte dans le DOM rendu.
    expect(el.textContent).not.toContain('OPAQUE-SERVER-TOKEN');
    const img = el.querySelector('img');
    if (img) {
      expect(img.getAttribute('alt') ?? '').not.toContain('OPAQUE-SERVER-TOKEN');
    }
  });
});
