# État courant — ESIC Connect

> **But** : donner en une lecture courte l'état **réel** du dépôt — ce qui
> est implémenté, testé, partiel ou hors périmètre — et les preuves
> associées. Ce document ne contient **pas** la chronologie des tranches :
> elle est archivée dans
> [`docs/reports/PROJECT_HISTORY.md`](reports/PROJECT_HISTORY.md).
>
> **Sources de vérité complémentaires** :
> - [`docs/reports/G1_FINAL_REPORT.md`](reports/G1_FINAL_REPORT.md) —
>   rapport final du grand lot produit G1 (anomalies corrigées, garanties
>   transactionnelles, coûts SQL mesurés, dettes) ;
> - [`docs/reports/G1_REQUIREMENTS_TRACEABILITY.md`](reports/G1_REQUIREMENTS_TRACEABILITY.md)
>   — matrice EF-* / RG-* / AC-* du lot G1 ;
> - [`docs/reports/PROJECT_FINAL_AUDIT.md`](reports/PROJECT_FINAL_AUDIT.md)
>   — audit vérifiable de la finalisation F1 (**antérieur à G1** :
>   ses totaux « 12 modules / V11 / 682 tests » sont **périmés**) ;
> - `git log` — le code et les tests font foi.

## Dernière mise à jour

```text
2 septembre 2026 — audit documentaire de clôture (aucun code modifié)
```

## Point de référence Git

