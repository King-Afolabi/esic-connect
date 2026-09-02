# Stratégie de tests et cahier de recette — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Version | 1.0 |
| Date | 28 août 2026 |
| Statut | À exécuter progressivement |
| Responsable | Abubacar AFOLABI |

---

# 1. Objectifs

La stratégie doit vérifier :

- la conformité fonctionnelle ;
- l’intégrité des données ;
- les autorisations ;
- la sécurité ;
- les performances ;
- l’accessibilité ;
- la résilience ;
- l’IoT ;
- l’IA ;
- la démonstrabilité.

---

# 2. Niveaux de tests

## Tests unitaires

Ciblent :

- une règle ;
- une fonction ;
- un service isolé ;
- un calcul.

## Tests d’intégration

Ciblent :

- Spring Boot et MySQL ;
- Spring Boot et Redis ;
- migrations ;
- sécurité ;
- repository ;
- FastAPI ;
- MQTT.

## Tests d’API

Ciblent :

- routes ;
- validation ;
- statuts HTTP ;
- autorisations ;
- erreurs.

## Tests end-to-end

Ciblent les parcours complets Angular → Spring Boot → données.

## Tests de sécurité

Ciblent :

- authentification ;
- autorisations ;
- injections ;
- fichiers ;
- sessions ;
- QR ;
- IoT.

## Tests de performance

Ciblent :

- latence ;
- cache ;
- import ;
- rapports ;
- concurrence.

## Recette utilisateur

Vérifie que la solution répond aux besoins du Product Owner.

---

# 2bis. État réel des tests (audit du 2 septembre 2026)

> Les §2 et §5 à §16 décrivent une **stratégie cible**. Cette section
> donne ce qui existe **réellement** dans le dépôt, mesuré sur HEAD
> `d3450e6`.

## 2bis.1 Totaux mesurés

| Suite | Commande | Résultat |
|---|---|---|
| Back-end | `cd backend && ./mvnw clean test` | **811 tests, 0 échec, 0 erreur, 0 ignoré** — **96 classes** de test |
| Front-end | `cd frontend && npm test -- --watch=false` | **71 fichiers / 602 tests / 0 échec** (Vitest + jsdom) |
| Lint front | `npm run lint` | « All files pass linting » |
| Build front | `npm run build` | initial **484,52 kB** brut, 0 alerte de budget |
| Audit npm | `npm audit --audit-level=high` | **passe** (0 haute, 0 critique) — 1 vulnérabilité **modérée** sur `qs`, tirée par `@angular/cli` (outillage de développement, absent du bundle servi) ; suivie dans l'issue de migrations majeures |

Preuves complémentaires relevées pendant le lot G1
(`G1_FINAL_REPORT.md` §11) : suite back verte sous les **trois fuseaux**
(défaut, `TZ=UTC`, `TZ=Europe/Paris`) ; **Flyway `V1 → V16` rejoué sur
une base `esic_test` recréée vierge** suivi de `ddl-auto=validate` OK ;
`ModularityTests` vert (**14 modules**).

## 2bis.1bis Base utilisée par la suite — isolation test / démonstration

La suite back-end s'exécute sur **`MYSQL_TEST_DATABASE`** (défaut
`esic_test`), jamais sur `MYSQL_DATABASE`. Les deux variables étaient
auparavant confondues : lancer le back-end de démonstration avec
`MYSQL_DATABASE=esic_connect_demo` faisait alors écrire la suite **dans
la base de démonstration** (les tests créent des milliers de comptes et
tronquent des tables).

Vérification exécutée le 2 septembre 2026 : suite complète lancée avec
`MYSQL_DATABASE=esic_connect_demo` **exporté**, volumes de
`esic_connect_demo` relevés avant et après — **identiques** sur les 15
tables métier suivies (`user_account` 14, `enrollment` 10,
`attendance_record` 10, `audit_event` 67, …). En CI,
`.github/workflows/backend-ci.yml` impose `MYSQL_TEST_DATABASE:
esic_connect_ci` (base éphémère du service, jetée à chaque run).

Les tests marqués du tag JUnit `perf` (`AttendanceTokenPerfTests`,
`StudentImportSimulationPerfTests`) sont **exclus** du run par défaut ;
`./mvnw test -Pperf` les exécute. Ils produisent des mesures
**indicatives**, pas une campagne de charge
(`docs/reports/PERF_NOTES.md`).

