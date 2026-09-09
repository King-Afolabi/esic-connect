# Roadmap six mois — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Produit | ESIC Connect |
| Version du document | **2.0** |
| Date | 3 septembre 2026 |
| Période de référence | 2 mars → 29 août 2026 |
| Nombre de sprints | 13 |
| Durée d'un sprint | deux semaines |
| Méthode | Scrum adapté, complété par un Kanban de correctifs |
| Product Owner | Monsieur BANKA |
| Scrum Master | Monsieur INOUSSA Chaabane |
| Architecte-développeur | Abubacar AFOLABI |

---

## 0. Nature de ce document

> **Ce document est une trajectoire de référence, pas un relevé
> d'exécution.**
>
> Il décrit l'ordre logique dans lequel le produit se construit, ce que
> chaque sprint doit produire, et à quelle condition il est considéré
> comme terminé.
>
> Il ne constitue en aucun cas la preuve que les travaux ont été
> exécutés aux dates indiquées. **Les preuves réelles sont l'historique
> Git, les tests exécutés, les mesures relevées et les captures.**
> L'état réel du produit est tenu dans `docs/STATUS.md`, et lui
> seul fait foi.

---

## 1. Cadence

Chaque sprint dure deux semaines.

**Début** — planification, choix de l'objectif, sélection des stories,
vérification de la capacité, identification des risques.

**Pendant** — point quotidien, mise à jour du tableau, revue de code
systématique, tests écrits avec le code.

