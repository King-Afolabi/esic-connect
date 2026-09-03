# Backlog produit — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Version | **2.0** |
| Date | 3 septembre 2026 |
| Périmètre | version 1.0 du produit |
| Trajectoire | `docs/06-roadmap-six-mois.md` — 13 sprints de deux semaines |
| Exigences | `docs/02-cahier-des-charges.md` §36 |
| Total | **135 stories, 643 points** |

---

## 0. Conventions

**Format** : `US-NNN` — *En tant que* rôle, *je veux* capacité, *afin de*
bénéfice.

**Estimation** : suite de Fibonacci en points relatifs (1, 2, 3, 5, 8,
13). Une story de plus de 13 points est découpée.

**Priorité** : `MUST`, `SHOULD`, `COULD` — reprise du cahier des
charges. Aucune priorité ne signifie « abandonné ».

**Terminé** : la définition de terminé du cahier des charges §41
s'applique à **toute** story, sans exception ni allègement.

**Règle de découpage** : une story livre une capacité utilisable par un
utilisateur. Une tâche purement technique est rattachée à la story
qu'elle sert, jamais présentée comme un incrément.

---

## 1. Épopées

| Épopée | Domaine | Stories | Points |
|---|---|---:|---:|
| E01 | Identité et accès | 15 | 66 |
| E02 | Utilisateurs et invitations | 9 | 37 |
| E03 | Référentiels et organisation | 12 | 45 |
| E04 | Population et imports | 10 | 52 |
| E05 | Planning | 13 | 81 |
| E06 | Séances et remplacements | 9 | 34 |
| E07 | Émargement et assiduité | 16 | 85 |
| E08 | Justificatifs et réclamations | 10 | 40 |
| E09 | Notifications et mobilité | 9 | 47 |
| E10 | Restitution et pilotage | 10 | 45 |
| E11 | Intelligence artificielle | 5 | 27 |
| E12 | Objets connectés | 5 | 24 |
| E13 | Intégrations | 4 | 21 |
| E14 | Exploitation et conformité | 8 | 39 |
| **Total** | | **135** | **643** |

Capacité retenue : **55 points par sprint**, soit 715 points sur 13
sprints pour 643 planifiés — une marge de 10 % réservée aux correctifs,
à la dette technique et aux imprévus. L'ordonnancement place les
priorités `COULD` en fin de sprint : ce sont elles qui absorbent un
dépassement, jamais la qualité ni les tests.

---

## 2. E01 — Identité et accès

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-001 | En tant qu'utilisateur, je me connecte avec mon email et mon mot de passe afin d'accéder à mon espace. | MUST | 5 | 1 | EF-AUTH-001 |
| US-002 | En tant qu'utilisateur multi-rôles, je choisis mon contexte d'usage afin de travailler avec les droits attendus. | MUST | 5 | 1 | EF-AUTH-002, 003 |
| US-003 | En tant qu'invité, j'active mon compte depuis un lien afin de définir mon mot de passe. | MUST | 5 | 1 | EF-AUTH-004 |
| US-004 | En tant qu'utilisateur, je réinitialise mon mot de passe oublié afin de retrouver l'accès sans assistance. | MUST | 5 | 2 | EF-AUTH-005 |
| US-005 | En tant qu'utilisateur, j'enregistre une passkey afin de me connecter sans mot de passe. | MUST | 8 | 2 | EF-AUTH-006 |
| US-006 | En tant qu'utilisateur, je me connecte par passkey afin d'aller plus vite et d'être protégé de l'hameçonnage. | MUST | 5 | 2 | EF-AUTH-007 |
| US-007 | En tant qu'administrateur, j'active un second facteur TOTP afin de protéger mon compte privilégié. | MUST | 5 | 2 | EF-AUTH-008 |
| US-008 | En tant qu'utilisateur, je conserve des codes de récupération afin de ne pas être bloqué si je perds mon appareil. | MUST | 3 | 2 | EF-AUTH-009 |
| US-009 | En tant que responsable sécurité, j'exige un contrôle renforcé selon le risque afin de ne pas gêner l'usage courant. | SHOULD | 5 | 2 | EF-AUTH-010 |
| US-010 | En tant que responsable sécurité, je protège les formulaires publics contre les robots. | MUST | 3 | 2 | EF-AUTH-011 |
| US-011 | En tant que responsable sécurité, je limite les tentatives sur les routes sensibles. | MUST | 5 | 2 | EF-AUTH-012 |
| US-012 | En tant qu'utilisateur, je consulte et révoque mes appareils de confiance. | SHOULD | 3 | 2 | EF-AUTH-013 |
| US-013 | En tant qu'utilisateur, je me déconnecte et ma session est réellement révoquée. | MUST | 3 | 2 | EF-AUTH-014 |
| US-014 | En tant que responsable sécurité, j'exige une réauthentification avant une action critique. | SHOULD | 3 | 2 | EF-AUTH-015 |
| US-015 | En tant qu'auditeur, je retrouve chaque événement d'authentification dans la piste d'audit. | MUST | 3 | 1 | EF-AUD-001 |

