# Registre des risques — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Propriétaire | Abubacar AFOLABI |
| Product Owner | Monsieur BANKA |
| Scrum Master | Monsieur INOUSSA Chaabane |
| Version | 1.0 |
| Date | 28 août 2026 |
| Statut | À suivre pendant tout le projet |

---

# 1. Méthode d’évaluation

## Probabilité

| Valeur | Niveau |
|---:|---|
| 1 | Rare |
| 2 | Peu probable |
| 3 | Possible |
| 4 | Probable |
| 5 | Très probable |

## Impact

| Valeur | Niveau |
|---:|---|
| 1 | Négligeable |
| 2 | Faible |
| 3 | Modéré |
| 4 | Élevé |
| 5 | Critique |

## Criticité

```text
Criticité = Probabilité × Impact
```

| Score | Niveau |
|---:|---|
| 1–4 | Faible |
| 5–9 | Modéré |
| 10–15 | Élevé |
| 16–25 | Critique |

---

# 2. Risques projet

| ID | Risque | P | I | Score | Réponse |
|---|---|---:|---:|---:|---|
| R-P01 | Périmètre trop large | 5 | 5 | 25 | Prioriser les MUST |
| R-P02 | Retard de développement | 5 | 4 | 20 | Parcours principal avant les options |
| R-P03 | Documentation incohérente | 4 | 5 | 20 | Mise à jour après chaque tâche |
| R-P04 | Dépendance excessive à l’IA | 4 | 5 | 20 | Relecture et tests humains |
| R-P05 | Code non compris par le candidat | 4 | 5 | 20 | Explication et validation manuelle |
| R-P06 | Démonstration instable | 4 | 5 | 20 | Démo locale, données fixes et vidéo |
| R-P07 | Fausses preuves dans le rapport | 3 | 5 | 15 | Statuts conçu/implémenté/testé |
| R-P08 | Modification tardive des besoins | 4 | 3 | 12 | Versionner le cahier des charges |
| R-P09 | Git mal utilisé | 3 | 4 | 12 | Commits fréquents et branches simples |
| R-P10 | Perte du travail | 2 | 5 | 10 | Git distant et sauvegarde locale |

---

# 3. Risques fonctionnels

| ID | Risque | P | I | Score | Réponse |
|---|---|---:|---:|---:|---|
| R-F01 | Import erroné | 4 | 5 | 20 | Simulation et confirmation |
| R-F02 | Création de doublons | 4 | 5 | 20 | Unicité et rapprochement |
| R-F03 | Mauvaise affectation de classe | 3 | 5 | 15 | Prévisualisation avant validation |
| R-F04 | Conflit de planning | 4 | 4 | 16 | Détection avant publication |
| R-F05 | Faux calcul d’assiduité | 3 | 5 | 15 | Tests unitaires des règles |
| R-F06 | Présence enregistrée deux fois | 3 | 4 | 12 | Contrainte unique et idempotence |
| R-F07 | Mauvaise gestion de l’alternance | 3 | 4 | 12 | Calcul sur séances attendues |
| R-F08 | Justificatif mal traité | 3 | 4 | 12 | Workflow et motif obligatoire |
| R-F09 | Formateur non affecté | 3 | 3 | 9 | Alerte avant publication |
| R-F10 | Apprenant provisoire jamais régularisé | 3 | 3 | 9 | Tableau des régularisations |

---

# 4. Risques cybersécurité

| ID | Risque | P | I | Score | Mesures |
|---|---|---:|---:|---:|---|
| R-C01 | Vol de compte | 4 | 5 | 20 | MFA, WebAuthn, détection |
| R-C02 | Force brute | 4 | 4 | 16 | Rate limiting et verrouillage temporaire |
| R-C03 | Élévation de privilège | 3 | 5 | 15 | RBAC et contrôle de périmètre |
| R-C04 | IDOR | 4 | 5 | 20 | Contrôle de propriété serveur |
| R-C05 | Injection SQL | 3 | 5 | 15 | JPA, paramètres et tests |
| R-C06 | XSS | 3 | 4 | 12 | Encodage, CSP et Angular |
| R-C07 | CSRF | 3 | 4 | 12 | Cookies SameSite et token CSRF |
| R-C08 | Secret dans Git | 3 | 5 | 15 | `.env`, scan et revue |
| R-C09 | Fichier malveillant | 3 | 5 | 15 | MIME, taille, antivirus |
| R-C10 | QR rejoué | 4 | 4 | 16 | TTL, nonce, unicité |
| R-C11 | Cache exposant un autre périmètre | 3 | 5 | 15 | Clés contextualisées |
| R-C12 | API IA directement accessible | 2 | 4 | 8 | Réseau interne |
| R-C13 | MQTT non sécurisé | 3 | 5 | 15 | Identité, TLS cible, anti-rejeu |
| R-C14 | Journaux contenant des secrets | 3 | 5 | 15 | Filtrage et revue |
| R-C15 | Session trop longue | 3 | 4 | 12 | Timeout et révocation |

