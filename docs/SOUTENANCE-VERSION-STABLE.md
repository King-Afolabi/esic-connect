# ESIC Connect — Dossier de soutenance (version stable candidate)

| Élément | Valeur |
|---|---|
| Produit | ESIC Connect — plateforme de gestion pédagogique, d'émargement et d'assiduité |
| Porteur | Abubacar AFOLABI |
| Branche | `feat/demo-readiness-e2e-ui` |
| SHA | `6a864a9` (voir `git log`) |
| Date | 9 septembre 2026 (passe de correction : apprenants responsable/formateur, changement de mot de passe, ANO-UX-007, recette e2e rejouée) |
| Environnement de démonstration | Raspberry Pi + tunnel Cloudflare — `https://drivers-revenues-alloy-guarantee.trycloudflare.com` (URL éphémère, à revérifier au démarrage) |

Ce document accompagne la soutenance. L'état d'avancement détaillé et
prouvé se trouve dans `docs/CURRENT-STATE.md` (source de vérité). L'audit
complet de la passe de stabilisation : `docs/audit/STABLE-RELEASE-AUDIT-2026-09-08.md`
(non versionné — fourni séparément).

---

## 1. Le produit

### 1.1 Problème résolu

Avant ESIC Connect, l'ESIC gère l'assiduité au tableur et sur feuilles
papier : ressaisies, doublons, pertes de documents, consolidation lente,
traçabilité insuffisante des corrections, difficulté à prouver une
présence, comptes non activés faute d'adresses valides, remplacements mal
communiqués, aucune détection d'anomalie.

ESIC Connect est le **système d'information unique** qui couvre, sans
rupture, le cycle **intégration d'un apprenant → attestation
d'assiduité**, en garantissant la fiabilité de la présence, la
traçabilité des décisions et la protection des données.

### 1.2 Acteurs et rôles

| Rôle | Vocation | Second facteur |
|---|---|---|
| `SUPER_ADMIN` | contrôle technique global, dispositifs, politiques de sécurité | obligatoire |
| `ADMIN` | administration fonctionnelle, utilisateurs, référentiels, invitations | obligatoire |
| `SCHOOL_ADMINISTRATION` | assiduité, justificatifs, réclamations, rapports, attestations | recommandé |
| `PEDAGOGICAL_MANAGER` | propriétaire de son périmètre : classes, imports, plannings, séances | obligatoire sur opérations sensibles |
| `TEACHER` | séances, émargement, présences, réclamations de ses séances | facultatif |
| `STUDENT` | planning, émargement, assiduité, justificatifs, réclamations | adaptatif |

Le **cumul de rôles** est natif et n'élargit jamais un périmètre : un
responsable également formateur ne voit pas les formations d'un autre
responsable. Le contexte d'usage est choisi dans l'interface et
**vérifié côté serveur** contre les autorités réellement détenues.

### 1.3 Chaîne de valeur couverte

```
Référentiels (formations, niveaux, promotions, classes, matières, salles,
              plages réseau, rythmes d'alternance)
   │
   ▼
Import des apprenants (CSV / Excel / classeur multifeuille, simulation
   puis confirmation atomique)  →  Invitation (jeton, délivrabilité,
   réémission)  →  Activation (mot de passe + passkey optionnelle)
   │
   ▼
Planning (import CSV/Excel/PDF texte, ou construction au calendrier ;
   détection de conflits ; correction ligne à ligne ; versionnement ;
   publication atomique et idempotente)
   │
   ▼
Séances (création depuis planning publié ou exceptionnelle ; ouverture ;
   clôture ; annulation ; report ; remplacement daté)
   │
   ▼
Émargement — 5 canaux, mêmes contrôles serveur :
   • QR dynamique du formateur (jeton rotatif 10 s, anti-rejeu)
   • Code court (même durée de vie, distanciel / caméra indisponible)
   • QR fixe de salle (plage réseau obligatoire, refusé après le début)
   • Borne connectée MQTT ......................... (perspective, sprint 12)
   • Saisie manuelle motivée et auditée
   Scanner QR intégré à l'application ; fondations NFC (même URL opaque).
   │
   ▼
Assiduité (4 points de contrôle nommés → demi-journées → journées ;
   paliers de retard 15/30 min ; alternance jamais comptée absente ;
   corrections append-only auditées ; départ anticipé)
   │
   ▼
Justificatifs (dépôt, pièce jointe contrôlée, décision motivée,
   ABSENT → EXCUSED)  ·  Réclamations (fil conversationnel, transfert,
   réouverture, historique complet)
   │
   ▼
Restitution (tableaux de bord par rôle ; rapports journalier / mensuel /
   annuel / individuel ; exports CSV / Excel / PDF ; attestation
   d'assiduité à identifiant vérifiable ; recherche globale ;
   consultation et export de la piste d'audit)
```

