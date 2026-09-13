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

  it('links a CLAIM notification to /claims/:publicId, open to any role (no route guard)', () => {
    const n = { resourceType: 'CLAIM', resourcePublicId: '11111111-1111-4111-8111-111111111111' };
    expect(notificationLink(n, ['STUDENT'])).toEqual({
      commands: ['/claims', '11111111-1111-4111-8111-111111111111'],
      label: 'Voir la réclamation',
    });
    expect(notificationLink(n, ['TEACHER'])).toEqual({
      commands: ['/claims', '11111111-1111-4111-8111-111111111111'],
      label: 'Voir la réclamation',
    });
    expect(notificationLink(n, [])).toEqual({
      commands: ['/claims', '11111111-1111-4111-8111-111111111111'],
      label: 'Voir la réclamation',
    });
  });

  it('never links a CLAIM notification without a valid publicId', () => {
    expect(notificationLink({ resourceType: 'CLAIM', resourcePublicId: '' }, ['STUDENT'])).toBeNull();
    expect(notificationLink({ resourceType: 'CLAIM', resourcePublicId: 'not-a-uuid' }, ['STUDENT'])).toBeNull();
  });

  it('labels the CLAIM notification types in French', () => {
    expect(notificationTypeLabel('CLAIM_OPENED')).toBe('Réclamation ouverte');
    expect(notificationTypeLabel('CLAIM_MESSAGE_ADDED')).toBe('Nouveau message sur une réclamation');
    expect(notificationTypeLabel('CLAIM_STATUS_CHANGED')).toBe('Statut de réclamation modifié');
  });
});