## 2bis.2 Nature exacte de chaque niveau — ne pas confondre

| Niveau | Existe ? | Ce que c'est réellement |
|---|---|---|
| **Tests unitaires** | oui | JUnit 5 (+ Mockito) sur les règles pures : parseurs CSV, normalisation, validation de champs, `CidrValidator`, résolution d'alternance, seuil de retard, calcul de demi-journées, tri des rapports, `JustificationFileSafetyValidator`, isolation de l'échec d'audit |
| **Tests d'intégration** | oui | `@SpringBootTest` / `@DataJpaTest` sur **MySQL réel** (pas H2) et **Redis réel** — contraintes SQL, migrations Flyway, transactions, concurrence |
| **Tests d'API** | oui | `TestRestTemplate` / `MockMvc` : statuts HTTP, corps d'erreur, en-têtes |
| **Recette de bout en bout (API)** | oui | `recette/PriorityPathRecetteIntegrationTests` — **une seule** classe rejouant le parcours prioritaire complet par appels HTTP réels, avec **un seul apprenant** créé par l'import puis activé et réutilisé jusqu'au justificatif |
| **Tests e2e navigateur** | **NON — `NOT_IMPLEMENTED`** | aucune dépendance Playwright / Cypress / Puppeteer, aucun script `e2e`. Décision `DEC-G1-011`. **La recette API n'est pas un e2e** : elle ne pilote aucun navigateur, ne rend aucun composant Angular et ne valide aucune interaction utilisateur |
| **Tests manuels / démonstration** | **NON consignés** | aucune manipulation UI enregistrée dans le dépôt. Seul le **parcours API** a été relevé à la main (`docs/11-guide-demonstration.md` §11.8, statuts HTTP) |
| **Tests de performance** | partiels | 2 tests taggés `perf` + mesures indicatives ; **aucune** campagne de charge, objectif « < 100 ms » non validé sur l'ensemble des routes |
| **Tests d'accessibilité** | partiels | 2 fichiers `*.a11y.spec.ts` avec `axe-core` ; pas d'audit outillé complet, pas de test lecteur d'écran |

## 2bis.3 Ce qui est réellement couvert, par thème

**Sécurité** — une classe `*SecurityTests` par module
(`identity`, `academic`, `enrollment`, `alternation`, `organization`,
`attendance`…) : matrice `401` anonyme / `403` hors rôle ou hors
périmètre / `200` autorisé. Plus :
`HttpSecurityHeadersIntegrationTests` (CSP, `Referrer-Policy`, `nosniff`,
`X-Frame-Options`, anti-cache, CORS accepté depuis une origine listée et
**rejeté sinon**), cloisonnement apprenant (AC-017), accès croisé à une
pièce jointe (`404`, pas d'oracle d'existence).

**Transactionnalité** — invariants nommés `T1` à `T6` de l'import CSV :
`T1` simulation sans écriture métier, `T3` rollback total sur exception,
`T4` e-mail seulement `AFTER_COMMIT`, `T5` aucune trace d'audit si
rollback. Étendus au lot G1 : publication de planning qui rollbacke ⇒
job `FAILED` **déterministe** ; rollback métier ⇒ **0** notification ;
annulation de séance qui rollbacke ⇒ **0** ligne d'audit
`SESSION_CANCELLED` (faute injectée, sans modifier de bean de
production) ; purge Redis seulement **après** commit.

**Concurrence** — inscriptions simultanées, affectations pédagogiques,
émargement double, corrections concurrentes, confirmations d'import,
**publications de planning concurrentes** (le perdant est idempotent,
jamais `FAILED`), ouverture / annulation simultanées d'une séance, fin de
remplacement concurrente. Attendu systématique : `2xx` / `409`, **jamais
de `5xx`**.