---

## 2. Fonctionnalités livrées (par domaine)

> Détail et preuves : `docs/CURRENT-STATE.md` §2. Couverture globale :
> **112 exigences livrées et testées / 5 partielles / 25 en perspective**
> (sur 142).

### Identité et accès — complet (15/15)

Connexion email + mot de passe (réponse uniforme) ; multi-rôles et
contexte d'usage vérifié serveur ; invitation / activation ;
**mot de passe oublié** (réponse neutre, jeton usage unique) ;
**passkeys WebAuthn** (le serveur ne reçoit qu'une clé publique et une
signature — aucune donnée biométrique) ; **second facteur TOTP**
(secret chiffré AES-256-GCM, anti-rejeu, 10 codes de récupération) ;
authentification adaptative ; **anti-robot Turnstile** (validation
serveur) ; **limitation de débit** Redis ; appareils de confiance ;
déconnexion et révocation de session ; **rétablissement de session au
rechargement** via cookie de renouvellement `HttpOnly` `SameSite=Strict`
rotatif ; **changement de mot de passe self-service** depuis « Sécurité
de mon compte » (tout rôle ; ne modifie que son propre compte ; mot de
passe actuel vérifié + politique côté serveur ; au succès, toutes les
sessions du compte sont fermées et l'ancien mot de passe devient
inutilisable).

### Référentiels et organisation — 12/13

Formations, niveaux, années, promotions, classes, matières, groupes
temporaires, affectations pédagogiques (contrôle de périmètre serveur) ;
sites, bâtiments, salles, **plages réseau CIDR IPv4/IPv6** ;
**QR fixe de salle** : génération serveur, renouvellement, révocation,
**affiche imprimable**, **matrice de rôles** (consultation/impression
pour `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` ; renouvellement pour
`ADMIN` seul) ; rythmes d'alternance et exceptions.
*Non livré* : détection de conflits de salle **hors** planning
(`EF-ORG-004`).

### Apprenants et imports — 9/10

Profils, inscriptions historisées, changement de classe conservant
l'historique, suivi à distance individuel ; import CSV **et Excel**,
classeur **multifeuille**, simulation sans écriture puis confirmation
**atomique**, correction de ligne avant confirmation, numéro étudiant
`ESIC-{année}-{NNNNN}` (auto ou saisi), recherche de la liste par nom.
La **liste des apprenants** est ouverte en **lecture** au
`PEDAGOGICAL_MANAGER` (apprenants inscrits dans les classes de ses
formations) et au `TEACHER` (apprenants des classes rattachées à ses
séances) — **périmètre résolu côté serveur**, jamais d'un paramètre
client ; une fiche hors périmètre renvoie `404`. Les écritures (création
de profil, inscription, transfert) restent réservées à l'administration.
*Non livré* : mapping de colonnes assisté par IA (`EF-IMP-005`).

### Planning — 11/13

Import CSV/Excel, prévisualisation sans création de séance, correction
ligne à ligne, **publication atomique versionnée** (≥ 3 versions, retour
arrière), **détection de conflits** formateur/classe/salle/horaire,
avertissement d'alternance, **calendrier interactif**.
*Non livré* : PDF texte + mapping IA (`EF-PLAN-012/013`).

### Séances — complet (9/9)

Cycle strict `PLANNED → OPEN → CLOSED`, annulation motivée, report lié,
demande d'annulation par le formateur (décidée par le responsable),
remplacement daté, séance multi-classes.

### Émargement et assiduité — 14/16

