import { HttpErrorResponse } from '@angular/common/http';

import { normalizeHttpError } from './api-error';

describe('normalizeHttpError', () => {
  it('keeps the backend business code and message for a 4xx ApiError body', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        timestamp: '2026-08-29T10:00:00Z',
        status: 409,
        code: 'ENR_DUPLICATE_STUDENT_NUMBER',
        message: 'Numéro étudiant déjà utilisé.',
        path: '/api/v1/student-profiles',
        correlationId: 'abc-123',
        details: ['studentNumber'],
      },
    });

    const normalized = normalizeHttpError(error);

    expect(normalized.status).toBe(409);
    expect(normalized.code).toBe('ENR_DUPLICATE_STUDENT_NUMBER');
    expect(normalized.message).toBe('Numéro étudiant déjà utilisé.');
    expect(normalized.correlationId).toBe('abc-123');
    expect(normalized.details).toEqual(['studentNumber']);
  });

  it('hides the server message for a 5xx response', () => {
    const error = new HttpErrorResponse({
      status: 500,
      error: {
        status: 500,
        code: 'INTERNAL_ERROR',
        message: 'NullPointerException at com.esic...',
        path: '/api/v1/whatever',
        correlationId: 'zzz',
        details: [],
      },
    });

    const normalized = normalizeHttpError(error);

    expect(normalized.code).toBe('INTERNAL_ERROR');
    expect(normalized.message).not.toContain('NullPointerException');
  });

  it('reports a dedicated code when the network is unreachable (status 0)', () => {
    const normalized = normalizeHttpError(new HttpErrorResponse({ status: 0 }));

    expect(normalized.code).toBe('NETWORK_UNAVAILABLE');
    expect(normalized.message).toContain('Impossible de joindre le serveur');
  });

  it('falls back to a generic error for a non-HTTP value', () => {
    const normalized = normalizeHttpError(new Error('boom'));

    expect(normalized.code).toBe('UNEXPECTED_ERROR');
    expect(normalized.message).toContain('Une erreur est survenue');
  });

  it('falls back to a generic code when the body is not an ApiError', () => {
    const normalized = normalizeHttpError(
      new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' }),
    );

    expect(normalized.code).toBe('UNEXPECTED_ERROR');
    expect(normalized.message).not.toContain('html');
  });
});
