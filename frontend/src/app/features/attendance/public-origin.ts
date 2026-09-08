import { environment } from '../../../environments/environment';

/** `publicBaseUrl` configuré, s'il est une URL http(s) absolue. */
function configuredOrigin(): string | null {
  const raw = (environment as { publicBaseUrl?: string }).publicBaseUrl?.trim();
  return raw && /^https?:\/\//i.test(raw) ? raw.replace(/\/+$/, '') : null;
}

/**
 * Origine publique de confiance pour construire une URL absolue :
 * l'origine configurée si elle existe, sinon celle d'où l'application est
 * servie. Jamais une origine reçue d'un en-tête ou d'un paramètre client.
 */
export function publicOrigin(): string {
  return (
    configuredOrigin() ??
    (typeof window !== 'undefined' && window.location?.origin ? window.location.origin : '')
  );
}

/**
 * Origines considérées comme **internes** par le parseur de références
 * d'émargement : celle du navigateur et l'origine publique configurée.
 * Une URL scannée hors de cette liste est rejetée.
 */
export function allowedInternalOrigins(): string[] {
  const origins = new Set<string>();
  if (typeof window !== 'undefined' && window.location?.origin) {
    origins.add(window.location.origin);
  }
  const configured = configuredOrigin();
  if (configured) {
    origins.add(configured);
  }
  return [...origins];
}