---

# 5. Risques RGPD

| ID | Risque | P | I | Score | Réponse |
|---|---|---:|---:|---:|---|
| R-D01 | Collecte excessive | 3 | 4 | 12 | Minimisation |
| R-D02 | Conservation indéfinie | 4 | 5 | 20 | Matrice de conservation |
| R-D03 | Accès non autorisé | 3 | 5 | 15 | Périmètres et audit |
| R-D04 | Données réelles envoyées à une IA | 3 | 5 | 15 | Données synthétiques |
| R-D05 | Pièces jointes trop accessibles | 3 | 5 | 15 | Téléchargement via API |
| R-D06 | Anonymisation insuffisante | 2 | 5 | 10 | Revue des agrégats |
| R-D07 | Absence de procédure de droits | 3 | 4 | 12 | Processus documenté |
| R-D08 | Adresse IP conservée sans besoin | 2 | 4 | 8 | Vérification volatile |
| R-D09 | Données biométriques stockées | 1 | 5 | 5 | WebAuthn sans biométrie serveur |
| R-D10 | Staging contenant des données réelles | 3 | 5 | 15 | Données fictives obligatoires |

La CNIL rappelle que les durées doivent être définies selon la finalité
et que les données ne peuvent pas être conservées indéfiniment.
([cnil.fr](https://www.cnil.fr/fr/passer-laction/les-durees-de-conservation-des-donnees?utm_source=openai))

---

# 6. Risques techniques

| ID | Risque | P | I | Score | Réponse |
|---|---|---:|---:|---:|---|
| R-T01 | Versions incompatibles | 3 | 4 | 12 | Versions figées |
| R-T02 | Docker indisponible | 2 | 4 | 8 | Lancement Maven/npm |
| R-T03 | Migration Flyway invalide | 3 | 5 | 15 | Test sur base vide |
| R-T04 | N+1 Hibernate | 4 | 3 | 12 | Requêtes ciblées |
| R-T05 | Mauvais cascade JPA | 3 | 5 | 15 | Aucun REMOVE métier |
| R-T06 | Redis indisponible | 3 | 4 | 12 | Dégradation contrôlée |
| R-T07 | FastAPI indisponible | 3 | 2 | 6 | Import manuel |
| R-T08 | MQTT indisponible | 3 | 3 | 9 | File locale et simulateur |
| R-T09 | SSE déconnecté | 3 | 2 | 6 | Reconnexion et rechargement |
| R-T10 | Staging gratuit indisponible | 4 | 3 | 12 | Démonstration locale |
| R-T11 | Faible performance import | 3 | 3 | 9 | Batch et index |
| R-T12 | Mauvais fuseau horaire | 3 | 4 | 12 | UTC + IANA |

---

# 7. Risques IoT et IA

| ID | Risque | P | I | Score | Réponse |
|---|---|---:|---:|---:|---|
| R-I01 | Pi non disponible | 3 | 3 | 9 | Simulateur Python |
| R-I02 | Événement MQTT rejoué | 3 | 5 | 15 | `eventId` et séquence |
| R-I03 | Usurpation de dispositif | 3 | 5 | 15 | Credential unique |
| R-I04 | Événements perdus hors ligne | 3 | 3 | 9 | File locale |
| R-I05 | Suggestion IA incorrecte | 4 | 4 | 16 | Score et validation humaine |
| R-I06 | IA bloque l’import | 3 | 4 | 12 | Mode manuel |
| R-I07 | Modèle non explicable | 3 | 3 | 9 | Raisons et règles |
| R-I08 | Données d’entraînement insuffisantes | 5 | 3 | 15 | Données synthétiques |

---

# 7bis. Risques du grand lot produit G1 (31 août 2026)

Risques propres au **grand lot produit G1** (planning, cycle de vie des
séances, notifications, tableaux de bord, pièces jointes, recette
globale). Décisions d'atténuation détaillées dans
`docs/reports/G1_ARCHITECTURE_DECISIONS.md`.

| ID | Risque | P | I | Score | Réponse |
|---|---|---:|---:|---:|---|
| R-G1-01 | Publication de planning partielle (séances créées sans version, ou l'inverse) | 3 | 5 | 15 | Transaction unique tout-ou-rien, verrou `SELECT … FOR UPDATE`, port `coursesession` synchrone, test de rollback total (DEC-G1-001, DEC-G1-003) |
| R-G1-02 | Duplication de séances à la (re)publication | 3 | 4 | 12 | Idempotence par `planning_entry.publicId` / `business_key`, contrainte unique, test de double publication (DEC-G1-002) |
| R-G1-03 | Deux publications concurrentes du même job | 3 | 4 | 12 | Verrou de ligne ; seconde publication → idempotente ou `409` métier ; jamais `500` ; test de concurrence (DEC-G1-003) |
| R-G1-04 | Séance `OPEN`/`CLOSED` réécrite par une nouvelle version de planning | 2 | 5 | 10 | Règle explicite : jamais de réécriture d'une séance `OPEN`/`CLOSED` ; supersession logique des seules séances `PLANNED` futures ; test par règle (DEC-G1-004) |
| R-G1-05 | Couplage `planning` ↔ `coursesession.internal` (violation Modulith) | 3 | 4 | 12 | Port public `PlanningSessionWriter` uniquement, commande immuable, aucun partage d'entité JPA, `ModularityTests` vert à chaque commit (DEC-G1-001) |
| R-G1-06 | Migration `planning` défectueuse / non rejouable | 2 | 5 | 10 | Migrations additives uniquement (`CREATE`/`ADD COLUMN` nullable), rejeu Flyway V1→Vn sur base vierge en test, `ddl-auto=validate` (DEC-G1-012) |
| R-G1-07 | Perte d'un événement de notification (crash entre commit métier et écriture) | 3 | 3 | 9 | Écriture après commit en `REQUIRES_NEW`, `dedup_key` unique, tâche de réconciliation optionnelle, acceptation documentée (DEC-G1-007) |
| R-G1-08 | Notification dupliquée | 3 | 2 | 6 | `dedup_key` = hachage (type, ressource, destinataire, événement) unique en base ; `DataIntegrityViolation` avalée (DEC-G1-007) |
| R-G1-09 | Un échec de notification annule (rollback) l'opération métier | 2 | 5 | 10 | **Traité (G1-D)** : `NotificationListener` en `@TransactionalEventListener(AFTER_COMMIT)` (hors transaction métier) → `NotificationWriter` → `NotificationRowWriter` `REQUIRES_NEW` **par ligne** ; tests `aRolledBackPlanningPublishedEventProducesNoNotification` et cycle réel. Une transaction métier qui rollbacke ⇒ 0 notification, phase `AFTER_COMMIT` jamais atteinte |
| R-G1-10 | Contenu sensible dans une notification (jeton, PII, IP, chemin, secret) | 3 | 5 | 15 | **Traité (G1-D)** : `title` / `body` construits par `NotificationListener` à partir d'informations déjà publiques (id / titre de séance, numéro de version) — le motif d'annulation (nominatif) est **exclu** ; DTO sans identifiant SQL (`id`, `recipient_user_id`, `dedup_key`) ; destinataires dérivés serveur ; test back « `body` sans motif » + front « never exposes a SQL identifier » |
| R-G1-11 | Incohérence base ↔ fichier pour une pièce jointe (fichier orphelin, ligne fantôme) | 3 | 3 | 9 | Séquence temporaire → validation → transaction DB `PENDING_STORAGE` → déplacement atomique → `STORED` ; compensation `@Scheduled` ; IHM ne montre que `STORED` (DEC-G1-009) |
| R-G1-12 | Traversal de chemin via le nom de fichier client | 2 | 5 | 10 | `storageKey` aléatoire jamais dérivé du nom client ; garde anti-`..` ; stockage hors webroot ; test dédié (DEC-G1-008) |
| R-G1-13 | Fichier malveillant / polyglotte accepté | 3 | 4 | 12 | Contrôle extension + MIME **+ magic bytes** ; rejet ZIP/OLE/exécutable ; MIME re-dérivé au téléchargement ; `Content-Disposition: attachment` + `nosniff` ; jamais de rendu HTML (DEC-G1-008, CDC §21.5) |
| R-G1-14 | Stockage de contenu sensible en base | 2 | 4 | 8 | Contenu **jamais** en base : métadonnées MySQL + fichier hors base via port (DEC-G1-008) |
| R-G1-15 | Volume disque non borné (pièces jointes) | 3 | 3 | 9 | Taille bornée (`JUSTIFICATION_MAX_FILE_BYTES`) ; dette de purge documentée (`docs/07-securite-rgpd.md`) ; suivi (DEC-G1-008) |
| R-G1-16 | N+1 Hibernate dans les tableaux de bord | 4 | 3 | 12 | **Partiellement traité (passe corrective F + 2e passe)** : N+1 **par classes** corrigé (port de lot `ClassGroupDirectory.findByPublicIds` ; mesure 1 classe → 14 requêtes, 15 classes → 14, croissance **nulle**). Coût **par séance affichée** **encore linéaire** (≈ 2 requêtes/séance : 1 séance → 10, 10 séances → 28 ; `toRef` hydrate points de contrôle + `session_class` **avant** le `trim` à 10) — borné *en pratique* par la fenêtre 7 j et l'affichage à 10, **pas** en requêtes au-delà. Ne jamais écrire « absence totale de N+1 » (`DEC-G1-010`, correction hors périmètre) |
| R-G1-17 | Régression sur les **811 tests back / 600 tests front** (totaux finaux mesurés au 2 septembre 2026 ; 693 / 475 au démarrage du lot) | 3 | 5 | 15 | Suite complète re-exécutée à chaque bloc (**et dans les trois modes de fuseau**), `ModularityTests` vert, aucun test supprimé / `@Disabled` / `continue-on-error` |
| R-G1-18 | Faux positifs de conflit à l'import (cours multi-classes) | 3 | 2 | 6 | Limite documentée (une ligne = une classe à l'import G1-B) ; contournement par `title` (DEC-G1-005) |
| R-G1-19 | Session de travail trop longue / limite de contexte | 4 | 3 | 12 | Blocs indépendants commités séparément ; `G1_IMPLEMENTATION_PROGRESS.md` mis à jour à chaque fin de bloc ; jamais démarrer un bloc non finançable |
| R-G1-20 | ~~Reprise nocturne : suite back rouge dans la fenêtre `00:00–02:00 CEST`~~ **RÉSOLU (checkpoint G1-0.1, 1er sept. 2026)** | 1 | 2 | 2 | `AttendanceService.validate` / `AttendanceJustificationService` décident désormais la couverture d'inscription à la **date civile de la séance** (`startsAt` projeté dans son fuseau persisté), plus à « aujourd'hui en UTC » ; `AttendanceServiceSessionDateTests` (horloge figée) ; suite back **693 / 0 dans les trois modes de fuseau**. Détail : `G1_IMPLEMENTATION_PROGRESS.md` §9 + « Correctif G1-0.1 ». `ClockConfig` inchangé |
| R-G1-21 | Absence de test e2e navigateur | 3 | 3 | 9 | **Fermé le 03/09/2026.** État au 31/08 : assumé (`DEC-G1-011`), Playwright non retenu pour coût disproportionné, aucune tentative d'installation. La suite a finalement été construite pendant l'audit QA : 149 tests Playwright / Chromium (`tests/`, `audit-report.md` §4). `DEC-G1-011` est **révisée**, pas contournée : la suite est conservée en **complément** de la recette d'intégration API, jamais en remplacement — la recette API ne pilote aucun navigateur et ne rend aucun composant Angular. Risque résiduel suivi en **R-QA-06** (§7ter) : coût d'exploitation de la suite et instabilité d'environnement observée |
| R-G1-22 | Déploiement futur avec stockage éphémère (pièces jointes perdues) | 3 | 4 | 12 | Port de stockage abstrait ⇒ adaptateur objet S3-compatible substituable sans toucher au métier ; volume persistant identifié dans le rapport final (DEC-G1-008) |
| R-G1-23 | Migration destructive impossible à rollback automatiquement | 1 | 5 | 5 | Aucune migration G1 n'est destructive (toutes additives) ; règle explicite (DEC-G1-012) |
| R-G1-24 | Documentation en avance sur le code (statut « livré » sans preuve) | 3 | 5 | 15 | Statut porté à `IMPLEMENTED_AND_TESTED` uniquement si code présent + test exécuté + résultat consigné ; statuts ambigus interdits |
| R-G1-25 | Audit de succès committé alors que la transaction métier rollbacke (`coursesession`) | 3 | 3 | 9 | **Corrigé au checkpoint G1-C.3** : `CourseSessionAuditListener` migré vers `@TransactionalEventListener(AFTER_COMMIT)` + `CourseSessionAuditWriter` (`REQUIRES_NEW`) ; test à faute injectée prouve « rollback métier ⇒ 0 ligne `SESSION_CANCELLED` ». Les 8 autres listeners d'audit restent en dette assumée (hors périmètre G1-C) |
| R-G1-26 | Effet Redis (purge de jetons) exécuté puis non compensé si la transaction rollbacke | 2 | 3 | 6 | **Corrigé au checkpoint G1-C.3** : `CourseSessionCloseListener` / `AttendanceCheckpointCloseListener` migrés vers `AFTER_COMMIT` — la purge n'a lieu qu'après commit réussi ; défense en profondeur inchangée (émargement bloqué dès le point de contrôle fermé) |
| R-G1-27 | Doc trompeuse : « `flyway repair` suffit à rattraper une base ayant appliqué l'ancienne forme de V12/V13 » | 3 | 3 | 9 | **Corrigé au checkpoint G1-C.3** : `flyway repair` ne modifie que l'historique / les sommes de contrôle, jamais le schéma ; une telle base doit être **recréée** ou corrigée par une **migration SQL corrective explicite**. Décision initiale conservée, correction datée ajoutée (en-tête de `V13`, `G1_IMPLEMENTATION_PROGRESS.md`, `docs/10-journal-ia.md`, `docs/CURRENT-STATE.md`) |
| R-G1-28 | Notifications limitées aux formateurs : un apprenant / responsable pédagogique n'est pas informé d'une séance annulée ou d'un planning publié (CDC §13.9, §23.2) | 3 | 3 | 9 | **Assumé (audit G1-D.1)** : `EF-NOTIF-002` / `RG-033` reclassées **`PARTIAL`** ; aucune audience inventée ; dette **G1-D-AUDIENCE** (`docs/05` §9bis) — ports publics `enrollment` / `academic` à ajouter, déduplication de destinataires testée. Le prototype ne présente pas la notification apprenant comme livrée |
| R-G1-29 | Perte d'une notification si la JVM meurt entre le commit métier et l'écriture `AFTER_COMMIT` (pas d'outbox) | 3 | 3 | 9 | **Assumé (DEC-G1-007, audit G1-D.1)** : livraison « au mieux » ; CDC §18.3 / §23.3 autorisent file + DLQ comme cible non implémentée ; `dedup_key` UNIQUE garantit l'absence de doublon si une reprise est ajoutée ; dette **G1-D-OUTBOX** (`docs/05` §9bis) avec critères de résolution. Testé : un échec complet du writer ne casse pas la mutation métier |
| R-G1-30 | Table `notification` sans borne de croissance ni politique de conservation | 3 | 2 | 6 | **Assumé (audit G1-D.1)** : aucune durée documentaire (MDD §23.1 ne fixe rien) ; **aucune purge inventée** ; `docs/07` §14 = rétention notifications `À_DÉFINIR` ; conformité RGPD non revendiquée sur ce point ; dette **G1-D-RETENTION** (`docs/05` §9bis) |
| R-G1-31 | Pièce jointe de justificatif porteuse d'un malware non détecté | 3 | 4 | 12 | **Assumé (G1-E `DEC-G1-E-ANTIVIRUS`)** : aucun moteur antivirus dans l'architecture ⇒ contrôle **structurel** livré (extension + type déclaré + magic bytes + rejet ZIP/OLE2 + cohérence extension↔contenu + taille + anti-traversal) ; l'antivirus reste une abstraction à ajouter (`FileSafetyScanner`) ; on n'écrit **jamais** « fichier garanti sans malware » ; téléchargement futur en `Content-Disposition: attachment` + `nosniff`, jamais de rendu HTML |
| R-G1-32 | Fichier orphelin ou métadonnée fantôme (base ↔ système de fichiers non transactionnels) | 3 | 3 | 9 | **Partiellement traité, partiellement assumé (G1-E, `DEC-G1-009`)** : séquence livrée (validation → ligne `PENDING_STORAGE` committée → déplacement atomique → vérification empreinte/taille → bascule `STORED`), **compensation** immédiate sur échec, **réconciliation `@Scheduled`** bornée des lignes `PENDING_STORAGE` (promotion `STORED` / déclassement `DELETED`) ; l'API ne renvoie jamais une pièce `PENDING_STORAGE`. **Non traité : le balayage des fichiers orphelins** (ligne `DELETED` dont la suppression best-effort du fichier a échoué) = **`NOT_IMPLEMENTED`** — un scan de répertoire sûr (liens symboliques, traversée, TOCTOU) a été jugé disproportionné ; test de figure de la portée `reconciliationDoesNotSweepAFileOrphanedByADeletedRow`. Stratégie future : job dédié borné, journalisation puis quarantaine avant purge |

| R-G1-33 | Trace d'audit manquante après un dépôt de pièce jointe réussi | 2 | 2 | 4 | **Assumé (passe corrective A)** : l'échec de la publication d'audit **après** le commit `STORED` est **isolé** — l'API répond `201`, la pièce reste durable et téléchargeable, l'échec est **journalisé** (WARN, sans PII). La trace n'est **pas rejouée** : dette d'audit cohérente avec les 8 listeners synchrones restants. Preuve directe : `AttendanceJustificationServiceAttachmentAuditIsolationTests` (unité Mockito) + test d'intégration HTTP |
| R-G1-34 | Trace d'audit de succès committée alors que la transaction métier rollbacke (8 modules restants) | 3 | 3 | 9 | **Ouvert — dette assumée** : seuls `coursesession` et `studentimport` publient en `AFTER_COMMIT`. Les 8 autres listeners restent `@EventListener` synchrones `REQUIRES_NEW`. Résolution cible : migration globale `AFTER_COMMIT` + **outbox d'audit** (`G1_FINAL_REPORT.md` §12) |
| R-G1-35 | Dépôt de pièce jointe impossible hors Docker (`/data` non inscriptible) | 4 | 2 | 8 | **Traité par la documentation** : `JUSTIFICATION_STORAGE_PATH` vaut par défaut `${UPLOAD_DIRECTORY:-/data/uploads}/justifications`, chemin inexistant sur un poste macOS / Linux ⇒ `503 ATT_ATTACHMENT_STORAGE_FAILED`. `README.md` (« Lancement » §2 et « Dépannage ») impose l'export d'un répertoire local inscriptible avant `spring-boot:run` |
| R-G1-36 | Absence de rate-limiting sur `/auth/login` | 3 | 4 | 12 | **Ouvert — dette assumée (`docs/07` §5)** : un limiteur *fail-safe*, sans énumération de comptes et testé, dépasse le périmètre livré. Atténuations en place : refus **uniforme** quel que soit le motif d'échec, hachage BCrypt, aucun message distinguant email inconnu / mot de passe faux / compte inactif |
| R-G1-37 | Échec intermittent d'un test d'intégration sous `TZ=UTC` | 2 | 2 | 4 | **Observé une seule fois** (`EnrollmentDirectoryTests`, 1re passe corrective G1), **non reproduit** en 5 répétitions isolées ni sur les runs complets ⇒ **cause non déterminée**. Mécanisme *plausible* (non prouvé) : contention du pool HikariCP partagé entre contextes `@SpringBootTest` (`TEST_ISOLATION_DECISION.md`). Ne pas qualifier de « problème d'infrastructure confirmé » |
| R-G1-38 | Parcours jamais démontré manuellement, aucune capture d'écran dans le dépôt | 4 | 4 | 16 | **Ouvert** — c'est le seul point qui maintient le Groupe 1 en `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`. Atténuation : recette d'intégration API rejouant le parcours complet ; `docs/11-guide-demonstration.md` §11-§13 (scénario, checklist jury, matrice preuve) ; plan de secours documenté. **Action requise avant soutenance** : exécuter le parcours UI, produire les captures, consigner le résultat |

---

# 7ter. Risques relevés par l'audit QA indépendant (3 septembre 2026)

Source : `audit-report.md`. Cotation identique au §1
(probabilité × impact). Ces cinq entrées ne remplacent aucun risque
existant : elles constatent l'état vérifié en pilotant l'application.

| Réf | Risque | P | I | C | État au 3 septembre 2026 |
|---|---|:-:|:-:|:-:|---|
| R-QA-01 | Base applicative `esic_connect` polluée par ~27 000 comptes de fixtures de test : aucune démonstration crédible, aucun identifiant connu | 5 | 4 | 20 | **Outillé, non exécuté.** `scripts/db-doctor.sh` (diagnostic, code 2) et `scripts/db-reset.sh` (sauvegarde → recréation → Flyway → contrôle) sont livrés. La base **reste polluée** tant que le script n'a pas été lancé. Cause probable traitée en amont : `MYSQL_TEST_DATABASE` désormais explicite (`.env.example`, `docs/13` §2) |
| R-QA-02 | Contradiction documentaire sur le périmètre du planning : addendum « hors périmètre » du 31 août contre module livré le 1er septembre | 5 | 3 | 15 | **Fermé.** Tranché en faveur du périmètre livré ; `docs/01` §23.6 et `docs/02` §4.5.2 remplacent les addendums F2, marqués caducs. Aucune suppression d'historique |
| R-QA-03 | Un paramètre de requête obligatoire absent produisait un `500` au lieu d'un `400` : fausse la supervision et trompe tout client de l'API documentée | 3 | 2 | 6 | **Fermé.** `GlobalExceptionHandler` traite désormais 4 familles d'erreurs d'appel client en `400 VALIDATION_ERROR`. Garde-fous : `PlanningImportIntegrationTests` + `tests/09-security-edge-cases.spec.ts` |
| R-QA-04 | Lien d'évitement absent des pages publiques : repère `#main-content` inatteignable au clavier | 2 | 2 | 4 | **Fermé.** Composant partagé `core/a11y/skip-link` utilisé par la coquille **et** les 4 pages publiques ; garde-fou e2e |
| R-QA-06 | Coût d'exploitation de la suite e2e et instabilité d'environnement : sur 7 exécutions complètes, un test **aléatoire** (jamais le même) s'est bloqué 7 à 16 min avant timeout, corrélé à une charge système croissante, non reproductible isolément | 3 | 2 | 6 | **Sous surveillance.** Ni défaut applicatif ni défaut de test (chacun réussi en < 2 s sur ≥ 5 exécutions). Atténuations : `retries: 1` déjà actif quand `CI` est défini, workflow `e2e.yml` à déclenchement **manuel** pour ne pas fragiliser chaque PR, machine sans charge concurrente recommandée. C'est exactement le coût que `DEC-G1-011` anticipait : il est désormais mesuré, pas supposé |
| R-QA-05 | Aucune persistance de session : tout rechargement de page déconnecte, y compris pendant une démonstration | 4 | 3 | 12 | **Ouvert — assumé.** Choix de sécurité du prototype (JWT en mémoire seule, RG-085) ; le rafraîchissement de jeton est classé `SOUHAITÉ` (`docs/02` §23.4). Atténuations : redirection `?redirect=` après reconnexion, avertissement explicite en tête du guide de démonstration (`docs/11` §11bis.1) |

> **R-G1-38 est reclassé.** Le parcours prioritaire est désormais rejoué
> de bout en bout dans un **vrai navigateur** (149 tests Playwright,
> captures dans `captures/`). Ce qui reste ouvert dans R-G1-38 est
> strictement la **manipulation humaine** consignée : un navigateur
> piloté par script n'en est pas une.

---

# 8. Suivi

Chaque risque possède un état :

- `OUVERT` ;
- `SOUS_SURVEILLANCE` ;
- `TRAITÉ` ;
- `ACCEPTÉ` ;
- `FERMÉ`.

Les risques critiques doivent être revus :

- pendant le Sprint Planning ;
- pendant le point d’avancement ;
- avant toute démonstration ;
- avant un déploiement.

---

# 9. Plan de continuité minimal

En cas de panne pendant la soutenance :

1. basculer sur l’environnement local ;
2. utiliser les données préchargées ;
3. montrer les captures ;
4. lire les journaux ;
5. lancer la vidéo ;
6. expliquer le mode dégradé ;
7. distinguer la panne de l’architecture conçue.