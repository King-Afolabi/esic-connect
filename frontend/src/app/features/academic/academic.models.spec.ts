import { classGroupLabel } from './academic.models';

describe('classGroupLabel (Lots 14/15)', () => {
  it('formats "Nom — Code — Année"', () => {
    expect(
      classGroupLabel({
        name: 'Bachelor 3 Développement',
        code: 'B3-DEV-A',
        academicYearCode: '2026-2027',
      }),
    ).toBe('Bachelor 3 Développement — B3-DEV-A — 2026-2027');
  });

  it('omits a missing part instead of leaving a stray dash', () => {
    expect(classGroupLabel({ name: 'Classe 1', code: 'C1', academicYearCode: null })).toBe(
      'Classe 1 — C1',
    );
    expect(classGroupLabel({ name: null, code: 'C1' })).toBe('C1');
  });
});
