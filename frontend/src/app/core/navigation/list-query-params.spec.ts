import { ActivatedRoute, Params, Router } from '@angular/router';

import { ListQueryReader, writeListQueryParams } from './list-query-params';

function reader(queryParams: Params): ListQueryReader {
  return new ListQueryReader({ snapshot: { queryParams } } as unknown as ActivatedRoute);
}

describe('ListQueryReader', () => {
  it('str returns the value or the fallback', () => {
    const r = reader({ q: 'abc' });
    expect(r.str('q')).toBe('abc');
    expect(r.str('missing')).toBe('');
    expect(r.str('missing', 'x')).toBe('x');
    expect(reader({ q: '' }).str('q', 'x')).toBe('x');
  });

  it('int accepts non-negative integers only', () => {
    expect(reader({ page: '3' }).int('page', 0)).toBe(3);
    expect(reader({ page: '0' }).int('page', 5)).toBe(0);
    expect(reader({ page: '-1' }).int('page', 0)).toBe(0);
    expect(reader({ page: 'x' }).int('page', 0)).toBe(0);
    expect(reader({}).int('page', 7)).toBe(7);
  });

  it('oneOf constrains to a closed set', () => {
    const allowed = ['A', 'B'] as const;
    expect(reader({ s: 'B' }).oneOf('s', allowed, 'A')).toBe('B');
    expect(reader({ s: 'Z' }).oneOf('s', allowed, 'A')).toBe('A');
    expect(reader({}).oneOf('s', allowed, 'A')).toBe('A');
  });

  it('direction only accepts asc/desc', () => {
    expect(reader({ dir: 'asc' }).direction('dir', 'desc')).toBe('asc');
    expect(reader({ dir: 'DESC' }).direction('dir', 'asc')).toBe('asc');
  });

  it('hasAny detects a state worth restoring', () => {
    expect(reader({ q: 'x' }).hasAny(['q', 'status'])).toBe(true);
    expect(reader({ q: '' }).hasAny(['q'])).toBe(false);
    expect(reader({}).hasAny(['q'])).toBe(false);
  });
});

describe('writeListQueryParams', () => {
  it('navigates with merge + replaceUrl and nulls out empty / zero values', () => {
    const navigate = vi.fn();
    const route = {} as ActivatedRoute;
    writeListQueryParams({ navigate } as unknown as Router, route, {
      q: 'abc',
      status: '',
      page: 0,
      size: 50,
      sort: null,
    });

    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: route,
      queryParams: { q: 'abc', status: null, page: null, size: '50', sort: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });
});