## 3. E02 — Utilisateurs et invitations

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-020 | En tant qu'administrateur, je crée un utilisateur en attente d'activation. | MUST | 3 | 1 | EF-USER-001 |
| US-021 | En tant qu'administrateur, je suspends et réactive un compte. | MUST | 3 | 1 | EF-USER-002 |
| US-022 | En tant qu'administrateur, j'archive et restaure un compte sans perdre l'historique. | MUST | 3 | 1 | EF-USER-003 |
| US-023 | En tant qu'administrateur, j'attribue et retire un rôle avec des gardes qui m'empêchent de me verrouiller. | MUST | 5 | 1 | EF-USER-006 |
| US-024 | En tant que responsable, j'émets une invitation depuis l'interface. | MUST | 5 | 3 | EF-USER-007 |
| US-025 | En tant que responsable, je suis la délivrabilité d'une invitation et je corrige l'adresse. | SHOULD | 5 | 3 | EF-USER-008 |
| US-026 | En tant que responsable, je réémets une invitation en révoquant l'ancien jeton. | MUST | 3 | 3 | EF-USER-007 |
| US-027 | En tant qu'administrateur, j'exécute une opération de masse après prévisualisation. | SHOULD | 5 | 4 | EF-USER-004 |
| US-028 | En tant qu'administrateur, je détecte et traite les doublons de comptes. | MUST | 5 | 4 | EF-USER-005 |

## 4. E03 — Référentiels et organisation

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-030 | En tant qu'administrateur, je gère les années scolaires. | MUST | 3 | 1 | EF-ACA-005 |
| US-031 | En tant qu'administrateur, je gère les formations et leurs responsables. | MUST | 5 | 1 | EF-ACA-001, 008 |
| US-032 | En tant qu'administrateur, je gère les niveaux. | MUST | 2 | 1 | EF-ACA-002 |
| US-033 | En tant que responsable, je gère les promotions de mon périmètre. | MUST | 3 | 1 | EF-ACA-003 |
| US-034 | En tant que responsable, je gère les classes de mon périmètre. | MUST | 5 | 1 | EF-ACA-004 |
| US-035 | En tant que responsable, je gère les matières. | MUST | 3 | 3 | EF-ACA-006 |
| US-036 | En tant que responsable, je constitue un groupe temporaire pour une option ou une langue. | SHOULD | 5 | 3 | EF-ACA-007 |
| US-037 | En tant qu'administrateur, je gère sites, bâtiments et salles. | MUST | 5 | 1 | EF-ORG-001 |
| US-038 | En tant que super administrateur, je gère les plages réseau autorisées. | MUST | 3 | 1 | EF-ORG-002 |
| US-039 | En tant que responsable, je vois les conflits et incohérences de salle. | MUST | 3 | 6 | EF-ORG-004 |
| US-040 | En tant que responsable, je définis le rythme d'alternance d'une classe. | MUST | 5 | 4 | EF-ACA-009 |
| US-041 | En tant que responsable, je pose une exception d'alternance individuelle ou collective. | MUST | 3 | 4 | EF-ACA-009 |

