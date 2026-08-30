import { HttpErrorResponse } from '@angular/common/http';

import { toAdministrationError } from './administration-errors';

function apiError(status: number, code: string, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    statusText: 'x',
    error: { status, code, message, path: '/api/v1/users/u-1', correlationId: 'abc', details: [] },
  });
}

describe('toAdministrationError', () => {
  it('keeps the safe server message and business code for a known USER_* conflict', () => {
    const view = toAdministrationError(
      apiError(409, 'USER_INVALID_STATE', "L'état actuel du compte ne permet pas cette opération."),
    );
    expect(view.status).toBe(409);
    expect(view.code).toBe('USER_INVALID_STATE');
    expect(view.message).toBe("L'état actuel du compte ne permet pas cette opération.");
    expect(view.field).toBeNull();
  });

  it('marks the self-action and super-admin protections as global (no field)', () => {
    expect(toAdministrationError(apiError(409, 'USER_SELF_ACTION_FORBIDDEN', 'x')).field).toBeNull();
    expect(toAdministrationError(apiError(403, 'USER_SUPER_ADMIN_PROTECTED', 'x')).code).toBe(
      'USER_SUPER_ADMIN_PROTECTED',
    );
    expect(toAdministrationError(apiError(403, 'USER_OPERATION_FORBIDDEN', 'x')).status).toBe(403);
  });

  it('attaches USER_ROLE_UNKNOWN to the role field', () => {
    const view = toAdministrationError(apiError(400, 'USER_ROLE_UNKNOWN', 'Code de rôle inconnu.'));
    expect(view.field).toBe('role');
    expect(view.message).toBe('Code de rôle inconnu.');
  });

  it('never surfaces a 5xx body and reports no business code', () => {
    const view = toAdministrationError(
      new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: 'stacktrace leak' }),
    );
    expect(view.code).toBeNull();
    expect(view.field).toBeNull();
    expect(view.message).not.toContain('stacktrace');
  });

  it('masks a 5xx even when its body is a structured ApiError carrying a message/trace', () => {
    const view = toAdministrationError(
      apiError(
        500,
        'USER_INVALID_STATE',
        'NullPointerException at com.esic.connect.identity.internal.UserManagementService.suspend',
      ),
    );
    expect(view.status).toBe(500);
    expect(view.code).toBeNull();
    expect(view.field).toBeNull();
    expect(view.message).not.toContain('NullPointerException');
    expect(view.message).not.toContain('com.esic.connect');
  });

  it('falls back to a generic view for an unknown code that is not USER_*', () => {
    const view = toAdministrationError(apiError(418, 'SOMETHING_ELSE', 'weird arbitrary message'));
    expect(view.code).toBeNull();
    expect(view.field).toBeNull();
    expect(view.message).not.toContain('weird arbitrary message');
  });

  it('falls back to a generic view for an unknown code that happens to start with USER_', () => {
    const view = toAdministrationError(
      apiError(400, 'USER_SOMETHING_UNKNOWN', 'do not show this arbitrary text'),
    );
    expect(view.code).toBeNull();
    expect(view.field).toBeNull();
    expect(view.message).not.toContain('do not show this arbitrary text');
  });
});
