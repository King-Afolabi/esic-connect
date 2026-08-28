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

## Avancement vérifié — 28 août 2026

- **TR-002 (Rôles)** : `IMPLÉMENTÉ` et `TESTÉ` au niveau persistance
  uniquement (`identity/internal` : `UserAccount`, `Role`, `UserRole` ;
  migrations Flyway `V1`/`V2` ; 6 rôles système seedés ; unicité d'une
  affectation active + réattribution après clôture vérifiées par test
  réel). Le contrôle d'accès par rôle (TZ-001 à 010) reste `REPORTÉ` :
  aucune route ni service métier n'existe encore.
- **TR-009 (Audit)** : `IMPLÉMENTÉ` et `TESTÉ` au niveau persistance
  (`audit/internal.AuditEvent` : acteur nullable après suppression du
  compte avec conservation du snapshot, vérifié par test réel). Aucune
  écriture d'audit depuis un service métier réel : `REPORTÉ`.
- **TR-001 (Connexion)** : toujours `CONÇU` uniquement — le socle de
  persistance de `user_account` existe (`password_hash` en base), mais
  aucune authentification, JWT, MFA ni WebAuthn n'est implémenté à ce
  stade (hors périmètre de cette tâche).

Preuve : `backend/src/test/java/com/esic/connect/identity/`,
`backend/src/test/java/com/esic/connect/audit/`, exécution réelle de
`./mvnw test` (9/9, `BUILD SUCCESS`) — voir `docs/CURRENT-STATE.md`.

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