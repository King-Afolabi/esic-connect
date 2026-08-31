# Sécurité et RGPD — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Version | 1.0 |
| Date | 28 août 2026 |
| Classification | Interne |
| Statut | Politique de conception à valider |

---

# 1. Objectif

Ce document définit les principes de sécurité et de protection des
données applicables à ESIC Connect.

Il couvre :

- les identités ;
- les autorisations ;
- les sessions ;
- les API ;
- les fichiers ;
- les imports ;
- les journaux ;
- Redis ;
- MySQL ;
- l’IA ;
- l’IoT ;
- les environnements ;
- les durées de conservation ;
- les droits des personnes ;
- les incidents.

La plateforme doit appliquer la sécurité et la protection des données
dès la conception.

---

# 2. Données traitées

## Données d’identité

- nom ;
- prénom ;
- email ;
- numéro étudiant ;
- téléphone facultatif ;
- date de naissance facultative.

## Données pédagogiques

- formation ;
- promotion ;
- classe ;
- année scolaire ;
- rythme d’alternance ;
- planning ;
- séances ;
- formateurs.

## Données d’assiduité

- points de contrôle ;
- présence ;
- retard ;
- absence ;
- absence excusée ;
- correction ;
- canal d’émargement.

## Données de sécurité

- événements d’authentification ;
- appareil de confiance ;
- credential WebAuthn ;
- audit ;
- identifiants de session ;
- anomalies.

## Documents

- justificatifs ;
- pièces jointes des réclamations.

## Données exclues

ESIC Connect ne doit pas stocker :

- l’empreinte digitale ;
- le modèle facial ;
- le code PIN du téléphone ;
- le mot de passe en clair ;
- les secrets dans les journaux.

---

# 3. Finalités

| Traitement | Finalité |
|---|---|
| Comptes | Donner accès à la plateforme |
| Planning | Organiser les cours |
| Présences | Suivre l’assiduité |
| Justificatifs | Qualifier une absence |
| Réclamations | Traiter les contestations |
| Audit | Garantir la traçabilité et la sécurité |
| IA d’import | Faciliter la normalisation |
| IoT | Proposer un canal d’émargement |
| Rapports | Produire des indicateurs et attestations |

---

# 4. Minimisation

Chaque donnée doit être :

- nécessaire ;
- justifiée ;
- limitée à sa finalité ;
- accessible aux seuls acteurs autorisés ;
- supprimée ou anonymisée à échéance.

