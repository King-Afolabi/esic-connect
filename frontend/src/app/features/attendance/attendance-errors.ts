/**
 * Traduction d'une erreur HTTP de la gestion de l'assiduité (V10) en
 * éléments d'affichage. Réutilise {@link toSessionError} de l'espace
 * Séances : sa liste blanche de codes couvre déjà tous les codes
 * `SESSION_*` / `ATT_*` de cette tranche (points de contrôle, présence
 * manuelle, correction, justificatifs, rapports). Un code inconnu ou un
 * `5xx` retombe sur le message générique — le message brut du serveur
 * n'est jamais affiché.
 */
export { toSessionError as toAttendanceError, type SessionErrorView as AttendanceErrorView } from '../sessions/session-errors';
