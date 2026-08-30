import { CanonicalPatternConfiguration } from './alternation.models';
import {
  buildCyclePreview,
  buildPatternConfiguration,
  classifyCyclePreviewCell,
  emptyPatternConfigFormValue,
  formValueFromCanonical,
} from './pattern-config';

describe('buildPatternConfiguration', () => {
  const base = emptyPatternConfigFormValue();

  it('THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY sends explicit school + company days, no cycleLengthWeeks', () => {
    const built = buildPatternConfiguration('THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY', {
      ...base,
      threeTwoDays: {
        MONDAY: 'SCHOOL',
        TUESDAY: 'SCHOOL',
        WEDNESDAY: 'SCHOOL',
        THURSDAY: 'COMPANY',
        FRIDAY: 'COMPANY',
      },
    });
    expect(built.errors).toEqual([]);
    expect(built.cycleLengthWeeks).toBeNull();
    expect(built.configuration).toEqual({
      schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY'],
      companyDays: ['THURSDAY', 'FRIDAY'],
    });
  });

  it('flags an incomplete 3j/2j classification', () => {
    const built = buildPatternConfiguration('THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY', {
      ...base,
      threeTwoDays: {
        MONDAY: 'SCHOOL',
        TUESDAY: 'SCHOOL',
        WEDNESDAY: 'SCHOOL',
        THURSDAY: 'SCHOOL',
        FRIDAY: 'SCHOOL',
      },
    });
    expect(built.errors.length).toBeGreaterThan(0);
  });

  it('ONE_WEEK_SCHOOL_OUT_OF_FOUR omits schoolDays by default and derives companyWeeks', () => {
    const built = buildPatternConfiguration('ONE_WEEK_SCHOOL_OUT_OF_FOUR', {
      ...base,
      oneWeekSchoolWeek: 2,
      weeksOutOfFourSchoolDays: null,
    });
    expect(built.errors).toEqual([]);
    expect(built.configuration).toEqual({ schoolWeeks: [2], companyWeeks: [1, 3, 4] });
    expect(built.preview.schoolDays).toEqual(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
  });

  it('ONE_WEEK_SCHOOL_OUT_OF_FOUR includes schoolDays only when explicitly restricted', () => {
    const built = buildPatternConfiguration('ONE_WEEK_SCHOOL_OUT_OF_FOUR', {
      ...base,
      oneWeekSchoolWeek: 1,
      weeksOutOfFourSchoolDays: ['MONDAY', 'TUESDAY'],
    });
    expect(built.configuration).toEqual({
      schoolWeeks: [1],
      companyWeeks: [2, 3, 4],
      schoolDays: ['MONDAY', 'TUESDAY'],
    });
  });

  it('TWO_WEEKS_SCHOOL_OUT_OF_FOUR requires exactly two school weeks', () => {
    const ok = buildPatternConfiguration('TWO_WEEKS_SCHOOL_OUT_OF_FOUR', {
      ...base,
      twoWeeksSchoolWeeks: [1, 3],
    });
    expect(ok.errors).toEqual([]);
    expect(ok.configuration).toEqual({ schoolWeeks: [1, 3], companyWeeks: [2, 4] });

    const bad = buildPatternConfiguration('TWO_WEEKS_SCHOOL_OUT_OF_FOUR', {
      ...base,
      twoWeeksSchoolWeeks: [1],
    });
    expect(bad.errors.length).toBeGreaterThan(0);
  });

  it('CUSTOM sends all five canonical keys and keeps companyDays even when empty', () => {
    const built = buildPatternConfiguration('CUSTOM', {
      ...base,
      customCycleLengthWeeks: 3,
      customWeekRoles: ['SCHOOL', 'COMPANY', 'UNCLASSIFIED'],
      customSchoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      customCompanyDays: [],
    });
    expect(built.errors).toEqual([]);
    expect(built.cycleLengthWeeks).toBe(3);
    expect(built.configuration).toEqual({
      cycleLengthWeeks: 3,
      schoolWeeks: [1],
      companyWeeks: [2],
      schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      companyDays: [],
    });
  });

  it('CUSTOM flags a school/company day intersection and an all-unclassified cycle', () => {
    const overlap = buildPatternConfiguration('CUSTOM', {
      ...base,
      customCycleLengthWeeks: 2,
      customWeekRoles: ['SCHOOL', 'COMPANY'],
      customSchoolDays: ['MONDAY'],
      customCompanyDays: ['MONDAY'],
    });
    expect(overlap.errors.some((e) => e.includes('à la fois'))).toBe(true);

    const empty = buildPatternConfiguration('CUSTOM', {
      ...base,
      customCycleLengthWeeks: 2,
      customWeekRoles: ['UNCLASSIFIED', 'UNCLASSIFIED'],
    });
    expect(empty.errors.some((e) => e.includes('au moins une semaine'))).toBe(true);
  });

  it('CUSTOM rejects a non-positive cycle length', () => {
    const built = buildPatternConfiguration('CUSTOM', {
      ...base,
      customCycleLengthWeeks: 0,
      customWeekRoles: [],
    });
    expect(built.errors.some((e) => e.includes('strictement positif'))).toBe(true);
  });
});

describe('formValueFromCanonical (round trip for the four types)', () => {
  const roundTrip = (
    type: Parameters<typeof buildPatternConfiguration>[0],
    config: CanonicalPatternConfiguration,
    expected: Record<string, unknown>,
  ) => {
    const formValue = formValueFromCanonical(type, config);
    const built = buildPatternConfiguration(type, formValue);
    expect(built.errors).toEqual([]);
    expect(built.configuration).toEqual(expected);
  };

  it('THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY', () => {
    roundTrip(
      'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY',
      {
        cycleLengthWeeks: 1,
        schoolWeeks: [1],
        companyWeeks: [],
        schoolDays: ['MONDAY', 'TUESDAY'],
        companyDays: ['WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      },
      { schoolDays: ['MONDAY', 'TUESDAY'], companyDays: ['WEDNESDAY', 'THURSDAY', 'FRIDAY'] },
    );
  });

  it('ONE_WEEK_SCHOOL_OUT_OF_FOUR keeps a restricted school-day set', () => {
    roundTrip(
      'ONE_WEEK_SCHOOL_OUT_OF_FOUR',
      {
        cycleLengthWeeks: 4,
        schoolWeeks: [3],
        companyWeeks: [1, 2, 4],
        schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY'],
        companyDays: [],
      },
      { schoolWeeks: [3], companyWeeks: [1, 2, 4], schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY'] },
    );
  });

  it('TWO_WEEKS_SCHOOL_OUT_OF_FOUR with the default full school week omits schoolDays', () => {
    roundTrip(
      'TWO_WEEKS_SCHOOL_OUT_OF_FOUR',
      {
        cycleLengthWeeks: 4,
        schoolWeeks: [2, 4],
        companyWeeks: [1, 3],
        schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
        companyDays: [],
      },
      { schoolWeeks: [2, 4], companyWeeks: [1, 3] },
    );
  });

  it('CUSTOM including empty companyDays', () => {
    roundTrip(
      'CUSTOM',
      {
        cycleLengthWeeks: 3,
        schoolWeeks: [1, 2],
        companyWeeks: [3],
        schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
        companyDays: [],
      },
      {
        cycleLengthWeeks: 3,
        schoolWeeks: [1, 2],
        companyWeeks: [3],
        schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
        companyDays: [],
      },
    );
  });
});

describe('cycle preview (representation only)', () => {
  it('classifies a (week, day) cell exactly like the server rule', () => {
    const config: CanonicalPatternConfiguration = {
      cycleLengthWeeks: 4,
      schoolWeeks: [1],
      companyWeeks: [2, 3, 4],
      schoolDays: ['MONDAY', 'TUESDAY'],
      companyDays: [],
    };
    expect(classifyCyclePreviewCell(config, 1, 'MONDAY')).toBe('SCHOOL');
    // Week 1, Wednesday: school week but not a school day and no company day → UNKNOWN.
    expect(classifyCyclePreviewCell(config, 1, 'WEDNESDAY')).toBe('UNKNOWN');
    expect(classifyCyclePreviewCell(config, 3, 'MONDAY')).toBe('COMPANY');
    // Unclassified week → UNKNOWN.
    expect(
      classifyCyclePreviewCell({ ...config, schoolWeeks: [], companyWeeks: [] }, 1, 'MONDAY'),
    ).toBe('UNKNOWN');
  });

  it('builds one row per cycle week and five cells per row', () => {
    const preview = buildCyclePreview({
      cycleLengthWeeks: 2,
      schoolWeeks: [1],
      companyWeeks: [2],
      schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      companyDays: [],
    });
    expect(preview.map((w) => w.weekIndex)).toEqual([1, 2]);
    expect(preview[0].cells.map((c) => c.day)).toEqual([
      'MONDAY',
      'TUESDAY',
      'WEDNESDAY',
      'THURSDAY',
      'FRIDAY',
    ]);
    expect(preview[0].cells.every((c) => c.context === 'SCHOOL')).toBe(true);
    expect(preview[1].cells.every((c) => c.context === 'COMPANY')).toBe(true);
  });
});