**Fuseaux horaires** — la suite complète est verte sous défaut, `UTC` et
`Europe/Paris`. Un incident de fuseau réel a été corrigé au lot G1
(couverture d'inscription décidée à la **date civile de la séance**, plus
à « aujourd'hui en UTC » — `AttendanceServiceSessionDateTests`).

**Fichiers** — `CsvFileGuardTests` (rejet ZIP / OLE2 / PDF / octet nul,
UTF-8 strict, taille), `JustificationFileSafetyValidatorTests` (magic
bytes, extension trompeuse, polyglotte, nom assaini),
`LocalFilesystemJustificationFileStorageTests` (déplacement atomique,
anti-traversal, aucun fichier partiel),
`JustificationAttachmentIntegrationTests` (dépôt, compensation,
réconciliation, téléchargement, accès croisé).

**Notifications** — `NotificationIntegrationTests` (idempotence
`dedup_key`, isolation par destinataire, rollback ⇒ 0 notification) et
`NotificationDeliveryResilienceIntegrationTests` (l'échec d'un
destinataire n'interrompt pas les autres ; l'échec complet du writer ne
casse pas la mutation métier).

**Tableaux de bord** — `DashboardIntegrationTests` : périmètre par rôle,
contexte multi-rôle vérifié (`403` si le rôle n'est pas détenu),
remplaçant actif inclus, et **deux mesures de compteur de requêtes
Hibernate** (croissance nulle selon le nombre de classes ; croissance
linéaire ≈ 2 requêtes/séance selon le nombre de séances).

**Modularité** — `ModularityTests` (Spring Modulith) : aucune dépendance
vers un package `.internal` d'un autre module, aucun cycle.

## 2bis.4 Limites de couverture assumées

- **Aucun e2e navigateur** et **aucune démonstration UI consignée** ⇒ le
  lot G1 reste `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`.
- Aucune mesure de **couverture de code** (JaCoCo non configuré) : le
  nombre de tests ne dit rien du pourcentage de lignes couvertes.
- Pas de tests de **charge**, de **résilience infrastructure** (panne
  MySQL en cours de transaction), ni de **restauration de sauvegarde**.
- Pas de tests IA ni IoT (§12 et §13 ci-dessous) — **ces domaines n'ont
  aucun code**.
- Pas de test d'**injection / XSS / CSRF automatisé** : l'exposition est
  réduite par construction (JPA paramétré, Angular échappant par défaut,
  JWT en en-tête et non en cookie, pas de rendu HTML de fichier
  utilisateur) mais **aucun scanner n'a été passé**.
- Un test d'intégration (`EnrollmentDirectoryTests`) a échoué **une
  fois** sous `TZ=UTC` pendant le lot G1 ; **non reproduit** en 5
  répétitions isolées ni sur les runs complets — **cause non
  déterminée** (`docs/reports/TEST_ISOLATION_DECISION.md`).

---

# 3. Environnements de tests

| Environnement | Utilisation |
|---|---|
| Local | Tests manuels |
| Test | Tests automatisés |
| Staging | Recette et démonstration |
| Production | Tests limités non destructifs |

Aucune donnée réelle ne doit être utilisée en test ou staging.

---

# 4. Données de test

Créer au minimum :

- 1 super administrateur ;
- 1 administrateur ;
- 1 administration scolaire ;
- 2 responsables pédagogiques ;
- 3 formateurs ;
- 3 formations ;
- 4 classes ;
- 30 apprenants ;
- 3 rythmes d’alternance ;
- 10 matières ;
- 20 séances ;
- présences normales et anormales ;
- justificatifs fictifs ;
- Raspberry Pi fictive.

---

# 5. Tests unitaires prioritaires

| ID | Cas | Résultat attendu |
|---|---|---|
| TU-001 | Retard de 10 min | `PRESENT` |
| TU-002 | Retard de 20 min | `LATE` |
| TU-003 | Retard de 40 min | Validation manuelle |
| TU-004 | Deux contrôles du matin | Demi-journée validée |
| TU-005 | Quatre contrôles | Journée validée |
| TU-006 | Un seul contrôle | `PARTIAL` ou `TO_CONFIRM` |
| TU-007 | Aucun contrôle | `ABSENT` |
| TU-008 | Justificatif accepté | `EXCUSED` |
| TU-009 | Journée entreprise | Pas d’absence |
| TU-010 | Exception école | Présence attendue |
| TU-011 | Jeton expiré | Refus |
| TU-012 | Double validation | Résultat idempotent |
| TU-013 | Utilisateur hors classe | Refus |
| TU-014 | Distanciel non autorisé | Refus |
| TU-015 | Changement de classe | Historique conservé |

---

# 6. Tests du modèle de données

| ID | Action | Résultat |
|---|---|---|
| TD-001 | Supprimer un apprenant avec présence | Refus FK |
| TD-002 | Archiver l’apprenant | Succès |
| TD-003 | Supprimer une classe utilisée | Refus FK |
| TD-004 | Clôturer une inscription | Succès |
| TD-005 | Deux présences au même checkpoint | Contrainte unique |
| TD-006 | Deux événements IoT identiques | Refus doublon |
| TD-007 | Deux versions identiques | Refus |
| TD-008 | Changement concurrent de classe | Une seule inscription active |
| TD-009 | Correction d’une présence | Historique ajouté |
| TD-010 | Suppression du fichier expiré | Métadonnées conservées |

---

# 7. Tests d’authentification

| ID | Scénario | Résultat |
|---|---|---|
| TA-001 | Identifiants valides | Connexion |
| TA-002 | Mot de passe invalide | `401` neutre |
| TA-003 | Compte suspendu | Refus |
| TA-004 | Trois échecs | Contrôle renforcé |
| TA-005 | Déconnexion | Session révoquée |
| TA-006 | Mot de passe changé | Ancienne session révoquée |
| TA-007 | Nouvel appareil | MFA adaptatif |
| TA-008 | Passkey valide | Connexion |
| TA-009 | Credential révoqué | Refus |
| TA-010 | Cookie absent | `401` |

---

# 8. Tests d’autorisation

| ID | Scénario | Résultat |
|---|---|---|
| TZ-001 | Étudiant lit un autre étudiant | `403` |
| TZ-002 | Responsable lit une autre formation | `403` |
| TZ-003 | Formateur ouvre une séance non affectée | `403` |
| TZ-004 | Remplaçant ouvre sa séance | Autorisé |
| TZ-005 | Formateur publie le planning | `403` |
| TZ-006 | Admin consulte logs techniques critiques | Selon rôle |
| TZ-007 | Étudiant modifie une présence | `403` |
| TZ-008 | Administration exporte un rapport autorisé | Autorisé |
| TZ-009 | Responsable cumule rôle formateur | Contextes corrects |
| TZ-010 | Délégation expirée | `403` |

---

# 9. Tests des imports

| ID | Fichier | Résultat |
|---|---|---|
| TI-001 | CSV apprenants valide | Simulation valide |
| TI-002 | Colonne email absente | Erreur bloquante |
| TI-003 | Email invalide | Ligne en erreur |
| TI-004 | Doublon dans fichier | Avertissement/erreur |
| TI-005 | Apprenant existant | Mise à jour proposée |
| TI-006 | Nouvelle classe | Changement proposé |
| TI-007 | Formation hors périmètre | `403` |
| TI-008 | 100 apprenants | Traitement réussi |
| TI-009 | XLSX multifeuille | Mapping par feuille |
| TI-010 | Fichier trop grand | Refus |
| TI-011 | Macro ou format interdit | Refus |
| TI-012 | Confirmation deux fois | Idempotence |
| TI-013 | Planning valide | Brouillon créé |
| TI-014 | Horaire inversé | Erreur |
| TI-015 | Conflit de salle | Alerte |
| TI-016 | Formateur inconnu | À corriger |
| TI-017 | Mapping IA faible | Confirmation obligatoire |

---

# 10. Tests d’émargement

| ID | Scénario | Résultat |
|---|---|---|
| TE-001 | QR valide | Présence |
| TE-002 | QR expiré | Refus |
| TE-003 | QR d’une autre séance | Refus |
| TE-004 | QR fixe hors réseau | Refus |
| TE-005 | QR fixe après début | Refus |
| TE-006 | Code court valide | Présence |
| TE-007 | Deux scans simultanés | Une présence |
| TE-008 | WebAuthn échoue | Parcours alternatif |
| TE-009 | Apprenant sans smartphone | Saisie manuelle |
| TE-010 | Apprenant provisoire | Entrée provisoire |
| TE-011 | Distanciel collectif | Canal distant |
| TE-012 | Distanciel individuel autorisé | Accepté |
| TE-013 | Distanciel individuel non autorisé | Refus |
| TE-014 | Correction | Motif et audit |
| TE-015 | SSE déconnecté | Reconnexion |

---

# 11. Tests de fichiers

| ID | Scénario | Résultat |
|---|---|---|
| TF-001 | PDF 2 Mo | Accepté |
| TF-002 | PDF 6 Mo | Refus |
| TF-003 | Extension JPG avec exécutable | Refus |
| TF-004 | Utilisateur non autorisé | `403` |
| TF-005 | Nom avec traversée de chemin | Neutralisé |
| TF-006 | Fichier purgé | Métadonnée présente, contenu absent |

---

# 12. Tests IA

| ID | Scénario | Résultat |
|---|---|---|
| TIA-001 | Colonne « Courriel » | Mapping vers email |
| TIA-002 | Score faible | `TO_REVIEW` |
| TIA-003 | FastAPI indisponible | Mode manuel |
| TIA-004 | Suggestion rejetée | Aucune application |
| TIA-005 | Donnée ambiguë | Avertissement |
| TIA-006 | Résultat validé | Décision humaine tracée |

---

# 13. Tests IoT

| ID | Scénario | Résultat |
|---|---|---|
| TO-001 | Heartbeat valide | Dispositif en ligne |
| TO-002 | Device inconnu | Refus |
| TO-003 | Event ID rejoué | Ignoré/refusé |
| TO-004 | Séquence ancienne | Refus |
| TO-005 | Session inexistante | Refus |
| TO-006 | Broker coupé | Mise en file locale |
| TO-007 | Reconnexion | Republie sans doublon |
| TO-008 | Credential révoqué | Refus |

---

# 14. Tests de performance

## Scénarios

| ID | Mesure | Objectif initial |
|---|---|---|
| TP-001 | Planning en cache | Viser < 100 ms local |
| TP-002 | Génération du jeton | Viser < 100 ms local |
| TP-003 | Validation présence | Mesurer p50/p95 |
| TP-004 | Import de 100 élèves | Temps documenté |
| TP-005 | Rapport mensuel | Temps documenté |
| TP-006 | 20 scans simultanés | Aucun doublon |

Les objectifs sont des cibles mesurées, pas des garanties non vérifiées.

---

# 15. Tests de résilience

| ID | Panne | Résultat |
|---|---|---|
| TR-001 | Redis indisponible | QR indisponible proprement |
| TR-002 | FastAPI indisponible | Import manuel disponible |
| TR-003 | SMTP indisponible | Email en attente |
| TR-004 | MQTT indisponible | File locale |
| TR-005 | SSE interrompu | Reconnexion |
| TR-006 | Redémarrage Spring | Données durables conservées |
| TR-007 | Restauration MySQL | Application fonctionnelle |

---

# 16. Tests d’accessibilité

- navigation au clavier ;
- focus visible ;
- labels ;
- contraste ;
- messages d’erreur ;
- tableaux alternatifs ;
- solution sans caméra ;
- solution sans WebAuthn ;
- responsive ;
- zoom.

---

# 17. Cahier de recette

## REC-001 — Parcours responsable pédagogique

### Préconditions

- compte responsable actif ;
- formation affectée ;
- classe inexistante ou vide ;
- fichier CSV disponible.

### Étapes

1. Se connecter.
2. Sélectionner le contexte responsable.
3. Créer ou sélectionner la classe.
4. Importer les apprenants.
5. Lire les erreurs.
6. Confirmer.
7. Importer le planning.
8. Corriger les conflits.
9. Publier.

### Résultat attendu

- comptes créés ou mis à jour ;
- historique conservé ;
- séances créées ;
- audit disponible.

---

## REC-002 — Parcours formateur

1. Se connecter.
2. Consulter les séances.
3. Ouvrir la séance.
4. Ouvrir le point de contrôle.
5. Afficher le QR.
6. Suivre les présences.
7. Corriger un retard.
8. Clôturer.

### Résultat attendu

La liste est à jour et les actions sont auditées.

---

## REC-003 — Parcours apprenant

1. Se connecter.
2. Consulter le prochain cours.
3. Ouvrir l’émargement.
4. Scanner ou saisir le code.
5. Confirmer localement.
6. Consulter l’historique.
7. Déposer un justificatif fictif.

### Résultat attendu

La présence personnelle est visible sans exposition des données des
autres étudiants.

---

## REC-004 — Rapport

1. Sélectionner une classe.
2. Choisir une date.
3. Générer le rapport.
4. Exporter en CSV.
5. Rechercher un apprenant.
6. Générer son rapport.

### Résultat attendu

Les statuts et demi-journées sont cohérents.

---

## REC-005 — IoT

1. Lancer Mosquitto.
2. Lancer le client Raspberry Pi ou simulateur.
3. Publier un heartbeat.
4. Publier un événement.
5. Republier le même événement.

### Résultat attendu

Le premier est traité, le second est identifié comme doublon.

---

# 18. Fiche d’anomalie

| Champ | Contenu |
|---|---|
| ID | Identifiant |
| Titre | Résumé |
| Environnement | Local/test/staging |
| Version | Commit |
| Gravité | Mineure/majeure/bloquante |
| Étapes | Reproduction |
| Attendu | Résultat attendu |
| Obtenu | Résultat constaté |
| Preuve | Capture/log |
| Responsable | Affectation |
| Statut | Ouverte/corrigée/vérifiée |

---

# 18b. Scénario de recette — gestion de l'assiduité (tranche V10)

Branche `feature/attendance-management-and-reporting`. Exécuté en local
(profil `demo`), statuts HTTP relevés — voir
`docs/11-guide-demonstration.md` §10.

1. Ouvrir une séance ; constater le point de contrôle `START` ouvert.
2. Créer un second point de contrôle (`CUSTOM`), l'ouvrir, émettre son
   jeton (`POST .../checkpoints/{cpId}/attendance-token`), un apprenant
   émarge → présence rattachée à **ce** point de contrôle.
3. Émarger 20 min après le début planifié → statut `LATE`,
   `lateMinutes > 0`.
4. Saisir manuellement une présence `ABSENT` (motif obligatoire),
   corriger en `PRESENT` (motif), annuler (`CANCELLED`) ; l'historique
   `GET .../attendance/{aid}/history` liste les 3 entrées ordonnées.
5. Fermer la séance ; un apprenant dépose un justificatif métier sur une
   absence dérivée (`POST /api/v1/me/attendance/justifications`) ; un
   second dépôt actif → `409`.
6. Un `TEACHER` sur `.../justifications/{id}/review` → `403` ;
   l'administration accepte → présence `ABSENT → EXCUSED_ABSENCE` ; un
   refus sans motif → `400`.
7. Consulter `GET /api/v1/attendance/reports/summary` puis
   `.../students` ; exporter le CSV (`.../students/export`) — vérifier
   BOM UTF-8, séparateur `;`, et qu'une cellule débutant par `=` est
   préfixée d'une apostrophe.
8. Vérifier les rôles : un `STUDENT` sur `/me/attendance` → `200`, un
   non-`STUDENT` → `403` ; un `TEACHER` sur `/attendance/reports/**` →
   `403`.
9. **(PR #22)** `GET /api/v1/sessions/{id}/attendance/candidates` : les 2
   apprenants inscrits, sans e-mail ni id SQL ; `STUDENT` → `403`,
   anonyme → `401`. Utiliser l'identifiant renvoyé pour une présence
   manuelle.
10. **(PR #22)** Rapports : `sort=lastName,desc` → `200` ; `sort=email,asc`
    ou `sort=startsAt,sideways` → `400 ATT_REPORT_INVALID_SORT`. Le code
    de classe des rapports est lisible (`C-DEMO`), jamais un UUID.
11. **(PR #22)** `GET /api/v1/sessions/{id}/attendance/export` : le
    formateur affecté exporte sa séance (`200`, nom de fichier contrôlé) ;
    `STUDENT` → `403`.
12. **(PR #22)** Concurrence : deux `validate` / deux `correct` / deux
    `review` / QR+manuel simultanés sur la même cible → exactement une
    écriture, un `409` contrôlé, **aucun `500`**.

**Critères de validation** : aucun `500` pour un conflit métier attendu
(y compris en concurrence) ; motif obligatoire respecté ; historique
conservé ; aucune donnée personnelle ni identifiant SQL dans l'audit ou
le CSV ; un `timeZoneId` persistant invalide fait échouer le rapport en
`500` contrôlé plutôt que de produire des chiffres trompeurs ; JWT et
contexte de rôle en mémoire seule côté front (aucun accès `localStorage`
/ `sessionStorage`).

---

# 19. Critères de sortie

Une version est candidate à la soutenance lorsque :

- le parcours principal fonctionne ;
- aucune anomalie bloquante n’est ouverte ;
- les tests critiques passent ;
- les données sont fictives ;
- les rôles sont vérifiés ;
- les rapports fonctionnent ;
- la documentation est cohérente ;
- une sauvegarde est disponible ;
- la vidéo de secours existe.