La CNIL recommande de documenter les données nécessaires et d’associer
une durée de conservation à chaque catégorie. ([cnil.fr](https://www.cnil.fr/fr/minimiser-les-donnees-collectees?utm_source=openai))

---

# 5. Authentification

## Mots de passe

- Argon2id ou BCrypt ;
- longueur minimale ;
- blocage des mots de passe courants ;
- aucun stockage en clair ;
- aucun mot de passe dans les logs.

## MFA

Obligatoire pour :

- super administrateur ;
- administrateur ;
- opérations sensibles des responsables.

Adaptatif pour les apprenants :

- première connexion ;
- nouvel appareil ;
- récupération ;
- changement inhabituel ;
- modification de la sécurité.

## WebAuthn

- clé publique stockée côté serveur ;
- biométrie conservée uniquement par le terminal ;
- solution de secours ;
- révocation d’un appareil ;
- réauthentification pour les actions critiques.

## Anti-brute-force

- compteurs Redis ;
- ralentissement progressif ;
- challenge anti-bot ;
- verrouillage temporaire ;
- notification de connexion inhabituelle.

### État d’implémentation (checkpoint F5 — 31 août 2026)

**`NOT_IMPLEMENTED` — dette assumée.** Il n’y a **aucune** limitation de
débit sur `POST /api/v1/auth/login` ni sur les autres endpoints
sensibles (réémission d’invitation, activation). Redis est présent mais
n’est utilisé que pour les jetons d’émargement.

Cette lacune a été **évaluée** au checkpoint F5 et **volontairement
laissée en dette** : un limiteur correct exige un comportement *fail-safe*
explicite en cas d’indisponibilité de Redis, des clés qui ne contiennent
pas l’adresse e-mail en clair, un TTL borné, une réponse strictement
uniforme (pas d’énumération de comptes), et une couverture de test
sérieuse (dépassement, expiration, Redis KO) — un volume que ce lot de
finalisation ne peut pas traiter sans risque pour les ~30 tests
d’authentification existants. Mieux vaut l’absence claire qu’un
pseudo-contrôle fragile.

Le refus est déjà **uniforme** (même réponse pour e-mail inconnu / mauvais
mot de passe / compte inactif, testé) et le hachage BCrypt ralentit
intrinsèquement les tentatives. À implémenter pour une mise en service :
filtre de rate-limit Redis (fenêtre fixe ou *token bucket*) sur
`/auth/login`, avec les garanties ci-dessus, + Turnstile sur les
formulaires publics.

---

# 6. Sessions

## Stratégie

- access token court ;
- cookie `HttpOnly` ;
- attribut `Secure` ;
- `SameSite` adapté ;
- refresh token rotatif ;
- révocation ;
- CSRF adapté au cookie.

## Expiration

- 30 minutes d’inactivité ;
- durée absolue configurable ;
- révocation après changement de mot de passe ;
- révocation après suspension ;
- révocation à la déconnexion.

## Interdiction

Aucun token sensible dans :

```text
localStorage
```

---

# 7. Autorisations

## Modèle

- RBAC pour les rôles ;
- contrôle du périmètre ;
- contrôle de propriété ;
- contrôle contextuel des séances.

## Principes

- refus par défaut ;
- contrôle serveur ;
- moindre privilège ;
- aucune confiance dans l’interface ;
- vérification sur chaque ressource ;
- tests `401`, `403` et IDOR.

## Cas critiques

- un responsable ne lit que ses formations ;
- un enseignant ne gère que ses séances ;
- un apprenant ne lit que son dossier ;
- une administration ne lit que les données autorisées ;
- un remplaçant ne reçoit que les droits de ses séances.

---

# 8. Sécurité des API

- HTTPS obligatoire hors local ;
- validation Jakarta ;
- requêtes paramétrées ;
- erreurs neutres ;
- pagination ;
- limitation des débits ;
- CORS restrictif ;
- CSP ;
- taille des corps limitée ;
- type de contenu contrôlé ;
- OpenAPI sans secret.

La CNIL recommande TLS 1.2 ou 1.3, la limitation des ports et des comptes
de base nominatifs ou spécifiques à l’application. ([cnil.fr](https://www.cnil.fr/fr/securiser-vos-sites-web-vos-applications-et-vos-serveurs?utm_source=openai))

## État d’implémentation des contrôles API (checkpoint F5 — 31 août 2026)

| Contrôle | État | Détail |
|---|---|---|
| Validation Jakarta, requêtes paramétrées (JPA), erreurs neutres, pagination, taille des corps bornée | `IMPLEMENTED_AND_TESTED` | `GlobalExceptionHandler`, `@Valid`, `spring.servlet.multipart` (2 MiB) |
| **CORS restrictif** | `IMPLEMENTED_AND_TESTED` (F5) | `SecurityConfig.corsConfigurationSource` piloté par `app.security.cors.allowed-origins` (= `APP_ALLOWED_ORIGINS`) ; **jamais `*`** ; `allowCredentials=false` (jeton dans l’en-tête, pas de cookie) ; méthodes `GET/POST/PUT/PATCH/DELETE/OPTIONS` ; en-têtes `Authorization`, `Content-Type`, `Accept`, `X-Requested-With` ; appliqué à `/api/**`. Test : `HttpSecurityHeadersIntegrationTests` (origine listée acceptée, sinon `403`). |
| **`Content-Security-Policy`** | `IMPLEMENTED_AND_TESTED` (F5) | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`. **Aucun `script-src 'unsafe-inline'` ni `'unsafe-eval'`.** `style-src 'unsafe-inline'` et `img-src data:` sont **nécessaires à Swagger UI** (springdoc) — écart assumé et limité à ce besoin ; l’app Angular est servie séparément et applique sa propre politique côté serveur web. |
| **`Referrer-Policy`** | `IMPLEMENTED_AND_TESTED` (F5) | `no-referrer`. |
| En-têtes par défaut Spring Security (`nosniff`, `X-Frame-Options: DENY`, anti-cache, HSTS sur HTTPS) | `IMPLEMENTED_AND_TESTED` | conservés (jamais désactivés) ; HSTS émis uniquement sur réponses HTTPS — non exigé sur HTTP local. |
| Limitation de débit / rate-limiting | `NOT_IMPLEMENTED` | dette assumée — voir §5 « Anti-brute-force ». |
| HTTPS hors local | `NOT_IMPLEMENTED` | pas de terminaison TLS dans le prototype (cible `docs/03` §37). |
| OpenAPI sans secret | `IMPLEMENTED` | DTO sans `id` SQL / hash / jeton ; export runtime `scripts/dump-openapi.sh`. |

## Chaîne d’approvisionnement des dépendances (checkpoint F4 — 31 août 2026)

Contrôles réellement en place :

| Contrôle | Portée | Fichier |
|---|---|---|
| **Dependabot** | montées de version + alertes de sécurité pour **Maven** (`/backend`), **npm** (`/frontend`) et **GitHub Actions** (`/`), cadence hebdomadaire, PR ouvertes plafonnées | `.github/dependabot.yml` |
| **`actions/dependency-review-action@v4`** | sur chaque PR vers `main` : échec si une dépendance **ajoutée / modifiée** par la PR (Maven **ou** npm) introduit une vulnérabilité connue de sévérité ≥ `high`, ou une licence de la liste noire (`GPL-2.0`, `GPL-3.0`, `AGPL-3.0`) | `.github/workflows/dependency-review.yml` |
| **`npm audit --audit-level=high`** | front-end : échec de la CI si une dépendance npm (dev ou prod) porte une vulnérabilité connue ≥ `high` | `.github/workflows/frontend-ci.yml` |

Sécurité des workflows : tous en `permissions: contents: read`, avec
`concurrency` + annulation, `timeout-minutes`, actions officielles
épinglées sur une version majeure, **aucun secret**, **aucun
`pull_request_target`**, **aucune exécution de code d’une PR non fiable
avec des droits élevés**.

**Écart assumé** : il n’y a **pas** de scan SCA de fond de *tout* l’arbre
Maven (type OWASP Dependency-Check). Un tel scan exige aujourd’hui une
clé d’API NVD et le téléchargement / la mise en cache d’une base CVE
volumineuse ; sans stratégie de cache et de clé, le job serait
fréquemment rouge pour des raisons d’indisponibilité réseau, ce qui
nuirait à la CI. Le différentiel de PR (`dependency-review-action`,
qui couvre Maven) + Dependabot (alertes de sécurité sur tout l’arbre)
couvrent l’essentiel du risque pour un prototype. À planifier pour une
mise en service réelle : `org.owasp:dependency-check-maven` en job
planifié dédié, avec clé NVD en secret et cache de la base.

---

# 9. Sécurité des fichiers

## Formats

- PDF ;
- JPEG ;
- PNG.

## Taille

```text
5 Mo maximum
```

## Contrôles

- extension ;
- MIME ;
- signature réelle ;
- taille ;
- nom généré ;
- antivirus ;
- stockage hors répertoire public ;
- téléchargement par API ;
- contrôle du propriétaire ;
- empreinte SHA-256.

## Interdictions

- exécution ;
- chemin fourni par l’utilisateur ;
- URL publique permanente ;
- inclusion directe du nom original dans le chemin.

---

# 10. Sécurité des imports

- limite de taille ;
- limite du nombre de lignes ;
- analyse sans exécution de macro ;
- rejet des fichiers chiffrés non prévus ;
- simulation avant application ;
- validation du périmètre ;
- transaction de confirmation ;
- audit ;
- nettoyage des données temporaires.

L’import Excel ne doit jamais exécuter de formule ou de macro.

---

# 11. Sécurité du QR

## QR fixe

- référence de salle uniquement ;
- aucune donnée personnelle ;
- réseau ESIC requis ;
- fenêtre temporelle ;
- séance active recherchée côté serveur.

## QR dynamique

- valeur aléatoire ;
- courte durée ;
- renouvellement ;
- point de contrôle ;
- séance ;
- usage contrôlé ;
- protection contre le rejeu ;
- stockage Redis.

## Validation

Le serveur vérifie :

- jeton ;
- expiration ;
- séance ;
- inscription ;
- autorisation ;
- unicité ;
- canal ;
- risque.

---

# 12. MySQL

- compte applicatif dédié ;
- aucun accès root depuis Spring Boot ;
- réseau privé en staging ;
- contraintes ;
- migrations Flyway ;
- sauvegardes ;
- requêtes paramétrées ;
- chiffrement du transport en cible ;
- permissions minimales.

## Suppression

- `RESTRICT` par défaut ;
- aucun `ON DELETE CASCADE` métier ;
- archivage ;
- anonymisation ;
- purge contrôlée.

---

# 13. Redis

Redis contient uniquement :

- jetons temporaires ;
- cache ;
- sessions ;
- rate limiting ;
- révocations ;
- compteurs.

## Sécurité

- accès réseau privé ;
- mot de passe ou ACL ;
- aucune exposition Internet ;
- clés contextualisées ;
- TTL ;
- aucune donnée durable uniquement dans Redis ;
- aucun secret en clair lorsque cela peut être évité.

---

# 14. Audit et journalisation

## Événements audités

- connexions ;
- échecs ;
- rôles ;
- imports ;
- publications ;
- séances ;
- présences ;
- corrections ;
- justificatifs ;
- réclamations ;
- exports ;
- sécurité ;
- IoT ;
- actions administratives.

## Contenu interdit

- mots de passe ;
- tokens complets ;
- secrets ;
- biométrie ;
- justificatifs complets ;
- IP dans l’audit métier.

## Conservation

La CNIL recommande, pour les traces de sécurité, une période glissante
souvent comprise entre six mois et un an, sauf besoin légal, contentieux
ou post-incident justifié. ([cnil.fr](https://www.cnil.fr/sites/default/files/2026-05/cnil_guide_securite_personnelle.pdf?utm_source=openai))

Proposition :

| Donnée | Durée initiale |
|---|---|
| Logs techniques ordinaires | 6 mois |
| Logs de sécurité | 12 mois |
| Audit métier | 5 ans à valider |
| Présences | 5 années scolaires à valider |
| Justificatifs | 12 mois |
| Imports temporaires | 30 à 90 jours |
| Sessions révoquées | Jusqu’à expiration + marge |
| Agrégats anonymes | Selon utilité documentée |

Les durées doivent être validées avec le référent RGPD et les obligations
de l’établissement. Le RGPD impose une conservation limitée selon la
finalité, pas une durée universelle. ([cnil.fr](https://www.cnil.fr/fr/passer-laction/les-durees-de-conservation-des-donnees?utm_source=openai))

## État d’implémentation de la purge (checkpoint F3 — 31 août 2026)

Le tableau ci-dessus est une **cible**. Dans le code réellement fusionné
sur `main`, **une seule purge automatique est implémentée**. Le reste est
`DOCUMENTATION_ONLY` : il n’y a **pas** de tâche de purge, et il ne faut
donc **pas** présenter le système comme « conforme RGPD » sur la
limitation de conservation.

| Donnée | Purge réelle ? | Détail |
|---|---|---|
| **Jobs d’import CSV (`student_import_*`)** | **OUI — implémentée et testée** | `StudentImportPurgeService` (`@Scheduled`, `app.import.student.purge-cron`, défaut 03:30) : jobs `SIMULATED` / `EXPIRED` échus + `CANCELLED` anciens supprimés en cascade ; jobs `APPLIED` anciens → lignes filles supprimées, en-tête et agrégats conservés ; `student_number_sequence` jamais purgée. Tests : `StudentImportPurgeTests`. |
| **Fichier importé** | **N/A — jamais persisté** | le contenu CSV n’est jamais écrit sur disque ; seule l’empreinte SHA-256 est conservée (`CsvFileGuard`). |
| Jetons / codes d’émargement (Redis) | **OUI — par TTL** | expiration Redis (`app.attendance.token-ttl`, défaut `PT30S`) + invalidation explicite à la fermeture de séance / du point de contrôle. Aucune persistance MySQL. |
| Jetons d’invitation (`account_invitation`) | **NON** | TTL métier vérifié à l’usage, mais **aucune purge** des lignes `PENDING` échues (dette connue — cf. `docs/CURRENT-STATE.md`). |
| Piste d’audit (`audit_event`) | **NON** | append-only, **aucune** rétention / archivage / anonymisation outillé. |
| Présences, corrections, justificatifs | **NON** | aucune purge ni anonymisation ; conservation de fait illimitée en base. |
| Comptes archivés | **NON** | statut `ARCHIVED` (pas de connexion), historique conservé ; **pas** de séparation en archivage intermédiaire ni d’anonymisation. |
| Logs techniques du serveur | **NON géré ici** | dépend de la configuration d’exploitation (rotation logback / plateforme). |

Conséquence : les exigences `docs/07` §14 (conservation limitée), §18
(droits des personnes) et §39 du cahier des charges sont **partiellement
couvertes** — une seule purge outillée. Pour une mise en service réelle,
il faut : (1) une tâche `@Scheduled` de purge des invitations `PENDING`
échues, (2) une politique de rétention outillée pour l’audit et les
présences (archivage → purge / anonymisation), (3) des procédures pour
les droits d’accès / rectification / effacement / export.

---

# 15. Protection de l’IA

- données synthétiques pour le prototype ;
- aucune donnée réelle envoyée à un service public non approuvé ;
- Spring Boot filtre les données ;
- score de confiance ;
- validation humaine ;
- traçabilité ;
- aucun refus automatique ;
- aucune sanction automatique ;
- mode manuel disponible.

---

# 16. Sécurité IoT

- identité unique ;
- credential par appareil ;
- secret hors du code ;
- TLS en cible ;
- `eventId` unique ;
- numéro de séquence ;
- horodatage ;
- liste d’autorisation ;
- révocation ;
- file locale ;
- validation Spring Boot ;
- données pseudonymisées.

La borne ne doit pas pouvoir écrire directement dans MySQL.

---

# 17. Environnements

## Local

- données fictives ;
- secrets locaux ;
- ports de développement ;
- Mailpit.

## Test

- base temporaire ;
- données générées ;
- secrets jetables ;
- aucune dépendance à la production.

## Staging

- données fictives ;
- HTTPS ;
- base distincte ;
- secrets distincts ;
- accès limité ;
- domaine séparé.

## Production

- segmentation réseau ;
- WAF ;
- sauvegardes ;
- supervision ;
- secrets gérés ;
- principe du moindre privilège.

---

# 18. Droits des personnes

Prévoir les procédures pour :

- accès ;
- rectification ;
- export ;
- limitation ;
- suppression lorsque applicable ;
- contestation d’une présence ;
- information sur les traitements ;
- information sur l’IA ;
- retrait ou révocation d’un appareil.

---

# 19. Gestion des incidents

## Étapes

```text
Détection
→ Qualification
→ Confinement
→ Éradication
→ Restauration
→ Communication
→ Retour d’expérience
```

## Niveaux

| Niveau | Exemple |
|---|---|
| Faible | Erreur sans donnée exposée |
| Moyen | Compte compromis isolé |
| Élevé | Accès non autorisé à plusieurs dossiers |
| Critique | Fuite massive ou indisponibilité majeure |

## Preuves

- journaux ;
- identifiant de corrélation ;
- horodatage ;
- décisions ;
- responsables ;
- actions correctives.

---

# 20. Contrôles avant mise en service

- secrets absents de Git ;
- dépendances analysées ;
- tests d’autorisation ;
- HTTPS ;
- CORS ;
- sauvegarde testée ;
- restauration testée ;
- comptes par défaut supprimés ;
- données de démonstration absentes ;
- logs sans secrets ;
- politique de conservation active ;
- scan de vulnérabilités ;
- documentation à jour.

---

# 21. Référentiel de vérification

Le plan de tests de sécurité doit s’inspirer :

- d’OWASP ASVS ;
- des bonnes pratiques OWASP API ;
- des recommandations CNIL ;
- des exigences métier du cahier des charges.

Les mesures de sécurité doivent être proportionnées aux risques et
documentées, ce qui contribue directement aux blocs 2, 3 et 4 du RNCP 39394.