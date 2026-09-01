import { HttpErrorResponse } from '@angular/common/http';

import { SAFE_FALLBACK_MESSAGE } from '../../core/models/api-error';
import { toOrganizationError } from './organization-errors';

function apiError(status: number, code: string, message = 'msg'): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    statusText: 'x',
    error: { timestamp: '', status, code, message, path: '', correlationId: null, details: [] },
  });
}

describe('toOrganizationError', () => {
  it('keeps the server message for a known 4xx code', () => {
    const view = toOrganizationError(apiError(409, 'ORG_DUPLICATE_CODE', 'Code déjà utilisé.'));
    expect(view.code).toBe('ORG_DUPLICATE_CODE');
    expect(view.message).toBe('Code déjà utilisé.');
    expect(view.field).toBe('code');
    expect(view.forbidden).toBe(false);
    expect(view.notFound).toBe(false);
  });

  it('maps field-bound codes to their form control', () => {
    expect(toOrganizationError(apiError(400, 'ORG_INVALID_TIME_ZONE')).field).toBe('timeZoneId');
    expect(toOrganizationError(apiError(400, 'ORG_INVALID_COUNTRY_CODE')).field).toBe('countryCode');
    expect(toOrganizationError(apiError(400, 'ORG_INVALID_CIDR')).field).toBe('cidr');
    expect(toOrganizationError(apiError(400, 'ORG_BUILDING_SITE_MISMATCH')).field).toBe(
      'buildingPublicId',
    );
  });

  it('flags a known 404 as notFound and a 403 as forbidden', () => {
    expect(toOrganizationError(apiError(404, 'SITE_NOT_FOUND')).notFound).toBe(true);
    const forbidden = toOrganizationError(apiError(403, 'ANYTHING'));
    expect(forbidden.forbidden).toBe(true);
    expect(forbidden.code).toBeNull();
  });

  it('masks an unknown code and any 5xx behind the generic message', () => {
    const unknown = toOrganizationError(apiError(400, 'ORG_FUTURE_CODE', 'internal detail'));
    expect(unknown.code).toBeNull();
    expect(unknown.field).toBeNull();
    expect(unknown.message).toBe(SAFE_FALLBACK_MESSAGE);

    const server = toOrganizationError(apiError(500, 'ORG_DUPLICATE_CODE', 'stacktrace'));
    expect(server.code).toBeNull();
    expect(server.message).toBe(SAFE_FALLBACK_MESSAGE);
  });

  it('falls back gracefully on a non-HTTP error', () => {
    const view = toOrganizationError(new Error('boom'));
    expect(view.code).toBeNull();
    expect(view.message).toBe(SAFE_FALLBACK_MESSAGE);
  });
});