**Fin** — revue de sprint (démonstration de l'incrément), rétrospective,
mise à jour de `docs/STATUS.md`, pose du jalon Git.

**Règle de fin de sprint** : un sprint n'est pas terminé si son
incrément n'est pas démontrable, testé et documenté.

---

## 2. Vue d'ensemble

| Sprint | Période | Objectif | Jalon |
|---|---|---|---|
| S1 | 2 → 15 mars | Socle technique, identité, référentiels | `v0.1` |
| S2 | 16 → 29 mars | Sécurité forte : passkeys, MFA, anti-robot, limitation | `v0.2` |
| S3 | 30 mars → 12 avril | Population : profils, inscriptions, import CSV, invitations | `v0.3` |
| S4 | 13 → 26 avril | Excel, opérations de masse, alternance | `v0.4` |
| S5 | 27 avril → 10 mai | Planning : import, revue, conflits, publication versionnée | `v0.5` |
| S6 | 11 → 24 mai | Planning avancé : calendrier, retour arrière, séances, remplacements | `v0.6` |
| S7 | 25 mai → 7 juin | Émargement : ouverture, QR dynamique, code court, présences | `v0.7` |
| S8 | 8 → 21 juin | Points de contrôle, QR de salle, réseau, calculs d'assiduité | `v0.8` |
| S9 | 22 juin → 5 juillet | Justificatifs, réclamations, départ anticipé, transparence | `v0.9` |
| S10 | 6 → 19 juillet | Notifications multicanal, outbox, PWA installable et hors ligne | `v0.10` |
| S11 | 20 juillet → 2 août | Tableaux de bord, rapports, exports, attestations, calendriers | `v0.11` |
| S12 | 3 → 16 août | Service IA, borne connectée, détection d'anomalies | `v0.12` |
| S13 | 17 → 29 août | Durcissement, observabilité, RGPD, sauvegarde, déploiement | `v1.0` |

---

## 3. Sprints

### S1 — Socle, identité, référentiels

**Objectif** : un utilisateur se connecte, ses droits sont appliqués, et
les référentiels de l'établissement sont administrables.

| Contenu | Exigences |
|---|---|
| Squelette modulaire, migrations, conteneurs, intégration continue | — |
| Connexion, JWT, multi-rôles, contexte de rôle vérifié | EF-AUTH-001..003 |
| Invitation et activation de compte | EF-AUTH-004, EF-USER-001 |
| Cycle de vie des comptes et attribution de rôles | EF-USER-002, 003, 006 |
| Formations, niveaux, années, promotions, classes | EF-ACA-001..005, 008 |
| Sites, bâtiments, salles, plages réseau | EF-ORG-001, 002 |
| Piste d'audit | EF-AUD-001 |

**Livrables** : application démarrable en une commande, schéma versionné,
matrice d'autorisations testée, écrans d'administration.

**Terminé si** : un `PEDAGOGICAL_MANAGER` ne voit que son périmètre, et
les tests d'autorisation `401`/`403`/`200` passent pour chaque route.

---

### S2 — Sécurité forte

**Objectif** : l'authentification résiste aux attaques courantes et
propose une expérience sans mot de passe.

| Contenu | Exigences |
|---|---|
| Mot de passe oublié et réinitialisation | EF-AUTH-005 |
| Passkeys : enregistrement, connexion, gestion | EF-AUTH-006, 007 |
| Second facteur TOTP et codes de récupération | EF-AUTH-008, 009 |
| Authentification adaptative | EF-AUTH-010 |
| Anti-robot sur les formulaires exposés | EF-AUTH-011 |
| Limitation de débit sur les routes sensibles | EF-AUTH-012 |
| Appareils de confiance, déconnexion, révocation | EF-AUTH-013, 014, 015 |

**Terminé si** : `AC-020` à `AC-023` sont vérifiés par des tests, et
aucune route sensible n'est exploitable par force brute.

---

### S3 — Population et invitations

**Objectif** : une promotion entière entre dans le système et reçoit ses
accès.

| Contenu | Exigences |
|---|---|
| Profils apprenants, inscriptions, changement de classe historisé | EF-ENR-001..003 |
| Import CSV : simulation puis confirmation atomique | EF-IMP-001, 002 |
| Émission, suivi et réémission d'invitation depuis l'interface | EF-USER-007 |
| Suivi de délivrabilité des courriels | EF-USER-008 |
| Matières, groupes temporaires | EF-ACA-006, 007 |
| Formateurs externes et affectations pédagogiques | EF-TEA-001, 002 |

**Terminé si** : `AC-004` à `AC-006` sont vérifiés, et une confirmation
d'import interrompue ne laisse aucune donnée partielle.

---

### S4 — Excel, masse, alternance

**Objectif** : les fichiers réels de l'établissement sont acceptés et
l'alternance est modélisée.

| Contenu | Exigences |
|---|---|
| Import Excel `.xlsx` et classeur multifeuille | EF-IMP-003, 004 |
| Correction de ligne avant confirmation | EF-IMP-006 |
| Opérations de masse avec prévisualisation | EF-USER-004 |
| Détection et traitement des doublons | EF-USER-005 |
| Rythmes d'alternance, affectations, exceptions | EF-ACA-009 |

**Terminé si** : un classeur de trois feuilles produit trois classes
correctement rattachées, et une journée en entreprise est résolue comme
telle.

---

### S5 — Planning : import et publication

**Objectif** : un planning entre, est contrôlé, et devient des séances.

| Contenu | Exigences |
|---|---|
| Import CSV borné, jamais écrit sur disque | EF-PLAN-001 |
| Simulation sans création de séance | EF-PLAN-002 |
| Correction ligne à ligne dans l'écran de revue | EF-PLAN-003 |
| Détection des conflits formateur, classe, salle, horaire | EF-PLAN-009 |
| Publication atomique versionnée | EF-PLAN-004, 005, 007 |
| Création des séances depuis le planning publié | EF-SES-001 |

**Terminé si** : `AC-007` et `AC-008` sont vérifiés, et deux
publications concurrentes sont idempotentes.

---

### S6 — Planning avancé et séances

**Objectif** : le planning se construit et se corrige directement dans
l'application ; les séances vivent leur cycle complet.

| Contenu | Exigences |
|---|---|
| Calendrier interactif de construction | EF-PLAN-006 |
| Retour à une version antérieure | EF-PLAN-008 |
| Avertissement d'alternance à la publication | EF-PLAN-010 |
| Import de planning Excel | EF-PLAN-011 |
| Annulation, report, séance exceptionnelle, demande d'annulation | EF-SES-004, 006, 007, 008 |
| Remplacements datés et notifiés | EF-TEA-003..005, EF-SES-005 |
| Séance multi-classes ou de groupe | EF-SES-009 |
| Conflits et incohérences de salle | EF-ORG-004 |

**Terminé si** : `AC-009` est vérifié, et un remplaçant n'obtient de
droits que pendant sa période.

---

### S7 — Émargement nominal

**Objectif** : une séance s'ouvre et les présences s'enregistrent.

| Contenu | Exigences |
|---|---|
| Ouverture et clôture de séance | EF-SES-002, 003 |
| QR dynamique rotatif et code court | EF-ATT-001, 009 |
| Validation de présence, anti-rejeu | EF-ATT-002 |
| Suivi des présences en direct | EF-ATT-015 |
| Présence manuelle motivée | EF-ATT-006 |
| Correction auditée | EF-ATT-012 |
| Autorisation de suivi à distance individuel | EF-ENR-004 |

**Terminé si** : `AC-011`, `AC-018` sont vérifiés, et un double
émargement concurrent ne produit jamais d'erreur `500`.

---

### S8 — Points de contrôle et assiduité

**Objectif** : l'assiduité se calcule comme l'établissement la compte.

| Contenu | Exigences |
|---|---|
| Quatre points de contrôle nommés | EF-ATT-003 |
| Calcul demi-journées et journées | EF-ATT-004 |
| Paliers de retard 15 et 30 minutes | EF-ATT-005 |
| QR fixe de salle et contrôle réseau | EF-ATT-008, 010, EF-ORG-003 |
| Confirmation locale WebAuthn de l'émargement | EF-ATT-011 |
| Apprenant provisoire | EF-ATT-007 |

**Terminé si** : `AC-010`, `AC-012` à `AC-015` sont vérifiés.

---

### S9 — Justificatifs et réclamations

**Objectif** : l'apprenant explique, conteste et suit ses démarches.

| Contenu | Exigences |
|---|---|
| Dépôt, pièce jointe contrôlée et analysée, examen, décision | EF-JUS-001..004 |
| Réclamations : création, conversation, transfert, réouverture | EF-CLAIM-001..004 |
| Départ anticipé | EF-ATT-013 |
| Journal de transparence des présences | EF-ATT-014 |

**Terminé si** : `AC-016`, `AC-029`, `AC-030` sont vérifiés.

---

### S10 — Notifications et mobilité

**Objectif** : chacun est prévenu à temps, y compris hors ligne.

| Contenu | Exigences |
|---|---|
| Outbox transactionnelle pour tout effet de bord | EF-AUD-003, EF-OPS-005 |
| Centre de notifications et audience complète | EF-NOTIF-001..003 |
| Notifications par courriel et push PWA | EF-NOTIF-004, 005 |
| Préférences de notification | EF-NOTIF-006 |
| PWA installable, hors ligne, file d'actions | EF-PWA-001..003 |

**Terminé si** : `AC-027`, `AC-028`, `AC-031` sont vérifiés.

---

### S11 — Pilotage et restitution

**Objectif** : les décisions s'appuient sur des chiffres et des
documents.

| Contenu | Exigences |
|---|---|
| Tableaux de bord des quatre profils | EF-REP-007, 008 |
| Rapports de classe et individuels | EF-REP-001, 002, 010 |
| Exports CSV, Excel, PDF | EF-REP-003..005 |
| Attestation d'assiduité identifiable | EF-REP-006 |
| Recherche globale | EF-USER-009 |
| Consultation et export de l'audit | EF-AUD-002 |
| Flux iCalendar, réunions Teams, calendrier Microsoft | EF-INT-001..003 |

**Terminé si** : `AC-019`, `AC-032`, `AC-033`, `AC-034` sont vérifiés.

---

### S12 — Intelligence et objets connectés

**Objectif** : le système assiste l'humain et s'ouvre au matériel.

| Contenu | Exigences |
|---|---|
| Service IA : mapping de colonnes, score de confiance | EF-AI-001, 002, EF-IMP-005, EF-PLAN-013 |
| Détection d'anomalies et risque de décrochage | EF-AI-003, 004, EF-REP-009 |
| Validation humaine obligatoire | EF-AI-005 |
| Borne MQTT : identité, révocation, idempotence, télémétrie, file locale | EF-IOT-001..005, EF-ATT-016 |
| Import de planning PDF texte | EF-PLAN-012 |

**Terminé si** : `AC-024`, `AC-025`, `AC-026` sont vérifiés, et le
simulateur rejoue une coupure réseau sans doublon.

---

### S13 — Mise en service

**Objectif** : le produit est exploitable par d'autres que son auteur.

| Contenu | Exigences |
|---|---|
| Santé, métriques, journaux structurés | EF-OPS-001 |
| Sauvegarde et restauration avec preuve | EF-OPS-002 |
| Déploiement continu vers un environnement accessible | EF-OPS-003 |
| OpenAPI versionné dans le dépôt | EF-OPS-004 |
| Courriels via fournisseur réel | EF-INT-004 |
| Droits RGPD et purge | EF-RGPD-001..003 |
| Campagne de performance et d'accessibilité | NFR-PERF-*, WCAG AA |

**Terminé si** : `AC-035`, `AC-036` sont vérifiés, et l'application est
jointe par une URL avec HTTPS.

---

## 4. Jalons Git

Chaque fin de sprint pose une **étiquette** annotée sur `main` :

```text
v0.1 … v0.12   incréments de sprint
v1.0           version de mise en service
```

Le développement d'un sprint se fait sur une branche
`sprint/SNN-<thème>`, fusionnée par demande de tirage revue.

**Règle** : une étiquette n'est posée que lorsque l'incrément
correspondant est réellement présent sur `main`, testé et documenté. Une
étiquette n'est jamais posée par anticipation ni antidatée.

---

## 5. Dépendances

```text
S1 ──▶ S2 ──▶ S3 ──▶ S4
        │      │
        │      └────▶ S5 ──▶ S6 ──▶ S7 ──▶ S8 ──▶ S9
        │                                    │
        └────────────────────────────────────┴──▶ S10 ──▶ S11 ──▶ S12 ──▶ S13
```

- S5 exige les classes, les formateurs et les salles (S1, S3).
- S7 exige des séances issues d'un planning publié (S5).
- S8 exige l'alternance (S4) pour le dénominateur d'assiduité.
- S10 exige l'outbox avant d'élargir l'audience des notifications.
- S11 exige des présences réelles (S7, S8).
- S12 exige des imports (S3, S5) et des émargements (S7).
- S13 exige l'ensemble.

---

## 6. Capacité et estimation

| Élément | Valeur |
|---|---|
| Équipe de réalisation | 1 architecte-développeur, assisté |
| Capacité retenue | 55 points par sprint |
| Capacité totale | 715 points sur 13 sprints |
| Total planifié | 643 points, 135 stories |
| Marge | 10 % réservés aux correctifs, à la dette et aux imprévus |

Les points par story figurent dans `docs/05-product-backlog.md`.

---

## 7. Points de contrôle

| Moment | Contrôle |
|---|---|
| Fin S2 | la sécurité d'accès est-elle suffisante pour manipuler des données réelles ? |
| Fin S4 | les fichiers réels de l'établissement passent-ils ? |
| Fin S6 | un planning complet d'une classe est-il gérable de bout en bout ? |
| Fin S8 | l'assiduité calculée correspond-elle à celle constatée manuellement ? |
| Fin S10 | les utilisateurs sont-ils prévenus à temps, sans perte ? |
| Fin S12 | les apports de l'IA et de l'IoT sont-ils réels ou décoratifs ? |
| Fin S13 | un tiers peut-il installer, exploiter et restaurer le produit ? |

Un point de contrôle négatif déclenche une replanification, jamais une
livraison partielle présentée comme complète.

---

## 8. Position réelle

L'écart entre cette trajectoire et l'état effectif du dépôt est tenu
dans `docs/STATUS.md`, mis à jour à chaque livraison. Ce
document-ci n'est pas mis à jour pour refléter l'avancement : il décrit
le plan, pas la réalité.

En cas de contradiction entre cette roadmap et `docs/STATUS.md`,
**c'est `STATUS.md` qui a raison**.
