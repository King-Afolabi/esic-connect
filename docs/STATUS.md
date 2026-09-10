# État du projet — ESIC Connect

Vue synthétique et publique de ce qui est **réellement livré**, partiel
ou non implémenté. Le vocabulaire de statut est celui du dépôt :

| Statut | Signification |
|---|---|
| `IMPLEMENTED_AND_TESTED` | code livré **et** couvert par des tests automatisés passants |
| `PARTIAL` | une partie seulement est livrée — jamais présentée comme complète |
| `NOT_IMPLEMENTED` | aucun code ; limite explicitement assumée |
| `NOT_PERFORMED` | action jamais exécutée (démonstration manuelle, déploiement de production) |

---

## 1. Couverture des exigences

Le cahier des charges définit **142 exigences fonctionnelles**.

| Statut | Nombre | Part |
|---|---:|---:|
| `IMPLEMENTED_AND_TESTED` | 112 | 79 % |
| `PARTIAL` | 6 | 4 % |
| `NOT_IMPLEMENTED` | 24 | 17 % |

Les exigences non implémentées correspondent aux derniers incréments de
la trajectoire (service d'IA, objets connectés, RGPD outillé,
exploitation avancée). Ce ne sont pas des régressions.

### Par domaine

| Domaine | Livré | Partiel | Absent |
|---|---:|---:|---:|
| Identité et accès (15) | 15 | 0 | 0 |
| Utilisateurs (9) | 9 | 0 | 0 |
| Référentiels et organisation (13) | 12 | 0 | 1 |
| Inscriptions et imports (10) | 9 | 0 | 1 |
| Corps enseignant (5) | 4 | 1 | 0 |
| Planning (13) | 11 | 0 | 2 |
| Séances (9) | 9 | 0 | 0 |
| Émargement et assiduité (16) | 14 | 0 | 2 |
| Justificatifs et réclamations (8) | 8 | 0 | 0 |
| Notifications et mobilité (9) | 7 | 2 | 0 |
| Restitution (10) | 9 | 0 | 1 |
| IA et objets connectés (10) | 0 | 0 | 10 |
| Intégrations (4) | 1 | 3 | 0 |
| Transverse (11) | 4 | 0 | 7 |

---

## 2. Capacités livrées et testées

- **Identité et accès** : connexion mot de passe et passkey (WebAuthn,
  sans donnée biométrique), MFA TOTP obligatoire pour les rôles
  privilégiés, authentification adaptative, anti-robot avec validation
  serveur, limitation de débit, multi-rôles avec contexte vérifié côté
  serveur, invitations et activation, mot de passe oublié à réponse
  neutre, **changement de mot de passe personnel**, sessions révocables,
  appareils de confiance, continuité de session au rechargement par
  cookie de renouvellement `HttpOnly`.
- **Référentiels et organisation** : années, formations, niveaux,
  promotions, classes, groupes temporaires, matières, sites, bâtiments,
  salles, plages réseau CIDR IPv4/IPv6, rythmes d'alternance et
  exceptions.
- **Population** : import CSV et Excel `.xlsx` (y compris classeur
  multifeuille) en deux phases — simulation sans écriture puis
  confirmation atomique —, détection des doublons, correction ligne à
  ligne, inscriptions historisées, changement de classe conservant
  l'historique, numéro étudiant alloué automatiquement.
- **Planning** : import CSV et Excel, prévisualisation sans création de
  séance, correction ligne à ligne, détection des conflits (formateur,
  classe, salle, horaire, alternance), versionnement avec retour à une
  version antérieure, publication atomique et idempotente, calendrier
  interactif.
- **Séances** : création depuis un planning publié ou en exception,
  cycle de vie strict `PLANNED → OPEN → CLOSED`, annulation, report,
  demandes d'annulation, remplacements datés, séances multi-classes.