QR dynamique rotatif + code court, validation par jeton (anti-rejeu),
**4 points de contrôle nommés**, calcul demi-journées / journées,
paliers de retard, présence manuelle motivée, apprenant provisoire,
**contrôle de plage réseau**, QR fixe de salle, correction auditée,
départ anticipé, journal de transparence, suivi en direct,
**scanner QR intégré**.
*Non livré* : confirmation locale WebAuthn (`EF-ATT-011`), borne MQTT
(`EF-ATT-016`).

### Justificatifs et réclamations — complet (8/8)

Dépôt, **pièce jointe contrôlée** (extension + MIME + magic bytes,
stockage hors base/hors webroot, `Content-Disposition: attachment`),
**analyse antivirus** (ClamAV activable — inactive par défaut, jamais
présentée comme saine), décision motivée `ABSENT → EXCUSED` ;
réclamations à guichet, fil append-only, transfert, réouverture.

### Notifications — 7/9

Centre de notifications persistant, audience résolue **après commit**
(formateur, remplaçant, apprenants, responsable de périmètre), courriel,
préférences par catégorie et canal, **outbox transactionnelle** (reprise,
file d'échec, rejeu manuel).
*Partiel* : push réel (`EF-NOTIF-005` — chiffrement RFC 8291 vérifié
contre le vecteur officiel, aucun service réel sollicité).

### Restitution — 9/10

Rapports classe / individuel, **exports CSV / Excel / PDF** (injection de
formule neutralisée), **attestation d'assiduité** à identifiant
vérifiable, tableaux de bord des 4 profils avec **tableau équivalent** à
chaque graphique, recherche globale, **consultation et export de la piste
d'audit** (aucune route d'écriture — audit inviolable), rapport des
invitations non activées.
**ANO-UX-007 corrigé** (9 sept., `03c95ef`) : le rollup d'assiduité des
rapports et du tableau de bord regroupe désormais **par (inscription,
jour)** et déduplique les points de contrôle par type, exactement comme
le rapport journalier canonique — un jour d'alternance `SCHOOL` sans
séance publiée ne gonfle plus le dénominateur. Aucune règle de gestion
changée.
*Non livré* : rapport des anomalies d'émargement (`EF-REP-009`).

### PWA — installable, hors ligne partiel

