import { notificationLink, notificationTypeLabel } from './notifications.models';

describe('notifications.models — liens G1-E', () => {
  it('links a JUSTIFICATION notification to /my-attendance only for a STUDENT', () => {
    const n = { resourceType: 'JUSTIFICATION', resourcePublicId: 'j-1' };
    expect(notificationLink(n, ['STUDENT'])).toEqual({
      commands: ['/my-attendance'],
      label: 'Voir mes présences',
    });
    expect(notificationLink(n, ['TEACHER'])).toBeNull();
    expect(notificationLink(n, ['PEDAGOGICAL_MANAGER'])).toBeNull();
  });

  it('labels the new justification notification types', () => {
    expect(notificationTypeLabel('JUSTIFICATION_ACCEPTED')).toBe('Justificatif accepté');
    expect(notificationTypeLabel('JUSTIFICATION_REJECTED')).toBe('Justificatif refusé');
  });

  it('never links an unknown resource type', () => {
    expect(notificationLink({ resourceType: 'SOMETHING_ELSE', resourcePublicId: 'x' }, ['STUDENT'])).toBeNull();
  });
});
