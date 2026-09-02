# Diagnostic du chemin critique de démonstration

> **But** : consigner ce qui **empêchait réellement** de dérouler le
> parcours prioritaire en démonstration, ce qui a été corrigé, et ce qui
> reste à faire à la main. Document daté ; il ne remplace pas
> `docs/CURRENT-STATE.md`.

| Élément | Valeur |
|---|---|
| Date | 2 septembre 2026 |
| Branche | `fix/demo-critical-path` |
| Base de démonstration | `esic_connect_demo` (créée vierge, Flyway `V1 → V16`) |
| Profil | `demo` |
| Nature de la vérification | **appels API** (aucune manipulation d'interface) |

---

## 1. Anomalies trouvées et corrigées

### 1.1 Import des apprenants — soumission native du formulaire

Le `[formGroup]` était posé sur `<div class="upload__scope">` et non sur
le `<form>`. Sans `FormGroupDirective` attachée à l'élément de
formulaire, Angular n'interceptait pas l'événement `submit` :
`(ngSubmit)` ne se déclenchait jamais et le clic sur « Lancer la
simulation » partait en **soumission HTML native**.

Conséquence en démonstration : rechargement de la page, **perte de la
session** (le JWT est en mémoire seule, par conception) et aucun appel à
l'API. L'écran paraissait « ne rien faire ».

**Corrigé** : `[formGroup]` déplacé sur le `<form>`, `novalidate` ajouté.
Deux tests couvrent la régression **au niveau du DOM réel** — un
`submit` annulable dispatché sur le vrai `<form>` (on vérifie que
`preventDefault` a bien eu lieu) et un clic sur `button[type=submit]` —
et non plus seulement la méthode du composant, qui passait déjà.

### 1.2 Préparation du planning — mauvais paramètre de recherche

`scripts/prepare-planning-demo.sh` appelait `/api/v1/users?query=…`
alors que `UserAccountController` attend **`q`**. Le paramètre inconnu
était ignoré, la réponse ne contenait pas le formateur, et
`resolve_from_api || true` **masquait l'échec**.

Conséquence : soit un message générique « identifiant de formateur
introuvable », soit — si l'on forçait le passage — un import échouant
plus tard sous la forme opaque `PLAN_TEACHER_NOT_ELIGIBLE`.

**Corrigé** : `q` au lieu de `query` ; `|| true` retiré ; chaque cause
d'échec distinguée (API injoignable, authentification `ADMIN` refusée,
réponse non paginée, compte absent, doublon d'e-mail, compte non
`ACTIVE`, rôle `TEACHER` absent, `publicId` manquant). L'éligibilité
vérifiée est **celle du back-end** (`PlanningReferenceResolver#resolveTeacher`) :
on échoue à la préparation plutôt qu'à l'import. Le test shell passe de
**4 à 11** vérifications, dont un contrôle **statique et dynamique** du
paramètre `q`.

### 1.3 Base de test et base de démonstration confondues

`application-test.yml` résolvait sa base via `${MYSQL_DATABASE}` — la
**même variable** que le runtime. Lancer le back-end de démonstration
avec `MYSQL_DATABASE=esic_connect_demo` faisait donc écrire la suite de
tests **dans la base de démonstration**. Les tests créent des milliers
de comptes et tronquent des tables : le jeu préparé pour la soutenance
était détruit par un simple `./mvnw test`.

Ordre de grandeur constaté : la base applicative `esic_connect`, utilisée
jusqu'ici par la suite, contient **27 105 comptes** — pour un jeu de
démonstration qui en compte 14.

**Corrigé** : le profil `test` lit **`MYSQL_TEST_DATABASE`** (défaut
`esic_test`). La CI impose explicitement `MYSQL_TEST_DATABASE:
esic_connect_ci`.

**Preuve** : suite complète (**811 tests**) lancée avec
`MYSQL_DATABASE=esic_connect_demo` **exporté** ; volumes de
`esic_connect_demo` relevés avant et après — **identiques** sur les 15
tables métier suivies. Vérification refaite après une seconde exécution
complète.

### 1.3bis Fuite résiduelle par le profil `demo` activé en test

La bascule vers `MYSQL_TEST_DATABASE` ne suffisait pas.
`DefaultDemoAccountProvisionerTests` est la seule classe à activer
`@ActiveProfiles({"test", "demo"})`. `application-demo.yml` définit
`spring.datasource.url` sur `${MYSQL_DATABASE}` et, `demo` étant déclaré
**après** `test`, cette valeur l'emportait : cette classe se connectait
encore à la base de **démonstration**.

Comment le trou a été trouvé : le relevé de volumes avant/après ne
montrait **rien** — le provisionnement de démonstration est idempotent
et les comptes existaient déjà, donc aucune ligne n'était ajoutée. La
fuite n'est apparue qu'en cherchant les **URL JDBC réellement ouvertes**
dans le journal de la suite, qui en contenait deux :
`…/esic_test` et `…/esic_connect_demo`.

