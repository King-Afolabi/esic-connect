/*
 * Service worker d'ESIC Connect (EF-PWA-001, EF-PWA-002, EF-NOTIF-005).
 *
 * POURQUOI UN SERVICE WORKER ÉCRIT À LA MAIN plutôt que
 * `@angular/service-worker`. Le cahier demande une file d'actions
 * différées avec résolution de conflit au retour du réseau (§29.2,
 * EF-PWA-003). Le service worker d'Angular sait mettre en cache une
 * coquille applicative, mais il ne sait pas rejouer une action métier :
 * il aurait fallu l'accompagner d'un second mécanisme, alors qu'un seul
 * service worker peut être enregistré par portée. Écrire celui-ci permet
 * en outre de recevoir les notifications poussées et de contrôler
 * précisément ce qui est conservé sur l'appareil.
 *
 * CE QUI EST MIS EN CACHE, ET CE QUI NE L'EST JAMAIS.
 *   - la coquille applicative (HTML, JS, CSS, icônes) : sans donnée ;
 *   - quelques réponses `GET` d'API strictement listées, pour la
 *     consultation hors ligne du planning et de l'assiduité récents.
 *
 * Ne sont JAMAIS mises en cache : les routes d'authentification, les
 * réponses portant un jeton d'émargement, et toute méthode autre que
 * `GET`. Le cache de données est vidé à la déconnexion, sur message de
 * l'application : un appareil partagé ne doit pas conserver les données
 * de la personne précédente.
 */

const VERSION = 'v1';
const SHELL_CACHE = `esic-shell-${VERSION}`;
const DATA_CACHE = `esic-data-${VERSION}`;

/**
 * Ressources de la coquille connues à l'avance. Les fichiers JS et CSS
 * portent une empreinte dans leur nom à chaque construction : les
 * énumérer ici serait faux dès la version suivante. Ils sont donc mis en
 * cache à l'usage (voir `handleAsset`).
 */
const SHELL_URLS = ['/', '/index.html', '/manifest.webmanifest', '/favicon.ico'];

/**
 * Préfixes d'API consultables hors ligne (§29.2 : « le planning récent,
 * la prochaine séance, l'historique d'assiduité récent et les
 * notifications déjà reçues »). Liste FERMÉE : tout ce qui n'y figure pas
 * traverse le réseau sans laisser de trace sur l'appareil.
 */
const CACHEABLE_API_PREFIXES = [
  '/api/v1/me/attendance',
  '/api/v1/me/notifications',
  '/api/v1/me/dashboard',
  '/api/v1/planning/calendar',
  '/api/v1/sessions',
];

/**
 * Jamais en cache, même en `GET` : ces réponses portent des jetons à
 * durée de vie courte ou des éléments d'authentification.
 */
const NEVER_CACHE_PREFIXES = ['/api/v1/auth', '/api/v1/sessions/attendance-token'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(SHELL_URLS))
      // Une ressource manquante ne doit pas empêcher l'installation :
      // l'application resterait alors sans service worker du tout.
      .catch(() => undefined)
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key !== SHELL_CACHE && key !== DATA_CACHE)
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

/**
 * Vidage du cache de données, demandé par l'application à la
 * déconnexion. Sans cela, les données de la personne précédente
 * resteraient lisibles hors ligne sur un appareil partagé.
 */
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'ESIC_CLEAR_DATA_CACHE') {
    event.waitUntil(caches.delete(DATA_CACHE));
  }
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') {
    return; // les écritures ne sont ni mises en cache ni rejouées ici
  }
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }
  if (request.mode === 'navigate') {
    event.respondWith(handleNavigation(request));
    return;
  }
  if (url.pathname.startsWith('/api/')) {
    if (NEVER_CACHE_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))) {
      return;
    }
    if (CACHEABLE_API_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))) {
      event.respondWith(handleApi(request));
    }
    return;
  }
  event.respondWith(handleAsset(request));
});

/**
 * Navigation : réseau d'abord, coquille en cache à défaut. L'application
 * démarre donc hors ligne — mais sans session, puisque le jeton ne vit
 * qu'en mémoire (RG-093). Elle affiche alors son écran de connexion et
 * indique que le réseau est nécessaire pour ouvrir une session.
 */
async function handleNavigation(request) {
  try {
    const response = await fetch(request);
    const cache = await caches.open(SHELL_CACHE);
    cache.put('/index.html', response.clone());
    return response;
  } catch (offline) {
    const cached = await caches.match('/index.html');
    return cached || Response.error();
  }
}

/** Ressource de coquille : cache d'abord, réseau à défaut. */
async function handleAsset(request) {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }
  const response = await fetch(request);
  if (response && response.ok) {
    const cache = await caches.open(SHELL_CACHE);
    cache.put(request, response.clone());
  }
  return response;
}

/**
 * Donnée d'API : réseau d'abord, cache à défaut.
 *
 * Une réponse servie depuis le cache porte l'en-tête
 * `X-ESIC-From-Cache` et la date de sa mise en cache. L'interface s'en
 * sert pour dire clairement « données du … », plutôt que de présenter un
 * état ancien comme s'il était courant.
 */
async function handleApi(request) {
  try {
    const response = await fetch(request);
    if (response && response.ok) {
      const cache = await caches.open(DATA_CACHE);
      const copy = new Response(response.clone().body, {
        status: response.status,
        statusText: response.statusText,
        headers: withCacheStamp(response.headers),
      });
      cache.put(request, copy);
    }
    return response;
  } catch (offline) {
    const cached = await caches.match(request, { cacheName: DATA_CACHE });
    if (cached) {
      return cached;
    }
    throw offline;
  }
}

function withCacheStamp(headers) {
  const copy = new Headers(headers);
  copy.set('X-ESIC-From-Cache', '1');
  copy.set('X-ESIC-Cached-At', new Date().toISOString());
  return copy;
}

/**
 * Notification poussée (EF-NOTIF-005). Le contenu reçu ne comporte
 * aucune donnée sensible : un titre, un corps neutre et une catégorie
 * (§29.3). Le clic ramène vers l'application, qui décide où aller — le
 * serveur ne transmet jamais de chemin d'interface (§21.5).
 */
self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch (unreadable) {
    payload = {};
  }
  const title = payload.title || 'ESIC Connect';
  event.waitUntil(
    self.registration.showNotification(title, {
      body: payload.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: payload.category || 'esic',
      data: { category: payload.category || null },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      const open = clients.find((client) => client.url.includes(self.location.origin));
      if (open) {
        return open.focus();
      }
      return self.clients.openWindow('/notifications');
    }),
  );
});
