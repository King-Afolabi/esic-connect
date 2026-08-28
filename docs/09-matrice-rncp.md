# Matrice de traçabilité RNCP 39394 — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Candidat | Abubacar AFOLABI |
| Certification | RNCP 39394 |
| Version | 1.0 |
| Date | 28 août 2026 |
| Statut | À mettre à jour selon les preuves réelles |

---

# 1. Principe

La certification RNCP 39394 est évaluée par un rapport d’expérience
professionnelle comportant quatre parties correspondant aux quatre
blocs. ([francecompetences.fr](https://www.francecompetences.fr/recherche/rncp/39394?utm_source=openai))

Statuts utilisés :

- `CONÇU` ;
- `IMPLÉMENTÉ` ;
- `TESTÉ` ;
- `DÉMONTRÉ` ;
- `SIMULÉ` ;
- `REPORTÉ`.

Une exigence décrite n’est pas automatiquement réalisée.

---

# 2. Bloc 1 — Pilotage stratégique

| Compétence projet | Livrable | Preuve | Statut |
|---|---|---|---|
| Analyser l’existant | Cadrage | Description du processus actuel | CONÇU |
| Recueillir les besoins | Cahier des charges | Besoins par acteur | CONÇU |
| Traduire les besoins | Backlog | Epics et user stories | CONÇU |
| Piloter le projet | Roadmap | 13 sprints | CONÇU |
| Gérer les priorités | Product Backlog | MoSCoW | CONÇU |
| Gérer les risques | Registre des risques | Cotation et mesures | CONÇU |
| Définir les indicateurs | Cahier des charges | KPI | CONÇU |
| Assurer la traçabilité | Matrice RNCP | Exigence-code-test | CONÇU |
| Conduire le changement | Guide utilisateur futur | Formation et notifications | À FAIRE |
| Prendre en compte l’accessibilité | Cahier des charges | Alternatives au QR | CONÇU |
| Prendre en compte la durabilité | Architecture | PWA, mutualisation, archivage | CONÇU |
| Manager les parties prenantes | Gouvernance | PO, Scrum Master, développeur | CONÇU |

## Preuves attendues

- `01-cadrage.md` ;
- `02-cahier-des-charges.md` ;
- `05-product-backlog.md` ;
- `05a-roadmap-six-mois.md` ;
- `06-risques.md` ;
- comptes rendus disponibles ;
- GitHub Projects ;
- historique Git.

---

# 3. Bloc 2 — Technologies avancées et développement

| Compétence projet | Réalisation | Preuve | Statut |
|---|---|---|---|
| Choisir une méthode | Scrum/Kanban | Roadmap et backlog | CONÇU |
| Choisir les technologies | Stack | Architecture | CONÇU |
| Concevoir l’UX | Angular/PWA | Maquettes et écrans | À FAIRE |
| Développer le back-end | Spring Boot | Code et tests | À FAIRE |
| Développer le front-end | Angular | Code et captures | À FAIRE |
| Concevoir la base | MySQL | Modèle de données | CONÇU |
| Utiliser Redis | Cache et QR | Tests | À FAIRE |
| Importer les données | CSV/XLSX | Démonstration | À FAIRE |
| Produire des rapports | CSV/Excel | Exports | À FAIRE |
| Utiliser l’IA | Mapping intelligent | FastAPI et scores | À FAIRE |
| Sécuriser l’application | Spring Security | Tests | À FAIRE |
| Tester la plateforme | Tests | Rapports de tests | À FAIRE |
| Documenter l’API | OpenAPI | Swagger | À FAIRE |
| Former les utilisateurs | Guides | Documents | À FAIRE |

---

# 4. Bloc 3 — Infrastructure et cybersécurité

| Compétence projet | Réalisation | Preuve | Statut |
|---|---|---|---|
| Concevoir l’infrastructure | Local/staging/AWS | Diagrammes | CONÇU |
| Conteneuriser | Docker Compose | Fichiers Compose | À FAIRE |
| Séparer les environnements | 4 profils | Configuration | À FAIRE |
| Authentifier | Password/WebAuthn/MFA | Tests | À FAIRE |
| Autoriser | RBAC et périmètres | Tests `403` | À FAIRE |
| Protéger les sessions | Cookies sécurisés | Configuration | À FAIRE |
| Limiter les attaques | Redis/Turnstile | Tests | À FAIRE |
| Protéger les données | MySQL/fichiers | Tests | À FAIRE |
| Auditer | AuditEvent | Écran et données | À FAIRE |
| Superviser | Actuator | Health checks | À FAIRE |
| Sauvegarder | MySQL/fichiers | Script | À FAIRE |
| Restaurer | Procédure | Rapport de test | À FAIRE |
| Gérer les incidents | Procédure | Sécurité/RGPD | CONÇU |
| Détecter les anomalies | IA/règles | Alertes | À FAIRE |
| Assurer la résilience | Modes dégradés | Tests | À FAIRE |

---

# 5. Bloc 4 — IoT sécurisé et IA

| Compétence projet | Réalisation | Preuve | Statut |
|---|---|---|---|
| Développer un objet connecté | Client Raspberry Pi | Code Python | À FAIRE |
| Intégrer l’objet au SI | MQTT → Spring Boot | Démonstration | À FAIRE |
| Identifier le dispositif | Device ID/credential | Configuration | À FAIRE |
| Sécuriser les événements | Anti-rejeu | Tests | À FAIRE |
| Gérer la télémétrie | Heartbeat | Logs | À FAIRE |
| Gérer la coupure réseau | File locale | Test | À FAIRE |
| Analyser les événements | IA/règles | Alertes | À FAIRE |
| Détecter une anomalie | Score | Écran | À FAIRE |
| Préparer l’évolution NFC | Architecture | Dossier | CONÇU |
| Préparer AWS IoT Core | Architecture cible | Diagramme | CONÇU |

---

# 6. Matrice exigence → code → test → preuve

| ID | Exigence | Code cible | Test cible | Preuve | Bloc |
|---|---|---|---|---|---|
| TR-001 | Connexion | `identity` | TA-001 | Swagger/capture | BC02/BC03 |
| TR-002 | Rôles | `identity` | TZ-001 à 010 | Tests | BC03 |
| TR-003 | Import apprenants | `enrollment` | TI-001 à 012 | Vidéo | BC02 |
| TR-004 | Import planning | `planning` | TI-013 à 017 | Capture | BC02 |
| TR-005 | Publication | `planning` | Intégration | Séances créées | BC02 |
| TR-006 | QR | `attendance` | TE-001 à 007 | Démo | BC02/BC03 |
| TR-007 | WebAuthn | `identity` | TA-008 | Démo | BC02/BC03 |
| TR-008 | Rapports | `reporting` | REC-004 | Export | BC02 |
| TR-009 | Audit | `audit` | Contrôle DB | Écran | BC03 |
| TR-010 | IA | `ai-service` | TIA | Scores | BC02/BC03 |
| TR-011 | MQTT | `iot` | TO | Logs | BC04 |
| TR-012 | Anti-rejeu | `iot` | TO-003 | Refus doublon | BC03/BC04 |
| TR-013 | Sauvegarde | scripts | TR-007 | Rapport | BC03 |
| TR-014 | Pilotage | docs | Revue | Backlog | BC01 |
| TR-015 | Invitation / activation de compte | `identity`, `notification` | `AccountInvitation*Tests` | `./mvnw test` + Mailpit | BC02/BC03 |

## Avancement vérifié — 28 août 2026

- **TR-002 (Rôles)** : `IMPLÉMENTÉ` et `TESTÉ` — persistance
  (`identity/internal` : `UserAccount`, `Role`, `UserRole` ; migrations
  Flyway `V1`/`V2` ; 6 rôles système seedés ; unicité d'une affectation
  active + réattribution après clôture) **et** désormais portés dans le
  jeton JWT émis à la connexion (claim `roles`, autorités
  `ROLE_<code>`, filtrées aux affectations actives). Le contrôle
  d'accès par rôle sur des routes métier (TZ-001 à 010) reste
  `REPORTÉ` : aucune route métier n'existe encore.
- **TR-009 (Audit)** : `IMPLÉMENTÉ` et `TESTÉ`, désormais alimenté par
  un flux métier réel (connexion réussie/refusée), plus seulement par
  test direct de persistance : événement applicatif découplé
  (`identity.LoginSucceededEvent`/`LoginFailedEvent` →
  `audit/internal.SecurityAuditEventListener`, transaction dédiée
  `REQUIRES_NEW`), acteur nullable conservé pour un email inconnu sans
  jamais stocker l'adresse brute, échec de journalisation vérifié
  sans impact sur la réponse d'authentification.
- **TR-001 (Connexion)** : `IMPLÉMENTÉ` et `TESTÉ` —
  `POST /api/v1/auth/login` (email/mot de passe, `UserDetailsService`
  standard, `PasswordEncoder` délégué BCrypt, `AuthenticationManager`
  standard), JWT HS256 stateless (Spring Security OAuth2 Resource
  Server + Nimbus, sujet = identifiant public, `iat`/`exp`/`jti`/`roles`,
  aucune donnée personnelle), réponse publique strictement uniforme
  vérifiée pour email inconnu/mauvais mot de passe/compte non actif,
  `last_login_at` mis à jour. MFA et WebAuthn restent `REPORTÉ`
  (hors périmètre de cette tâche).
- **TR-015 (Invitation / activation de compte)** : `IMPLÉMENTÉ` et
  `TESTÉ` — `POST /api/v1/account-invitations` (protégé par
  `@PreAuthorize` : `ADMIN`, `SUPER_ADMIN`, `PEDAGOGICAL_MANAGER`,
  `SCHOOL_ADMINISTRATION` ; émission limitée aux comptes
  `PENDING_ACTIVATION` ; attribution du rôle demandé via `user_role` ;
  rôle inconnu ou inactif refusé), `GET …/validate` et `POST …/activate`
  publics. Jeton `SecureRandom` 32 octets Base64URL sans padding, seule
  l'empreinte SHA-256 est stockée (`account_invitation`, migration `V3`,
  `token_hash` UNIQUE, une seule invitation `PENDING` par compte via
  colonne générée), TTL configurable (`P30D` par défaut, refus de
  démarrage si ≤ 0), révocation des invitations `PENDING` antérieures,
  jeton à usage unique. Validation publique strictement générique
  (`{"valid": bool}`, réponse identique pour jeton inconnu / expiré /
  révoqué / accepté). Activation : mot de passe encodé (BCrypt), statut
  `ACTIVE`, `email_verified_at`. Email d'activation via Mailpit (module
  `notification`, écouteur `AFTER_COMMIT`, échec avalé sans jeton/email/
  lien dans les logs — file persistante = dette technique). Audit
  `ACCOUNT_INVITATION_ISSUED` / `ACCOUNT_ACTIVATED` sans jeton.
  `@PreAuthorize` refusé désormais traduit en `403` neutre
  (`GlobalExceptionHandler`).

Preuve : `backend/src/test/java/com/esic/connect/identity/`,
`backend/src/test/java/com/esic/connect/notification/`,
`backend/src/test/java/com/esic/connect/audit/`, exécution réelle de
`./mvnw test` (**50/50**, `BUILD SUCCESS`, lancé deux fois) — voir
`docs/CURRENT-STATE.md`. Émetteur JWT vérifié explicitement
(`JwtValidators`), jeton à émetteur incorrect refusé (401 nu, aucun
détail de validation exposé).

---

# 7. Mise à jour

Après chaque fonctionnalité :

1. renseigner le statut ;
2. ajouter le chemin du code ;
3. ajouter la commande de test ;
4. ajouter la preuve ;
5. ajouter le commit ;
6. identifier le bloc concerné.

---

# 8. Couverture finale attendue

## BC01

Preuves documentaires et de pilotage.

## BC02

Application fonctionnelle, IA, UX, base, imports et rapports.

## BC03

Infrastructure, sécurité, audit, tests, supervision et résilience.

## BC04

Raspberry Pi, MQTT, sécurité IoT et analyse des événements.

La fiche officielle exige que les quatre parties soient présentées dans
le rapport d’expérience professionnelle.