## 5. E04 — Population et imports

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-050 | En tant que responsable, je crée et consulte le profil d'un apprenant. | MUST | 3 | 3 | EF-ENR-001 |
| US-051 | En tant que responsable, j'inscris un apprenant dans une classe. | MUST | 5 | 3 | EF-ENR-002 |
| US-052 | En tant que responsable, je change un apprenant de classe en conservant son historique. | MUST | 5 | 3 | EF-ENR-003 |
| US-053 | En tant que responsable, je simule un import CSV d'apprenants sans rien créer. | MUST | 8 | 3 | EF-IMP-001 |
| US-054 | En tant que responsable, je confirme un import et tout est créé ou rien ne l'est. | MUST | 8 | 3 | EF-IMP-002 |
| US-055 | En tant que responsable, j'importe un fichier Excel. | MUST | 5 | 4 | EF-IMP-003 |
| US-056 | En tant que responsable, j'importe un classeur multifeuille et je confirme le rattachement des classes. | SHOULD | 5 | 4 | EF-IMP-004 |
| US-057 | En tant que responsable, je corrige une ligne en anomalie sans recommencer l'import. | SHOULD | 5 | 4 | EF-IMP-006 |
| US-058 | En tant que responsable, je crée un formateur externe et je l'invite. | MUST | 3 | 3 | EF-TEA-001 |
| US-059 | En tant que responsable, j'affecte un formateur à une classe, une matière et une période. | MUST | 5 | 3 | EF-TEA-002 |

## 6. E05 — Planning

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-070 | En tant que responsable, j'importe un planning CSV sans qu'il soit écrit sur disque. | MUST | 5 | 5 | EF-PLAN-001 |
| US-071 | En tant que responsable, je prévisualise le planning sans créer de séance. | MUST | 8 | 5 | EF-PLAN-002 |
| US-072 | En tant que responsable, je vois les conflits formateur, classe, salle et horaire. | MUST | 8 | 5 | EF-PLAN-009 |
| US-073 | En tant que responsable, je corrige une ligne dans l'écran de revue. | MUST | 5 | 5 | EF-PLAN-003 |
| US-074 | En tant que responsable, je publie le planning et les séances sont créées. | MUST | 8 | 5 | EF-PLAN-004, EF-SES-001 |
| US-075 | En tant que responsable, je consulte l'historique des versions du planning. | MUST | 3 | 5 | EF-PLAN-005, 007 |
| US-076 | En tant que responsable, je reviens à une version antérieure. | MUST | 5 | 6 | EF-PLAN-008 |
| US-077 | En tant que responsable, je construis un planning dans un calendrier interactif. | MUST | 13 | 6 | EF-PLAN-006 |
| US-078 | En tant que responsable, je duplique une semaine et j'applique un rythme. | SHOULD | 5 | 6 | EF-PLAN-006 |
| US-079 | En tant que responsable, je suis averti d'un créneau tombant en période d'entreprise. | SHOULD | 3 | 6 | EF-PLAN-010 |
| US-080 | En tant que responsable, j'importe un planning Excel. | SHOULD | 5 | 6 | EF-PLAN-011 |
| US-081 | En tant que responsable, j'importe un planning PDF texte et je revois chaque ligne. | COULD | 8 | 12 | EF-PLAN-012 |
| US-082 | En tant que responsable, l'assistant me propose la correspondance des colonnes avec un score. | SHOULD | 5 | 12 | EF-PLAN-013, EF-IMP-005 |

## 7. E06 — Séances et remplacements

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-090 | En tant que formateur, je consulte mes séances et celles qui me sont déléguées. | MUST | 3 | 5 | EF-SES-001 |
| US-091 | En tant que responsable, j'annule une séance avec motif et je notifie. | MUST | 5 | 6 | EF-SES-004 |
| US-092 | En tant que responsable, je reporte une séance annulée à une nouvelle date. | SHOULD | 3 | 6 | EF-SES-007 |
| US-093 | En tant que formateur, je demande l'annulation d'une séance sans pouvoir la décider. | SHOULD | 3 | 6 | EF-SES-008 |
| US-094 | En tant que responsable, je crée une séance exceptionnelle avec motif. | MUST | 3 | 6 | EF-SES-006 |
| US-095 | En tant que responsable, je désigne un remplaçant sur une séance ou une période. | MUST | 8 | 6 | EF-TEA-003, EF-SES-005 |
| US-096 | En tant que formateur, je propose un remplaçant sans pouvoir valider moi-même. | MUST | 3 | 6 | EF-TEA-004 |
| US-097 | En tant que remplaçant, je reçois les droits uniquement pendant ma période. | MUST | 3 | 6 | EF-TEA-003 |
| US-098 | En tant que responsable, je rattache plusieurs classes ou un groupe à une séance. | SHOULD | 3 | 6 | EF-SES-009 |

