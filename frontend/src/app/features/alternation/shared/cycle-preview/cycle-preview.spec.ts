import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CanonicalPatternConfiguration } from '../../alternation.models';
import { CyclePreview } from './cycle-preview';

const CONFIG: CanonicalPatternConfiguration = {
  cycleLengthWeeks: 2,
  schoolWeeks: [1],
  companyWeeks: [2],
  schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  companyDays: [],
};

describe('CyclePreview', () => {
  let fixture: ComponentFixture<CyclePreview>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CyclePreview] });
    fixture = TestBed.createComponent(CyclePreview);
    fixture.componentRef.setInput('config', CONFIG);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;

  it('renders one table row per cycle week with a row header', () => {
    const bodyRows = el().querySelectorAll('tbody tr');
    expect(bodyRows.length).toBe(2);
    expect(el().textContent).toContain('Semaine 1');
    expect(el().textContent).toContain('Semaine 2');
  });

  it('labels every cell with text, not colour alone', () => {
    const cells = Array.from(el().querySelectorAll('tbody td'));
    expect(cells.length).toBe(10);
    expect(cells.every((c) => (c.textContent ?? '').trim().length > 0)).toBe(true);
    expect(el().textContent).toContain('École');
    expect(el().textContent).toContain('Entreprise');
  });

  it('provides a caption stating it represents the configuration, not a real date', () => {
    const caption = el().querySelector('caption');
    expect(caption?.textContent).toContain('ne détermine pas le contexte');
  });

  it('provides an accessible legend', () => {
    expect(el().querySelector('.cycle-preview__legend')?.getAttribute('aria-label')).toBe('Légende');
  });
});