- **Émargement** : QR dynamique rotatif, code court, QR fixe de salle
  avec contrôle de plage réseau, scan **intégré à l'application**
  (composant caméra + décodeur logiciel de repli), saisie manuelle
  motivée, apprenant provisoire, quatre points de contrôle journaliers,
  calcul des demi-journées, paliers de retard, corrections auditées
  (historique append-only), départ anticipé, journal de transparence.
  L'alternance en entreprise n'est jamais comptée comme une absence.
- **Consultation des apprenants** : liste et fiche ouvertes au
  responsable pédagogique et au formateur, **restreintes à leur
  périmètre côté serveur** ; recherche par nom, prénom ou numéro.
- **Justificatifs et réclamations** : dépôt avec pièces jointes
  contrôlées (type réel, taille, analyse antivirus activable),
  examen et décision motivée, transformation `ABSENT → EXCUSED`,
  réclamations conversationnelles avec transfert et réouverture.
- **Notifications** : centre persistant, courriel (encodage UTF-8),
  audience résolue après commit, préférences par canal et catégorie,
  garanties par une outbox transactionnelle avec reprise et file
  d'échec rejouable.
- **Mobilité** : application installable (PWA), file d'actions différées
  pour l'émargement hors ligne avec état « en attente de confirmation ».
- **Pilotage** : tableaux de bord par rôle, rapports de classe et
  individuels, exports CSV / Excel / PDF partageant une même description
  de rapport, attestation d'assiduité à identifiant vérifiable,
  recherche globale au périmètre de l'appelant, consultation et export
  de la piste d'audit, flux iCalendar signé et révocable.
- **Transverse** : en-têtes HTTP durcis, CORS restrictif piloté par
  configuration, autorisations contrôlées côté serveur à quatre niveaux
  (route, service, ressource, périmètre), audit inaltérable sans donnée
  personnelle ni adresse IP, jeton d'accès en mémoire seule.

---

## 3. Partiel