## 8. E07 — Émargement et assiduité

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-110 | En tant que formateur, j'ouvre ma séance. | MUST | 3 | 7 | EF-SES-002 |
| US-111 | En tant que formateur, j'affiche un QR dynamique qui change périodiquement. | MUST | 8 | 7 | EF-ATT-001 |
| US-112 | En tant que formateur, j'affiche un code court utilisable sans caméra. | MUST | 3 | 7 | EF-ATT-009 |
| US-113 | En tant qu'apprenant, je scanne le QR et ma présence est enregistrée. | MUST | 8 | 7 | EF-ATT-002 |
| US-114 | En tant qu'apprenant, je saisis le code court quand la caméra n'est pas utilisable. | MUST | 3 | 7 | EF-ATT-009 |
| US-115 | En tant que formateur, je vois la liste des présents se mettre à jour en direct. | MUST | 5 | 7 | EF-ATT-015 |
| US-116 | En tant que formateur, j'enregistre une présence manuelle avec motif. | MUST | 5 | 7 | EF-ATT-006 |
| US-117 | En tant que formateur, je clôture la séance et les jetons sont purgés. | MUST | 3 | 7 | EF-SES-003 |
| US-118 | En tant que responsable, je corrige une présence avec motif et l'historique est conservé. | MUST | 5 | 7 | EF-ATT-012 |
| US-119 | En tant que responsable, j'autorise un apprenant à suivre une séance à distance. | MUST | 3 | 7 | EF-ENR-004 |
| US-120 | En tant qu'établissement, je dispose de quatre points de contrôle nommés par journée. | MUST | 8 | 8 | EF-ATT-003 |
| US-121 | En tant qu'administration, l'assiduité est calculée en demi-journées et en journées. | MUST | 8 | 8 | EF-ATT-004 |
| US-122 | En tant qu'établissement, les retards suivent les paliers 15 et 30 minutes. | MUST | 5 | 8 | EF-ATT-005 |
| US-123 | En tant qu'apprenant, j'émarge par le QR fixe de la salle avant le début du cours. | MUST | 8 | 8 | EF-ATT-010, EF-ORG-003 |
| US-124 | En tant que responsable sécurité, le QR fixe n'est accepté que depuis le réseau autorisé. | MUST | 5 | 8 | EF-ATT-008 |
| US-125 | En tant qu'apprenant, je confirme localement mon émargement par WebAuthn. | SHOULD | 5 | 8 | EF-ATT-011 |

## 9. E08 — Justificatifs et réclamations

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-140 | En tant qu'apprenant, je dépose un justificatif pour une absence. | MUST | 5 | 9 | EF-JUS-001 |
| US-141 | En tant qu'apprenant, je joins un fichier contrôlé et analysé. | MUST | 5 | 9 | EF-JUS-002 |
| US-142 | En tant que responsable, j'examine et je décide avec motif. | MUST | 5 | 9 | EF-JUS-003 |
| US-143 | En tant qu'administration, une acceptation transforme l'absence en absence excusée. | MUST | 3 | 9 | EF-JUS-004 |
| US-144 | En tant qu'apprenant, j'ouvre une réclamation auprès du bon interlocuteur. | MUST | 5 | 9 | EF-CLAIM-001 |
| US-145 | En tant qu'utilisateur, j'échange dans le fil de la réclamation. | MUST | 3 | 9 | EF-CLAIM-002 |
| US-146 | En tant que formateur, je transfère une réclamation avec motif. | SHOULD | 3 | 9 | EF-CLAIM-003 |
| US-147 | En tant qu'apprenant, je rouvre une réclamation clôturée. | SHOULD | 3 | 9 | EF-CLAIM-004 |
| US-148 | En tant qu'apprenant, je signale un départ anticipé et il est tracé. | SHOULD | 5 | 9 | EF-ATT-013 |
| US-149 | En tant qu'apprenant, je consulte le journal de transparence de mes présences. | SHOULD | 3 | 9 | EF-ATT-014 |

