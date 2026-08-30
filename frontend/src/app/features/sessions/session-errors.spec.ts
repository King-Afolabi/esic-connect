import { HttpErrorResponse } from '@angular/common/http';

import { SAFE_FALLBACK_MESSAGE } from '../../core/models/api-error';
import { toSessionError } from './session-errors';

function apiError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: { timestamp: 't', status, code, message, path: '/x', correlationId: 'c-1', details: [] },
  });
}

describe('toSessionError', () => {
  it('keeps a known SESSION_* code and its safe server message', () => {
    const view = toSessionError(apiError(409, 'SESSION_INVALID_STATE', 'État invalide.'));
    expect(view.code).toBe('SESSION_INVALID_STATE');
    expect(view.message).toBe('État invalide.');
    expect(view.forbidden).toBe(false);
    expect(view.notFound).toBe(false);
  });

  it('keeps a known ATT_* code and flags 404 / 403 / 503', () => {
    expect(toSessionError(apiError(404, 'SESSION_NOT_FOUND', 'Introuvable.')).notFound).toBe(true);
    expect(toSessionError(apiError(403, 'SESSION_OPERATION_FORBIDDEN', 'Refusé.')).forbidden).toBe(true);
    const unavailable = toSessionError(
      apiError(503, 'ATT_TOKEN_BACKEND_UNAVAILABLE', 'Service indisponible.'),
    );
    expect(unavailable.backendUnavailable).toBe(true);
    expect(unavailable.code).toBe('ATT_TOKEN_BACKEND_UNAVAILABLE');
  });

  it('drops an unknown code and its arbitrary message in favour of the generic fallback', () => {
    const view = toSessionError(apiError(400, 'SESSION_FUTURE_CODE', 'détail interne arbitraire'));
    expect(view.code).toBeNull();
    expect(view.message).toBe(SAFE_FALLBACK_MESSAGE);
    expect(view.message).not.toContain('arbitraire');
  });

  it('masks a 5xx: no code, generic message, never the raw server body', () => {
    const view = toSessionError(
      new HttpErrorResponse({
        status: 500,
        error: { status: 500, code: 'INTERNAL_ERROR', message: 'stacktrace: NPE at line 42', details: [] },
      }),
    );
    expect(view.code).toBeNull();
    expect(view.message).toBe(SAFE_FALLBACK_MESSAGE);
    expect(view.message).not.toContain('stacktrace');
  });

  it('gives a controlled, meaningful message for a 503 token-backend outage', () => {
    const view = toSessionError(
      apiError(503, 'ATT_TOKEN_BACKEND_UNAVAILABLE', 'redis connection refused at 10.0.0.1'),
    );
    expect(view.backendUnavailable).toBe(true);
    expect(view.message).toContain('momentanément indisponible');
    expect(view.message).not.toContain('redis');
  });

  it('handles a non-HTTP error with the generic fallback', () => {
    const view = toSessionError(new Error('boom'));
    expect(view.code).toBeNull();
    expect(view.message).toBe(SAFE_FALLBACK_MESSAGE);
  });
});