| Exigence | Ce qui existe | Ce qui manque |
|---|---|---|
| `EF-TEA-002` | API d'affectation pédagogique | écran d'affectation classe–matière–période |
| `EF-NOTIF-005` | abonnement push par appareil, préférences, chiffrement RFC 8291 vérifié contre le vecteur de test officiel | aucun service de poussée réel sollicité ; sans clés VAPID l'API déclare `providerActive: false` |
| `EF-PWA-002` | consultation hors ligne **application ouverte** (cache du planning, de l'assiduité, des notifications) | pas de consultation après un démarrage à froid sans réseau (le jeton ne vit qu'en mémoire) |
| `EF-INT-002` | port et adaptateur Microsoft Graph (réunion Teams), adaptateur inactif | aucun locataire Microsoft réel sollicité ; l'API déclare `meetingActive: false` |
| `EF-INT-003` | port et adaptateur Microsoft Graph (calendrier) | idem — aucun locataire réel |
| `EF-INT-004` | fournisseur de courriel réel intégré de bout en bout (SMTP Brevo, domaine authentifié SPF/DKIM/DMARC), code et transaction SMTP testés jusqu'à l'acceptation serveur (`250 OK`) | livraison finale actuellement bloquée par une revue de compte côté fournisseur (suspension anti-abus d'un compte neuf), sans lien avec le code applicatif — en attente de réponse du support Brevo |

---

## 4. Non implémenté

Aucune ligne de code — limites explicitement assumées :

- détection des conflits de salle hors planning (`EF-ORG-004`) ;
- assistance IA au mapping d'import et import de planning PDF texte
  (`EF-IMP-005`, `EF-PLAN-012`, `EF-PLAN-013`) ;
- confirmation locale d'un émargement par WebAuthn, borne connectée
  (`EF-ATT-011`, `EF-ATT-016`) ;
- rapport des anomalies d'émargement (`EF-REP-009`) ;
- service d'IA complet (`EF-AI-001` à `EF-AI-005`) ;
- objets connectés MQTT et simulateur (`EF-IOT-001` à `EF-IOT-005`) ;
- droits RGPD outillés et exploitation avancée
  (`EF-RGPD-001` à `EF-RGPD-003`, `EF-OPS-001` à `EF-OPS-004`).

---

## 5. Architecture livrée

**Monolithe modulaire** Spring Boot, **19 modules** Spring Modulith,
isolation vérifiée automatiquement (aucune dépendance vers l'interne
d'un autre module, aucun cycle, aucune entité JPA partagée). Schéma
MySQL en **V34**, migrations Flyway. Communication entre modules par
ports publics et par événements ; effets de bord externes par outbox
transactionnelle.

Un seul service séparé est **prévu et non encore construit** : le
service d'IA Python / FastAPI.

---

## 6. Tests

Dernière exécution complète :

| Suite | Résultat |
|---|---|
| Back-end (`./mvnw clean test`) | 1280 tests, 0 échec ; `ModularityTests` vert (19 modules, 0 cycle) |
| Front-end (`ng test`) | ~935 tests, 0 échec ; `ng lint` et `ng build --configuration production` verts, aucune alerte de budget |
| Recette navigateur (`npm run test:e2e`, Playwright / Chromium) | 209 tests ; suite complète non systématiquement à 0 échec — un flake d'infra préexistant lié au rythme de connexion sur `02-authorization-rbac.spec.ts` provoque parfois des échecs en cascade sur des tests sans rapport, sans reproduction locale ; les 7 tests de non-régression du défilement des tableaux (`17-table-scroll-chaining.spec.ts`) sont, eux, verts à 100 % sur chaque run observé |
| `npm audit` (front-end) | 0 vulnérabilité |

Les commandes de vérification sont dans le `README.md` et
`scripts/verify-all.sh`. Les tests marqués `perf` sont exclus par
défaut.

---

## 7. Déploiement et statut de production

- Le produit est **déployable** par `compose.prod.yaml` (Docker
  Compose : MySQL, Redis, front-end, back-end, tunnel sortant). Procédure
  générique : [`docs/deployment/`](deployment/).
- Une instance de **recette / démonstration** est en ligne sur une
  Raspberry Pi, exposée par un tunnel Cloudflare **nommé** sur un
  domaine propre (URL publique stable, ne change plus au redémarrage),
  avec un jeu de données **strictement fictives** (profil `demo`). Ce
  n'est **pas** un déploiement de production.
- **Déploiement continu** : un runner GitHub Actions auto-hébergé
  tourne sur la Pi elle-même (service systemd) ; chaque fusion sur
  `main` déclenche automatiquement sauvegarde, reconstruction des
  seuls services impactés, contrôle `healthy` + vérification publique,
  et rollback automatique des images en cas d'échec.
- **`NOT_PERFORMED`** : démonstration manuelle de bout en bout par un
  humain (un navigateur piloté par script n'en est pas une) ; test de
  restauration de sauvegarde ; test de charge d'émargement soutenu.

---

## 8. Limites connues

| Réf | Limite |
|---|---|
| T-05 | politique de rétention des pièces jointes supprimées à définir avant tout usage sur données réelles |
| T-06 | pièces jointes sur système de fichiers local — monter un volume persistant sur un hébergement éphémère |
| T-13 | aucun service de poussée réel sollicité (pas de clés VAPID) |
| T-14 | pas de démarrage à froid hors ligne (le jeton d'accès ne vit qu'en mémoire) |
| T-15 | file d'actions différées non persistante entre deux chargements |
| T-16 | aucun locataire Microsoft réel sollicité — `EF-INT-002` / `EF-INT-003` restent `PARTIAL` |
| T-17 | aucun fichier de logo dans le dépôt — l'en-tête des PDF est une signature typographique |

---

## Règle de mise à jour

Ne jamais déclarer `IMPLEMENTED_AND_TESTED` sans commande exécutée et
reproductible, ni « démontré » sans vérification manuelle enregistrée,
ni « déployé » sans preuve. Ce document est mis à jour à chaque
livraison, dans le même commit que le code qu'il décrit.