## 10. E09 — Notifications et mobilité

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-160 | En tant qu'exploitant, tout effet de bord passe par une outbox et se rejoue. | MUST | 8 | 10 | EF-AUD-003, EF-OPS-005 |
| US-161 | En tant qu'utilisateur, je consulte mon centre de notifications. | MUST | 5 | 10 | EF-NOTIF-001 |
| US-162 | En tant qu'apprenant, je suis prévenu de la publication et des changements de planning. | MUST | 5 | 10 | EF-NOTIF-002, 003 |
| US-163 | En tant qu'utilisateur, je reçois les notifications importantes par courriel. | MUST | 5 | 10 | EF-NOTIF-004 |
| US-164 | En tant qu'apprenant, je reçois une notification push avant ma séance. | SHOULD | 5 | 10 | EF-NOTIF-005 |
| US-165 | En tant qu'utilisateur, je règle mes préférences de notification. | SHOULD | 3 | 10 | EF-NOTIF-006 |
| US-166 | En tant qu'apprenant, j'installe l'application sur mon téléphone. | MUST | 3 | 10 | EF-PWA-001 |
| US-167 | En tant qu'apprenant, je consulte mon planning sans réseau. | SHOULD | 5 | 10 | EF-PWA-002 |
| US-168 | En tant qu'apprenant, mon action hors ligne est rejouée à la reconnexion et signalée en attente. | SHOULD | 8 | 10 | EF-PWA-003 |

## 11. E10 — Restitution et pilotage

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-180 | En tant que responsable, je dispose d'un tableau de bord de mon périmètre. | MUST | 8 | 11 | EF-REP-007 |
| US-181 | En tant qu'utilisateur, chaque graphique dispose d'un tableau équivalent. | MUST | 3 | 11 | EF-REP-008 |
| US-182 | En tant qu'administration, je produis un rapport de classe. | MUST | 5 | 11 | EF-REP-001 |
| US-183 | En tant qu'administration, je produis un rapport individuel. | MUST | 5 | 11 | EF-REP-002 |
| US-184 | En tant qu'administration, j'exporte en CSV. | MUST | 3 | 11 | EF-REP-003 |
| US-185 | En tant qu'administration, j'exporte en Excel. | MUST | 3 | 11 | EF-REP-004 |
| US-186 | En tant qu'administration, j'exporte en PDF avec l'identité visuelle. | MUST | 5 | 11 | EF-REP-005 |
| US-187 | En tant qu'administration, je génère une attestation d'assiduité identifiable. | SHOULD | 5 | 11 | EF-REP-006 |
| US-188 | En tant qu'utilisateur, je recherche globalement dans mon périmètre. | SHOULD | 5 | 11 | EF-USER-009 |
| US-189 | En tant qu'auditeur, je consulte et j'exporte la piste d'audit. | SHOULD | 3 | 11 | EF-AUD-002 |

## 12. E11 — Intelligence artificielle

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-200 | En tant que responsable, l'assistant me propose la correspondance des colonnes. | SHOULD | 8 | 12 | EF-AI-001, EF-IMP-005 |
| US-201 | En tant que responsable, chaque proposition porte un score de confiance exploitable. | SHOULD | 3 | 12 | EF-AI-002 |
| US-202 | En tant que responsable sécurité, les émargements suspects reçoivent un score d'anomalie. | SHOULD | 8 | 12 | EF-AI-003 |
| US-203 | En tant que responsable, je suis alerté d'un risque de décrochage. | COULD | 5 | 12 | EF-AI-004 |
| US-204 | En tant qu'utilisateur, aucune proposition de l'IA ne s'applique sans ma confirmation. | MUST | 3 | 12 | EF-AI-005 |