| Élément | Valeur observée |
|---|---|
| Branche de travail | `docs/g1-manual-demonstration` |
| HEAD | `d3450e6` — `feat(g1): livrer la montée en gamme fonctionnelle du Groupe 1 (#40)` |
| `main` | `d3450e6` (identique — G1 fusionné par la PR #40) |
| Contenu vs `feature/master-level-product-expansion` | **identique** (`git diff` vide) |
| Working tree | une seule modification locale : `frontend/angular.json` (identifiant d'analytics ajouté par la CLI Angular sur le poste — **non commité**, sans effet fonctionnel) |

## Modules Spring Modulith réels (14)

14 `package-info.java` sous `com.esic.connect.*` ; `ModularityTests`
(Spring Modulith 1.4) **vert** : aucune dépendance vers un package
`.internal` d'un autre module, aucun cycle.

| Module | Rôle | Migration(s) |
|---|---|---|
| `identity` | comptes, rôles, authentification JWT, invitation / activation, administration des comptes | V1, V2, V3 |
| `organization` | site / bâtiment / salle / plage réseau CIDR | V4 |
| `academic` | année scolaire, formation, niveau, promotion, classe, affectation pédagogique + contrôle de périmètre | V5, V6 |
| `enrollment` | profil apprenant, inscription, changement de classe historisé | V7 |
| `alternation` | modèles de rythme, affectation historisée, exceptions individuelles, résolution `SCHOOL`/`COMPANY`/`UNKNOWN` | V8 |
| `planning` | import CSV → simulation (0 séance, AC-007) → publication atomique versionnée (N/N+1, `SUPERSEDED`, AC-008) → séances via le port `coursesession.PlanningSessionWriter` ; conflits formateur / classe / salle | V12, V13 |
| `coursesession` | séance manuelle **ou** issue d'un planning publié ; cycle `PLANNED → OPEN → CLOSED` / `CANCELLED` ; points de contrôle ; remplacements de formateur | V9, V10, V13, V14 |
| `attendance` | jeton d'émargement (Redis), validation, retard, présence manuelle / correction, justificatif **+ pièces jointes**, rapports + export CSV | V9, V10, V16 |
| `studentimport` | import CSV contrôlé des apprenants (lecture sécurisée, simulation, confirmation transactionnelle, purge) | V11 |
| `notification` | email d'activation (Mailpit) **+ centre de notifications métier persistantes** (`AFTER_COMMIT`, idempotence `dedup_key`) | V15 |
| `dashboard` | `GET /api/v1/me/dashboard` par rôle (lecture seule, agrégats bornés, contexte multi-rôle vérifié côté serveur) | — |
| `audit` | piste d'audit `audit_event` alimentée par les événements métier | V1 |
| `bootstrap` | amorçage `demo` (5 comptes fictifs, profil `demo` uniquement) | — |
| `shared` | types transverses, `BaseEntity`, `ApiError`, `GlobalExceptionHandler`, `ClockConfig` | — |

Modules décrits dans `docs/03-architecture.md` §7 comme **architecture
cible non implémentée** : `room` (remplacé par `organization`),
`justification` (fusionné dans `attendance`), `claim`, `reporting`
(fusionné dans `attendance`), `ai`, `iot`.

## Migrations Flyway réelles — schéma en **V16**

```text
V1  identité + audit          V9  séances + émargement
V2  seed des 6 rôles          V10 gestion d'assiduité + reporting
V3  invitations               V11 import CSV apprenants
V4  organisation              V12 module planning (7 tables)          [G1-B]
V5  référentiel académique    V13 lien course_session ↔ créneau       [G1-B]
V6  affectations pédagogiques V14 cycle de vie séances (CANCELLED,    [G1-C]
V7  profils + inscriptions        teacher_substitution)
V8  alternance                V15 table notification                  [G1-D]
                              V16 justification_attachment            [G1-E]
```

41 tables métier, `spring.jpa.hibernate.ddl-auto = validate`, aucune
donnée métier insérée par une migration. V12/V13 ont été corrigées **en
place** à l'audit G1-B.1 (jamais poussées) : une base ayant appliqué
l'ancienne forme **ne se répare pas** par un simple `flyway repair`
(recréation ou migration corrective explicite — voir l'en-tête de `V13`).

## Fonctionnalités livrées (`IMPLEMENTED_AND_TESTED`)

Sauf mention contraire, « testé » = tests automatisés passants
(`./mvnw clean test` / `npm test`). **Aucune démonstration manuelle n'est
enregistrée dans le dépôt.**

### Identité / accès
- Connexion email + mot de passe → JWT HS256 stateless (signature +
  `exp` + `iss` vérifiés, `401` nu). Réponse uniforme pour email
  inconnu / mauvais mot de passe / compte inactif.
- Multi-rôles ; autorités `ROLE_*` dans le JWT ; `@EnableMethodSecurity`
  + `@PreAuthorize` sur toutes les routes non publiques.
- Invitation + activation de compte (jeton `SecureRandom`, empreinte
  SHA-256 seule stockée, TTL configurable, usage unique).
- Administration des comptes : suspension / réactivation / archivage /
  attribution / retrait de rôle, gardes fines côté serveur (protection
  `SUPER_ADMIN`, auto-action interdite, dernier rôle actif protégé).
  Front `/administration` en lecture **et** écriture.
- Sélecteur de contexte de rôle côté front, **transmis au serveur** pour
  le tableau de bord et vérifié contre les autorités du JWT
  (`403 DASHBOARD_CONTEXT_NOT_HELD` si le rôle n'est pas détenu) ; le
  cumul de rôles n'élargit **jamais** le JWT.

### Référentiels
- `organization` : CRUD + archivage / restauration site / bâtiment /
  salle, plages réseau CIDR IPv4/IPv6 validées (sans DNS). **Écrans
  Angular livrés** (`/organization/sites…`, G1-A).
- `academic` : CRUD + archivage année / formation / niveau / promotion /
  classe ; affectation pédagogique + `AcademicScopeGuard` (périmètre RP
  décidé côté serveur). Front `/academic` en **lecture seule**.
- `enrollment` : profil apprenant, inscription, changement de classe
  conservant l'historique ; une seule inscription active par apprenant et
  par année (contrainte SQL + isolation de la concurrence). Front
  `/students` en **lecture seule**.
- `alternation` : 4 types de rythme, `configuration_json` validé et
  canonicalisé ; affectation historisée ; exceptions individuelles ;
  résolution `SCHOOL`/`COMPANY`/`UNKNOWN`. Front `/alternation` en R/W.

### Planning (G1-B)
- Import CSV borné, **jamais écrit sur disque** (SHA-256 seul) →
  **simulation** produisant lignes, anomalies et synthèse **sans créer
  aucune séance** (invariant T1, AC-007).
- Détection de conflits formateur / classe / salle et hors plage horaire
  **intra-fichier**, plus conflits formateur / classe contre les séances
  **déjà publiées**.
- **Publication atomique** : verrou `FOR UPDATE`, re-validation, version
  N/N+1 (ancienne `SUPERSEDED`, AC-008), séances créées / réutilisées /
  supersédées via le **port public** `coursesession.PlanningSessionWriter`
  (aucune entité JPA partagée entre modules). Publication concurrente
  **strictement idempotente** (le perdant renvoie `alreadyPublished=true`).
- Identité de créneau **stable et déterministe** :
  `course_session.planning_slot_public_id`.
- Écrans `/planning/import`, `/planning/import/:jobId`,
  `/planning/versions`.

### Séances & émargement
- Séance créée manuellement (motif obligatoire) **ou** issue d'un
  planning publié ; cycle strict `PLANNED → OPEN → CLOSED`, pas de
  réouverture ; `PLANNED`/`OPEN → CANCELLED` avec motif ; remplacements
  de formateur datés. Pas de `PATCH`.
- Une séance supersédée est **inactive partout** (garde centralisée
  `CourseSession.isOperational()`) ; une séance `CANCELLED` reste
  **consultable** en historique (`isHistoricallyReadable()`).
- Remplacement : formateur principal **jamais écrasé** ; une seule
  substitution `ACTIVE` applicable ; le remplaçant obtient `MANAGE`
  seulement pendant sa période ; `TEACHER` exclu de la création (« ne
  valide pas lui-même son remplacement »).
- Points de contrôle multiples (`START` / `END` / `CUSTOM`), transitions
  concurrentes → `409`, jamais `500`.
- Jeton d'émargement **opaque** + **code court** dans Redis (TTL,
  rotation, purge à la fermeture **après commit**) ; le QR n'encode que
  le jeton opaque, aucune donnée personnelle. Redis indisponible →
  `503 ATT_TOKEN_BACKEND_UNAVAILABLE`, jamais de validation dégradée.
- Validation par un `STUDENT` inscrit ; anti-double présence par
  contrainte SQL (concurrence → `200` / `409` / `0×500`).
- Classement `PRESENT` / `LATE` (seuil unique `PT10M`).
- Présence manuelle / correction / annulation logique, motif obligatoire,
  historique append-only, verrou optimiste → `409`.

### Assiduité / reporting / justificatifs
- Justificatif : dépôt / modification tant que `PENDING` / examen ;
  `ACCEPTED` → `ABSENT → EXCUSED_ABSENCE` ; `TEACHER` exclu de l'examen.
- **Pièce jointe (G1-E)** : dépôt multipart propriétaire, validation
  extension + type déclaré + **magic bytes** (type re-dérivé du contenu,
  rejet ZIP/OLE2), contenu **hors base et hors webroot**, séquence
  base↔fichier avec **compensation**, réconciliation `@Scheduled` des
  lignes `PENDING_STORAGE`, téléchargement `Content-Disposition:
  attachment` + `nosniff` (propriétaire **et** examinateur périmétré ;
  hors périmètre → `404`), notification `AFTER_COMMIT` du propriétaire à
  l'examen.
- Espace apprenant `/me/attendance*` : absences **dérivées** d'un point
  de contrôle fermé, jamais persistées ; aucun accès croisé (AC-017).
- Calcul de demi-journées : contexte d'alternance `COMPANY` exclu du
  dénominateur, `UNKNOWN` non satisfait compté à part.
- Rapports séance / classe / apprenant / synthèse (JSON paginé, tri
  serveur borné → `400 ATT_REPORT_INVALID_SORT`) ; export CSV (UTF-8 +
  BOM, `;`, neutralisation d'injection de formule).
- Front `/attendance-management` (4 sous-rapports + file des
  justificatifs), `/my-attendance`.

### Import CSV des apprenants
- Lecture sécurisée : extension `.csv`, rejet ZIP/OLE2/PDF/octet nul,
  UTF-8 strict, RFC 4180 maison, séparateur `,`/`;` auto-détecté ;
  fichier **jamais écrit sur disque** ; `2 MiB` max →
  `413 IMP_FILE_TOO_LARGE`.
- **Simulation** sans aucune écriture métier (T1).
- **Confirmation** transactionnelle unique : verrou `SELECT … FOR
  UPDATE`, re-validation complète, idempotence `APPLIED`, rollback total
  sur toute exception (T3), e-mail seulement `AFTER_COMMIT` (T4),
  numéro `ESIC-{annéeDébut}-{NNNNN}` alloué atomiquement.
- Audit `AFTER_COMMIT` + `REQUIRES_NEW` (aucune trace si rollback, T5) ;
  purge `@Scheduled`. Front `/students/import` (R/W).

### Notifications (EF-NOTIF-001)
- Centre in-app **persistant** : planning publié / séance annulée /
  remplaçant affecté / remplacement terminé → notifications produites
  `AFTER_COMMIT` (rollback métier ⇒ **0** notification), **idempotentes**
  (`dedup_key` SHA-256), **isolées par destinataire** (notification
  d'autrui → `404`, pas `403`).
- Frontière par destinataire durcie : l'échec d'un destinataire
  n'interrompt pas les autres.
- API `/api/v1/me/notifications` (liste paginée, `unread-count`,
  `{id}/read`, `read-all`), cloche `mat-badge` + centre Angular ; liens
  en **liste blanche par rôle** (aucun `targetPath` serveur).

### Transverse
- Audit `audit_event` alimenté par tous les flux métier ; **sans PII,
  sans jeton, sans adresse IP**.
- Matrices de sécurité `*SecurityTests` (`401` / `403` / `200`) par
  module ; concurrence testée (inscriptions, affectations, émargement,
  corrections, confirmations d'import, publications de planning) ;
  invariants transactionnels T1–T6 de l'import.
- En-têtes HTTP durcis : `nosniff`, `X-Frame-Options: DENY`, anti-cache,
  **CSP**, `Referrer-Policy: no-referrer` ; **CORS restrictif** piloté par
  `APP_ALLOWED_ORIGINS`, jamais `*`, `allowCredentials=false`.
- Front Angular 21.2 zoneless / standalone / Material ; JWT et contexte
  de rôle **en mémoire seule** (aucun `localStorage` / `sessionStorage`,
  asserté par test) ; build de production sous le budget de 500 kB.

## Fonctionnalités partielles (`PARTIAL`)

| Sujet | Ce qui existe | Ce qui manque |
|---|---|---|
| Points de contrôle (EF-ATT-003) | N points de contrôle par séance (`START`/`END`/`CUSTOM`) | les 4 types nommés (`MORNING_ARRIVAL`…) et le calcul journée / demi-journée strict du cahier ne sont pas modélisés tels quels |
| Retards (EF-ATT-005) | seuil unique `PT10M` → `LATE` | paliers 15 / 30 min, validation manuelle automatique après 30 min |
| Correction de lignes de planning (EF-PLAN-003) | annulation du job + réimport (`DEC-G1-003`) | pas de correction ligne à ligne dans l'écran de revue |
| Versionnement du planning (EF-PLAN-007, RG-032..035) | versions N/N+1, `SUPERSEDED`, aucune purge | conflit **salle** contre les séances déjà publiées non détecté (`coursesession` ne porte pas `room_code`) ; pas de retour à une version antérieure |
| Alternance ↔ assiduité | contexte résolu, consommé par le reporting | pas d'avertissement d'alternance sur un créneau jour-entreprise à la publication (`DEC-G1-006`) ; « demi-journées attendues » ne croise pas systématiquement le rythme |
| Pièces jointes — **durcissement opérationnel** (le périmètre fonctionnel est `IMPLEMENTED_AND_TESTED`) | validation structurelle, stockage hors webroot, compensation, réconciliation, téléchargement forcé | **antivirus `NOT_IMPLEMENTED`** (`DEC-G1-E-ANTIVIRUS` — ne jamais écrire « garanti sans malware ») ; **balayage des fichiers orphelins `NOT_IMPLEMENTED`** (la réconciliation ne traite QUE les `PENDING_STORAGE`) ; pas de remplacement direct d'une pièce ; rétention `DELETED` **`À_DÉFINIR`** (`R-G1-30`) |
| Notifications (EF-NOTIF-002 / RG-033) | voir « livrées » ci-dessus | audience **formateur uniquement** — apprenants / responsables pédagogiques non notifiés (dette **G1-D-AUDIENCE**) ; livraison « au mieux » après commit **sans reprise** (dette **G1-D-OUTBOX**) ; pas d'email métier, de push PWA, de préférences, de purge |
| Tableau de bord par rôle (G1-F, CDC §25) — **bloc global `PARTIAL`** | endpoint typé par rôle, périmètre serveur, contexte multi-rôle vérifié ; cartes `STUDENT` et `TEACHER` `IMPLEMENTED_AND_TESTED` (formateur **avec remplaçants actifs**) | cartes `PEDAGOGICAL_MANAGER` `PARTIAL` (justificatifs périmétrés, alternance `UNKNOWN`, planning actif, conflits récents : **pas de port agrégé borné**) ; carte `ADMINISTRATION` `PARTIAL` (dernières opérations d'audit non exposées) ; coût SQL **linéaire selon le nombre de séances** (≈ 2 requêtes/séance, non regroupé) ; pas de cache Redis |
| Audit transactionnel | `coursesession` et `studentimport` publient `AFTER_COMMIT` | **8 des 9 listeners d'audit** restent des `@EventListener` synchrones `REQUIRES_NEW` ; l'échec d'audit après stockage d'une pièce est **isolé** mais **non rejoué** (pas d'outbox) |
| Écrans d'écriture (dette G1-A) | `organization`, `alternation`, `administration`, imports, séances, émargement en R/W | écritures `academic` / `enrollment`, affectation d'un responsable pédagogique, **émission** d'invitation : API livrées, **aucun écran** |
| Rapports « officiels » (docs/02 §24.5) | calcul demi-journées + export CSV | mise en page (logo, PDF, identifiant de document), export Excel |
| OpenAPI | `/v3/api-docs` + `/swagger-ui` au runtime | pas d'`openapi.json` versionné (`scripts/dump-openapi.sh` à la demande) |
| Redis | jetons d'émargement uniquement | cache de planning, rate-limiting, droits calculés |
| Actuator / supervision | `/actuator/health` (`show-details: never`) | métriques, logs structurés JSON |
| Rétention / purge | purge planifiée de l'import CSV et des jobs de planning | audit, invitations `PENDING` échues, présences, pièces jointes `DELETED` |
| Performance | mesures indicatives (`docs/reports/PERF_NOTES.md`), concurrence testée | pas de campagne de charge ; objectif « < 100 ms » non validé sur l'ensemble des routes |
| Accessibilité | structure sémantique, labels, `role="alert"`, clavier ; 2 fichiers `*.a11y.spec.ts` (`axe-core`) | audit outillé complet, test lecteur d'écran |
| EF-USER-001 | création via invitation / fixtures | pas d'endpoint `POST /users` de création `PENDING_ACTIVATION` |
| Anti-brute-force `/auth/login` | refus uniforme + BCrypt | **rate-limiting `NOT_IMPLEMENTED`** — dette assumée (`docs/07` §5) |

## Hors périmètre assumé (`HORS_PÉRIMÈTRE_ASSUMÉ`)

Décidé pour cette livraison de prototype, assumé et documenté — jamais
présenté comme livré.

- `EF-PLAN-006` — création manuelle d'un planning plein calendrier
  (la création manuelle d'une **séance exceptionnelle** existe).
- QR fixe de salle + contrôle réseau CIDR (référentiel
  `site_network_range` présent, **non consommé**) — EF-ROOM-002,
  EF-ATT-008 ; scan caméra mobile (code court uniquement).
- WebAuthn / passkeys, MFA TOTP, Cloudflare Turnstile / anti-bot.
- Réclamations / messagerie (EF-CLAIM-001/002), départ anticipé,
  import Excel `.xlsx` / multifeuille, groupes temporaires.
- Service IA (FastAPI, mapping de colonnes, score d'anomalie) —
  EF-AI-001..003 ; IoT / MQTT / Raspberry Pi (broker Mosquitto démarré
  par `compose.yaml`, **aucun code back-end**) — EF-IOT-001/002.
- PWA installable / offline / push.
- Mot de passe oublié / réinitialisation (EF-AUTH-005) ;
  `/auth/logout` + révocation de session (JWT stateless assumé).
- Export Excel (EF-REP-004), export PDF.
- Déploiement cloud AWS / staging / HTTPS / haute disponibilité ;
  sauvegarde / restauration outillée et testée.
- Tests **e2e navigateur** (Playwright / Cypress) : `NOT_IMPLEMENTED`,
  non retenus faute de rapport coût / bénéfice (`DEC-G1-011`). Repli
  livré : la recette d'intégration API.

## Résultats de tests

Mesurés sur **ce dépôt**, HEAD `d3450e6` (2 septembre 2026).
Environnement : OpenJDK 21, MySQL 8 + Redis 7 (Docker Compose local),
Node 24.13, npm 11.6.2.

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **811 tests, 0 échec, 0 erreur, 0 ignoré** — 96 classes, `ModularityTests` **vert** (14 modules), schéma **V16** |
| `cd frontend && npm test -- --watch=false` | **71 fichiers / 600 tests / 0 échec** (Vitest + jsdom) |
| `npm run lint` | « All files pass linting » |
| `npm run build` | initial **484,52 kB** brut — 0 alerte de budget |
| `npm audit --audit-level=high` | **0 vulnérabilité** |

Preuves complémentaires du lot G1 (relevées à la passe corrective,
`G1_FINAL_REPORT.md` §11) : suite back **809 → 811** verte sous les trois
fuseaux (défaut / `TZ=UTC` / `TZ=Europe/Paris`) ; Flyway `V1 → V16`
rejoué sur une base `esic_test` recréée vierge suivi de
`ddl-auto=validate` OK.

Mesures de coût SQL du tableau de bord manager (compteur Hibernate) :

| Dimension | Mesure | Conclusion |
|---|---|---|
| Nombre de **classes** | 1 classe → 14 requêtes ; 15 classes → 14 | N+1 **corrigé**, croissance **nulle** |
| Nombre de **séances** | 1 séance → 10 ; 10 séances → 28 | **linéaire ≈ 2 requêtes/séance**, non regroupé — borné *en pratique* par la fenêtre 7 jours et l'affichage à 10, **pas** en requêtes au-delà (`DEC-G1-010`) |

Les tests portant le tag JUnit `perf` sont **exclus** du run par défaut
(`./mvnw test -Pperf` pour les exécuter).

## Démonstration

| Nature | Statut |
|---|---|
| Recette d'intégration **API** du parcours prioritaire (`PriorityPathRecetteIntegrationTests`) | `IMPLEMENTED_AND_TESTED` |
| Parcours API relevé à la main (statuts HTTP, `docs/11-guide-demonstration.md` §11.8) | exécuté |
| Démonstration **UI** de bout en bout | **`NOT_PERFORMED`** — aucune manipulation consignée |
| Tests **e2e navigateur** | **`NOT_IMPLEMENTED`** |
| **Statut global du lot G1** | **`IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED / PARTIAL`** |

## Principaux risques et dettes

| Réf | Dette / risque | Effet |
|---|---|---|
| G1-D-OUTBOX | notifications produites après commit **sans reprise** | une panne du writer perd la notification (métier non bloqué) |
| G1-D-AUDIENCE | audience notification = **formateurs** | apprenants et RP ne sont pas notifiés (RG-033 `PARTIAL`) |
| Audit synchrone | 8 listeners `@EventListener` + `REQUIRES_NEW` | une trace peut manquer sans annuler l'action ; pas d'outbox d'audit |
| `DEC-G1-E-ANTIVIRUS` | aucun antivirus | contrôle **structurel** seul ; ne jamais garantir l'absence de malware |
| Orphelins de fichiers | balayage `NOT_IMPLEMENTED` | un fichier peut subsister après une suppression best-effort échouée |
| `DEC-G1-010` | coût SQL linéaire par séance sur le dashboard | dégradation si la fenêtre contient beaucoup de séances |
| `R-G1-30` | rétention des pièces `DELETED` **`À_DÉFINIR`** | politique RGPD à arrêter avant tout usage réel |
| Rate-limiting | `/auth/login` non limité | dette de sécurité assumée (`docs/07` §5) |
| Stockage local | pièces jointes sur système de fichiers local | non persistant sur un hébergement éphémère ; port `JustificationFileStorage` prêt pour un adaptateur objet |
| Isolation des tests | `EnrollmentDirectoryTests` a échoué **une fois** sous `TZ=UTC` (1re passe G1) | **non reproduit** en 5 répétitions isolées ni sur les runs complets ; **cause non déterminée** (`TEST_ISOLATION_DECISION.md`) |

## Infrastructure

`docker compose up -d` démarre `mysql` (8.4), `redis` (7.4), `mailpit`,
`mosquitto`. Les trois premiers passent `healthy` ; Mosquitto n'a pas de
sonde et **aucun code back-end ne le consomme**. Nécessite un `.env`
local non versionné (`cp .env.example .env`, puis renseigner au minimum
`MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`
≥ 32 octets et, pour le profil `demo`, `ESIC_DEMO_PASSWORD` ≥ 12
caractères). Hors Docker, exporter aussi `JUSTIFICATION_STORAGE_PATH`
vers un répertoire local inscriptible — le défaut `/data/uploads/...`
n'est pas accessible en écriture. Voir `README.md`.

## Prochaines priorités produit

1. Démonstration manuelle du parcours et captures d'écran (seul point
   qui empêche de dépasser `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`).
2. Outbox transactionnelle (notifications **et** audit) — lève
   G1-D-OUTBOX et la dette des 8 listeners synchrones.
3. Élargissement de l'audience des notifications (apprenants / RP).
4. Cartes de tableau de bord manquantes + chargement par lot des séances
   (`DEC-G1-010`).
5. Politique de rétention des pièces jointes et des audits (`R-G1-30`).
6. Écrans d'écriture `academic` / `enrollment` / émission d'invitation.

## Règle de mise à jour

Ce document doit rester **court** et refléter le dépôt. Ne jamais
déclarer :

- `TESTED` sans commande exécutée ;
- `DEMONSTRATED` / démontré sans vérification manuelle enregistrée ;
- `DEPLOYED` sans URL ou preuve ;
- `FONCTIONNEL` seulement parce que le code existe.

La chronologie détaillée va dans `docs/reports/PROJECT_HISTORY.md`.
