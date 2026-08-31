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
| R-G1-09 | Un échec de notification annule (rollback) l'opération métier | 2 | 5 | 10 | Transaction notification indépendante (`REQUIRES_NEW`, `AFTER_COMMIT`) ; test « rollback métier ⇒ 0 notification » et « échec notification ⇒ métier intact » (DEC-G1-007) |
| R-G1-10 | Contenu sensible dans une notification (jeton, PII, IP, chemin, secret) | 3 | 5 | 15 | Corps neutre normé, revue, test de contenu ; destinataires dérivés serveur (DEC-G1-007) |
| R-G1-11 | Incohérence base ↔ fichier pour une pièce jointe (fichier orphelin, ligne fantôme) | 3 | 3 | 9 | Séquence temporaire → validation → transaction DB `PENDING_STORAGE` → déplacement atomique → `STORED` ; compensation `@Scheduled` ; IHM ne montre que `STORED` (DEC-G1-009) |
| R-G1-12 | Traversal de chemin via le nom de fichier client | 2 | 5 | 10 | `storageKey` aléatoire jamais dérivé du nom client ; garde anti-`..` ; stockage hors webroot ; test dédié (DEC-G1-008) |
| R-G1-13 | Fichier malveillant / polyglotte accepté | 3 | 4 | 12 | Contrôle extension + MIME **+ magic bytes** ; rejet ZIP/OLE/exécutable ; MIME re-dérivé au téléchargement ; `Content-Disposition: attachment` + `nosniff` ; jamais de rendu HTML (DEC-G1-008, CDC §21.5) |
| R-G1-14 | Stockage de contenu sensible en base | 2 | 4 | 8 | Contenu **jamais** en base : métadonnées MySQL + fichier hors base via port (DEC-G1-008) |
| R-G1-15 | Volume disque non borné (pièces jointes) | 3 | 3 | 9 | Taille bornée (`JUSTIFICATION_MAX_FILE_BYTES`) ; dette de purge documentée (`docs/07-securite-rgpd.md`) ; suivi (DEC-G1-008) |
| R-G1-16 | N+1 Hibernate dans les tableaux de bord | 4 | 3 | 12 | Requêtes agrégat dédiées bornées, jamais de collections JPA ; test compteur de requêtes sur ≥ 1 endpoint (DEC-G1-010) |
| R-G1-17 | Régression sur les 686 tests back / 475 tests front | 3 | 5 | 15 | Suite complète re-exécutée à chaque bloc, `ModularityTests` vert, aucun test supprimé / `@Disabled` / `continue-on-error` |
| R-G1-18 | Faux positifs de conflit à l'import (cours multi-classes) | 3 | 2 | 6 | Limite documentée (une ligne = une classe à l'import G1-B) ; contournement par `title` (DEC-G1-005) |
| R-G1-19 | Session de travail trop longue / limite de contexte | 4 | 3 | 12 | Blocs indépendants commités séparément ; `G1_IMPLEMENTATION_PROGRESS.md` mis à jour à chaque fin de bloc ; jamais démarrer un bloc non finançable |
| R-G1-20 | Reprise nocturne : suite back rouge dans la fenêtre `00:00–02:00 CEST` | 4 | 2 | 8 | Bug latent de fuseau (`EnrollmentService` zone système vs `AttendanceService` UTC) — **hors périmètre G1** ; contournement `TZ=UTC` (comme la CI) ; se résout de lui-même ; consigné dans `G1_IMPLEMENTATION_PROGRESS.md` §9 |
| R-G1-21 | e2e Playwright incompatible / vulnérable / navigateur non téléchargeable | 3 | 2 | 6 | Vérif compat + `npm audit` avant ajout ; repli = démonstration API automatisée ; statut alors `PARTIAL`, jamais « e2e livré » (DEC-G1-011) |
| R-G1-22 | Déploiement futur avec stockage éphémère (pièces jointes perdues) | 3 | 4 | 12 | Port de stockage abstrait ⇒ adaptateur objet S3-compatible substituable sans toucher au métier ; volume persistant identifié dans le rapport final (DEC-G1-008) |
| R-G1-23 | Migration destructive impossible à rollback automatiquement | 1 | 5 | 5 | Aucune migration G1 n'est destructive (toutes additives) ; règle explicite (DEC-G1-012) |
| R-G1-24 | Documentation en avance sur le code (statut « livré » sans preuve) | 3 | 5 | 15 | Statut porté à `IMPLEMENTED_AND_TESTED` uniquement si code présent + test exécuté + résultat consigné ; statuts ambigus interdits |

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