## 13. E12 — Objets connectés

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-210 | En tant qu'exploitant, la borne s'authentifie et publie un signal de vie. | SHOULD | 5 | 12 | EF-IOT-002, 004 |
| US-211 | En tant qu'apprenant, j'émarge depuis la borne de la salle. | SHOULD | 8 | 12 | EF-IOT-001, EF-ATT-016 |
| US-212 | En tant qu'exploitant, un événement déjà traité est ignoré. | MUST | 3 | 12 | EF-IOT-003 |
| US-213 | En tant qu'exploitant, la borne rejoue sa file locale après une coupure. | SHOULD | 5 | 12 | EF-IOT-005 |
| US-214 | En tant que super administrateur, je révoque une borne compromise. | SHOULD | 3 | 12 | EF-IOT-002 |

## 14. E13 — Intégrations

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-220 | En tant qu'utilisateur, je m'abonne à mon planning depuis mon agenda. | SHOULD | 5 | 11 | EF-INT-001 |
| US-221 | En tant que responsable, une séance distancielle crée sa réunion Teams. | SHOULD | 8 | 11 | EF-INT-002 |
| US-222 | En tant que formateur, mes séances apparaissent dans mon calendrier Microsoft. | COULD | 5 | 11 | EF-INT-003 |
| US-223 | En tant qu'exploitant, les courriels partent par un fournisseur réel avec suivi. | MUST | 3 | 13 | EF-INT-004 |

## 15. E14 — Exploitation et conformité

| ID | Story | Prio | Pts | S | Exigences |
|---|---|---|---:|---:|---|
| US-230 | En tant qu'exploitant, je supervise santé, métriques et journaux structurés. | MUST | 5 | 13 | EF-OPS-001 |
| US-231 | En tant qu'exploitant, je sauvegarde et je restaure, avec preuve. | MUST | 8 | 13 | EF-OPS-002 |
| US-232 | En tant qu'exploitant, chaque fusion déclenche les contrôles et le déploiement. | MUST | 5 | 13 | EF-OPS-003 |
| US-233 | En tant qu'intégrateur, je consomme une documentation OpenAPI versionnée. | MUST | 3 | 13 | EF-OPS-004 |
| US-234 | En tant que personne concernée, j'obtiens l'export de mes données. | SHOULD | 5 | 13 | EF-RGPD-001 |
| US-235 | En tant que personne concernée, je demande rectification ou limitation. | SHOULD | 3 | 13 | EF-RGPD-002 |
| US-236 | En tant que responsable de traitement, les données échues sont purgées ou anonymisées. | MUST | 5 | 13 | EF-RGPD-003 |
| US-237 | En tant qu'utilisateur, l'application est utilisable au clavier et par un lecteur d'écran. | MUST | 5 | 13 | WCAG AA |

---

## 16. Dette et travaux techniques

Ces éléments ne sont pas des stories utilisateur : ils sont rattachés au
sprint qui les rend nécessaires et conditionnent sa clôture.

| Réf | Travail | Sprint |
|---|---|---|
| T-01 | Outbox transactionnelle unique pour courriel, notification, audit, MQTT | 10 |
| T-02 | Remplacement des listeners d'audit synchrones par l'outbox | 10 |
| T-03 | Chargement par lot des séances du tableau de bord (coût SQL borné) | 11 |
| T-04 | Antivirus sur les pièces jointes et balayage des fichiers orphelins | 9 |
| T-05 | Politique de rétention des pièces jointes supprimées | 13 |
| T-06 | Adaptateur de stockage objet pour les pièces jointes | 13 |
| T-07 | Campagne de charge sur l'émargement | 13 |
| T-08 | Publication de `openapi.json` versionné | 13 |
| T-09 | Migration du hachage vers Argon2id | 13 |
| T-10 | Externalisation complète des textes de l'interface | 13 |

---

## 17. Règle de tenue

Ce backlog décrit **ce qui doit être construit**. Il ne décrit jamais ce
qui est construit : cette information est dans `docs/CURRENT-STATE.md`.

Une story n'est retirée du backlog que lorsqu'elle est terminée au sens
du cahier des charges §41, ou lorsque son retrait est décidé et motivé
par écrit.