C'est un rappel utile : une comparaison de volumes prouve l'absence
d'écriture **observable**, pas l'absence de connexion.

**Corrigé** par un `@TestPropertySource` au niveau de la classe, qui
prime sur tout fichier de profil et réimpose `MYSQL_TEST_DATABASE`, sans
modifier le profil `demo` du runtime. Vérifié : avec
`MYSQL_DATABASE=esic_connect_demo` exporté, la classe journalise
`Database: jdbc:mysql://localhost:3306/esic_test`.

### 1.4 Absence de compte `SUPER_ADMIN`

Aucun des cinq comptes amorcés ne portait `SUPER_ADMIN` : les routes qui
lui sont réservées — notamment les plages réseau CIDR de
`SiteNetworkRangeController`, **inaccessibles même à `ADMIN`** —
n'étaient pas démontrables. Le rôle existe depuis la migration `V2` ;
aucun privilège nouveau n'a été introduit.

**Corrigé** : `superadmin@example.test`, **séparé** du compte
d'administration quotidienne (RG-003) — il ne cumule aucun autre rôle,
ce que le test asserte dans les deux sens.

---

## 2. Défaut constaté et **non corrigé**

`GET /api/v1/planning/versions` **sans** le paramètre obligatoire
`classGroupPublicId` renvoie **`500`** au lieu de `400` :
`MissingServletRequestParameterException` n'est pas traduite par
`GlobalExceptionHandler` et retombe en « erreur inattendue ».

Portée : tout endpoint à `@RequestParam` obligatoire, pas seulement
celui-ci. Sans effet sur la démonstration (l'écran envoie toujours le
paramètre), mais c'est un **contrat d'API incorrect** — une requête
malformée du client est rapportée comme une panne serveur.

Non corrigé ici : hors du périmètre autorisé pour cette passe.

---

## 3. Parcours vérifié **par API** le 2 septembre 2026

Back-end profil `demo` sur `esic_connect_demo`, MySQL 8.4 + Redis 7.4
locaux.

| # | Étape | Résultat observé |
|---|---|---|
| 1 | Connexion des **6 comptes** | `200`, jeton émis pour chacun |
| 2 | Simulation import apprenants (8 lignes valides) | `201` — `{total 8, valid 8, error 0, plannedCreate 8}` |
| 3 | Confirmation de l'import | `200` — 8 comptes créés |
| 4 | Simulation import apprenants (fichier à erreurs, 11 lignes) | `201` — `{total 11, valid 7, warning 2, error 2}`, **laissée non confirmée** |
| 5 | Simulation import planning (5 créneaux valides) | `201` — `SIMULATED` |
| 6 | Publication du planning | `200` — `versionNumber 1`, `alreadyPublished false` |
| 7 | `GET /planning/versions?classGroupPublicId=…` | `200` — 1 version `PUBLISHED`, 5 créneaux |
| 8 | Simulation import planning **conflictuel** | `201` — `SIMULATED` |
| 9 | Publication du job conflictuel | **`409 PLAN_BLOCKING_ISSUES`** (refus attendu) |
| 10 | Ouverture de séance + point de contrôle | `204` / point de contrôle `OPEN` |
| 11 | 10 présences manuelles | `201` × 10 |
| 12 | 2 corrections `→ ABSENT` avec motif | `200` × 2 (auditées) |
| 13 | Dépôt de 2 justificatifs + 1 pièce jointe PDF fictive | `201` × 3 |
| 14 | Examen d'un justificatif (`ACCEPTED`) par le RP | `200` — `ABSENT → EXCUSED_ABSENCE` (AC-014) |

**Ce n'est pas une démonstration d'interface.** Aucune manipulation
navigateur n'a été effectuée ni consignée : le statut global reste
`IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`.

---

## 4. Volumes du jeu de démonstration

| Table | n | | Table | n |
|---|---:|---|---|---:|
| `site` / `building` / `room` | 1 / 1 / 3 | | `course_session` | 6 |
| `program` / `program_level` | 1 / 1 | | `attendance_checkpoint` | 6 |
| `academic_year` / `promotion` | 1 / 1 | | `attendance_record` | 10 |
| `class_group` | 1 | | `attendance_correction` | 15 |
| `pedagogical_assignment` | 1 | | `attendance_justification` | 2 |
| `user_account` | 14 | | `justification_attachment` | 1 |
| `student_profile` / `enrollment` | 10 / 10 | | `notification` | 2 |
| `student_import_job` / `_row` | 2 / 19 | | `audit_event` | 67 |
| `planning_import_job` | 2 | | | |
| `planning_version` / `_entry` | 1 / 5 | | | |

Données **strictement fictives** (domaine réservé `example.test`).

---

## 5. Reste à faire à la main

- **Démonstration d'interface de bout en bout** avec captures : seul
  point qui empêche de dépasser `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`.
- Vérifier en navigateur que l'écran `/students/import` soumet bien sans
  rechargement (la régression 1.1 est couverte par test, pas par une
  observation humaine).
- Traduire `MissingServletRequestParameterException` en `400` (§2).