Application installable ; consultation hors ligne (planning, assiduité,
notifications) **application ouverte** ; file d'actions différées avec
état « en attente de confirmation ».
*Partiel* : démarrage à froid hors ligne (`EF-PWA-002` — le jeton ne vit
qu'en mémoire, RG-093).

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Navigateur / PWA installable (Angular 21 standalone, zoneless,  │
│  signaux, Angular Material)   — jeton d'accès EN MÉMOIRE SEULE   │
└───────────────┬─────────────────────────────────────────────────┘
                │ HTTPS (tunnel Cloudflare, aucun port entrant)
        ┌───────▼────────┐
        │  Nginx (SPA +  │  reverse-proxy /api, en-têtes de sécurité
        │  reverse proxy)│
        └───────┬────────┘
                │ /api  (réseau Docker interne)
   ┌────────────▼─────────────────────────────────────────────┐
   │  Spring Boot 3.5 — MONOLITHE MODULAIRE (Spring Modulith)  │
   │  19 modules isolés, 0 cycle, communication par port       │
   │  public ou événement — aucune entité JPA partagée         │
   │                                                          │
   │  identity · organization · academic · enrollment ·        │
   │  alternation · planning · coursesession · attendance ·    │
   │  studentimport · notification · dashboard · claim ·       │
   │  outbox · document · search · integration · audit ·       │
   │  bootstrap · shared                                       │
   │                                                          │
   │  Tout effet de bord externe (courriel, notification,      │
   │  audit, MQTT) → OUTBOX TRANSACTIONNELLE                   │
   └──────┬──────────────────────────┬───────────────────────┘
          │                          │
   ┌──────▼──────┐            ┌──────▼──────┐
   │  MySQL 8    │            │  Redis 7    │  jetons, cache,
   │  (Flyway,   │            │  (TEMPORAIRE│  compteurs de débit,
   │  source de  │            │  uniquement)│  révocation
   │  vérité,    │            └─────────────┘  — jamais source de vérité
   │  V34)       │
   └─────────────┘

  Docker Compose sur Raspberry Pi 4 (aarch64) :
  mysql · redis · backend · frontend · cloudflared
```

**Choix assumés** :

- **Monolithe modulaire**, pas de microservices : cohésion
  transactionnelle nécessaire à l'émargement et à la publication, sans
  le coût opérationnel d'une constellation de services à cette échelle.
- **Pas de Kubernetes** : inadapté à la volumétrie ; conteneurs +
  service managé suffisent.
- **Deux services séparés** seulement, prévus : l'application Spring Boot
  et le futur service d'IA Python (écosystème et cycle de vie distincts).
- **Intégrations externes derrière un port** : le produit fonctionne
  intégralement sans Microsoft Graph, sans service de push, sans SMTP
  réel — avec un adaptateur local.

---

## 4. Sécurité

| Contrôle | Mise en œuvre |
|---|---|
| Autorisation | 4 niveaux — route (`@PreAuthorize`), service métier, ressource, périmètre pédagogique. **Jamais** décidée par l'affichage Angular. Refus par défaut. `404` plutôt que `403` quand l'existence est sensible. |
| MFA | TOTP obligatoire `SUPER_ADMIN`/`ADMIN` ; adaptatif apprenant ; secret chiffré AES-256-GCM. |
| WebAuthn | `webauthn4j` ; le serveur ne reçoit qu'une clé publique et une signature — **aucune donnée biométrique reçue ni stockée**. |
| Sessions | JWT HS256, `iss` vérifié, 401 nu ; révocation par `jti` + `credentials_invalidated_at` ; cookie de renouvellement `HttpOnly` `SameSite=Strict` rotatif avec détection de rejeu. |
| QR opaque | jeton aléatoire, temporaire, non prédictible, révocable, anti-rejeu — **aucune donnée personnelle dans le QR**. |
| Contrôle réseau | plage CIDR autorisée vérifiée **avant** toute autre décision ; l'adresse IP sert à décider puis disparaît — jamais persistée, jamais auditée. |
| Audit | via **outbox transactionnelle** : jamais perdu, jamais produit si la transaction est annulée ; `audit_event` immuable (aucune route d'écriture) ; sans mot de passe, jeton complet, biométrie **ni adresse IP**. |
| Fichiers | extension + MIME + magic bytes ; hors base et hors webroot ; téléchargement forcé `attachment` + `nosniff`. |
| Antivirus | port + adaptateur ClamAV activable ; **inactif par défaut** — une pièce non analysée n'est jamais présentée comme saine. |
| Secrets | hors dépôt (scan négatif) ; `.env` en `600` ; `JWT_SECRET` ≥ 32 o imposé au démarrage. |
| En-têtes | back-end : CSP stricte (script-src self + Turnstile), `Referrer-Policy: no-referrer`, nosniff, `X-Frame-Options: DENY`, HSTS. Front-end : nosniff / frame-options / referrer-policy / `Permissions-Policy` sur toutes les réponses Nginx (corrigé le 9 sept.). |
| Exposition réseau | aucun port entrant ; back-end sur `127.0.0.1` seul ; MySQL/Redis non publiés ; `cloudflared` sortant. Adminer : `127.0.0.1` + tunnel SSH uniquement. |

**Limites connues et assumées** : pas de CSP sur le SPA lui-même
(recommandée, exige une validation navigateur) ; Swagger exposé en
recette (routes protégées) ; pentest externe **non réalisé** ; test de
restauration de sauvegarde **non exécuté**.

---

## 5. Résultats mesurés (9 septembre 2026)

| Contrôle | Résultat |
|---|---|
| Frontend — lint | All files pass linting |
| Frontend — tests unitaires | **107 fichiers / 935 tests / 0 échec** |
| Frontend — build production | OK, aucune alerte de budget (initial ≈ 590 kio) |
| Frontend — typecheck e2e (`tsc --noEmit`) | 0 erreur |
| Frontend — `npm audit` | 0 vulnérabilité |
| Backend — `clean test-compile` | BUILD SUCCESS |
| Backend — `ModularityTests` | 19 modules, 0 cycle |
| Backend — suite complète (`./mvnw -o clean test`) | **1275 tests / 0 échec / 0 erreur** — `BUILD SUCCESS` (schéma V34, aucune migration) ; +15 vs 1260 : `RosterScopeIntegrationTests` (4), `PasswordChangeIntegrationTests` (7), `AttendanceRollupHalfDayIntegrationTests` (3), `EnrollmentSecurityTests` (+1) |
| Recette navigateur Playwright | **202 passés / 0 échoué / 0 non exécuté** (33,2 min) — suite complète **rejouée après les correctifs du 9 septembre** ; captures : `artifacts/stable-release-2026-09-08/` |
| Accessibilité (axe WCAG 2.1 AA) | dernier résultat **20/20**, 0 violation critique/sérieuse |
| Performance (Pi) | rapport mensuel de classe **1,9 s à chaud** (cible < 2 s) ; dashboard responsable 2,2–2,7 s à chaud |
| Migrations | V1 → V34 validées sur base vierge, 64 tables |
| Déploiement | Raspberry Pi, `compose.prod.yaml`, Quick Tunnel — 5/5 conteneurs `healthy` |

---

## 6. Limites et perspectives

Présentées comme **prochaines étapes**, pas comme des manques :

| Brique | État | Sprint cible |
|---|---|---|
| Service d'IA (mapping d'import, détection d'anomalies, décrochage) | non implémenté ; garde-fou de conception : l'IA n'est jamais décideur | 12 |
| Borne d'émargement IoT / MQTT + simulateur | non implémenté | 12 |
| Notifications push réelles | chiffrement RFC 8291 vérifié, aucun service réel sollicité | 12–13 |
| Microsoft Graph / Teams réel | port + adaptateurs écrits et testés, aucun locataire réel | 11–13 |
| Fournisseur email réel (Brevo) | configuré ; remise en boîte non prouvée (expéditeur à valider côté Brevo) | 13 |
| RGPD avancé (export, rectification, purge/anonymisation) | non implémenté | 13 |
| Exploitation complète (métriques, journaux structurés, CI/CD de bout en bout) | partiel (santé + Dependabot + CI de test) | 13 |
| Test de restauration de sauvegarde | procédure prête, non exécutée | 13 |
| Fusion réelle des doublons | comparaison en lecture seule livrée ; fusion volontairement non implémentée (décision porteur) | à décider |

---

## 7. Parcours démontrables (données fictives)

1. `ADMIN` crée formation + niveau + année ; `PEDAGOGICAL_MANAGER` crée
   promotion + classe.
2. Import CSV de 500 apprenants → simulation chiffrée → confirmation →
   comptes créés + invitations parties.
3. Un apprenant active son compte et enregistre une passkey.
4. Import d'un planning, correction d'une ligne en anomalie, publication
   → séances créées et notifiées.
5. Le formateur ouvre sa séance → QR dynamique + code court affichés.
6. Un apprenant émarge en **scannant le QR dans l'application**, un autre
   par **code court**, un troisième est **saisi manuellement** avec motif.
7. Présences en direct → le formateur clôture.
8. Dépôt d'un justificatif avec pièce jointe → acceptation → `EXCUSED`.
9. Ouverture puis traitement d'une réclamation.
10. Correction d'une présence → visible et auditée (ancienne valeur,
    auteur, date, motif).
11. Rapports produits et exportés CSV / Excel / PDF ; attestation générée
    avec identifiant vérifiable.
12. Consultation de la piste d'audit.
13. Un `PEDAGOGICAL_MANAGER` (puis un `TEACHER`) se connecte, ouvre
    « Apprenants » → il ne voit que les apprenants de son périmètre ;
    viser la fiche d'un apprenant d'une autre formation → « introuvable ».
14. N'importe quel rôle ouvre « Sécurité de mon compte » → « Mot de
    passe » → change son mot de passe → renvoi vers `/login`, ancien mot
    de passe refusé, nouveau accepté, autres sessions déconnectées.

---

## 8. Recommandation

Version **stable candidate**. La recette navigateur complète a été
**rejouée après les correctifs (202/202)** et le calcul du taux
d'assiduité (ANO-UX-007) est **corrigé en local**. Avant présentation en
jury, restent recommandés : une validation humaine à froid **en
navigateur** de la liste des apprenants et du changement de mot de passe
pour un `PEDAGOGICAL_MANAGER` / `TEACHER`, la revue visuelle du taux
d'assiduité d'un alternant, le déploiement par le porteur, et un test de
restauration de sauvegarde consigné.
