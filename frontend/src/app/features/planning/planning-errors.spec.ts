import { HttpErrorResponse } from '@angular/common/http';

import { SAFE_FALLBACK_MESSAGE } from '../../core/models/api-error';
import { toPlanningError } from './planning-errors';

function apiError(status: number, code: string, message = 'msg'): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    statusText: 'x',
    error: { timestamp: '', status, code, message, path: '', correlationId: null, details: [] },
  });
}

describe('toPlanningError', () => {
  it('keeps the server message for a known 4xx code', () => {
    const view = toPlanningError(apiError(409, 'PLAN_BLOCKING_ISSUES', 'Corrigez le fichier.'));
    expect(view.code).toBe('PLAN_BLOCKING_ISSUES');
    expect(view.message).toBe('Corrigez le fichier.');
    expect(view.notFound).toBe(false);
    expect(view.forbidden).toBe(false);
  });

  it('flags a known 404 as notFound and any 403 as forbidden', () => {
    expect(toPlanningError(apiError(404, 'PLAN_JOB_NOT_FOUND')).notFound).toBe(true);
    expect(toPlanningError(apiError(404, 'PLAN_VERSION_NOT_FOUND')).notFound).toBe(true);
    const forbidden = toPlanningError(apiError(403, 'PLAN_SCOPE_FORBIDDEN'));
    expect(forbidden.forbidden).toBe(true);
  });

  it('masks an unknown code and any 5xx behind the generic message', () => {
    const unknown = toPlanningError(apiError(400, 'PLAN_FUTURE_CODE', 'internal'));
    expect(unknown.code).toBeNull();
    expect(unknown.message).toBe(SAFE_FALLBACK_MESSAGE);

    const server = toPlanningError(apiError(500, 'PLAN_BLOCKING_ISSUES', 'stacktrace'));
    expect(server.code).toBeNull();
    expect(server.message).toBe(SAFE_FALLBACK_MESSAGE);
  });

  it('falls back gracefully on a non-HTTP error', () => {
    const view = toPlanningError(new Error('boom'));
    expect(view.code).toBeNull();
    expect(view.message).toBe(SAFE_FALLBACK_MESSAGE);
  });
});
