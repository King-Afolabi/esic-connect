# Guide de démonstration — Parcours d'émargement

Ce guide décrit une démonstration **locale** du parcours
« séance exceptionnelle → ouverture → émargement → présences → fermeture »
(modules back-end `coursesession` et `attendance`, front-end `/sessions`
et `/attendance`).

Toutes les commandes ci-dessous ont été exécutées le 30 août 2026 sur
macOS (Docker Desktop, OpenJDK 21, Node 24). Le scénario API a été relevé
en statuts HTTP réels ; la démonstration UI de bout en bout se fait
manuellement dans deux navigateurs (ou deux fenêtres privées).

---

## 1. Prérequis

| Outil | Version testée |
|---|---|
| Java (Temurin) | 21 |
| Node.js | 24 |
| Docker + Docker Compose | Docker Desktop |
| `curl`, `jq` | pour `scripts/seed-demo.sh` uniquement |

---

## 2. Configuration `.env`

Copier le modèle et compléter les valeurs **locales** (aucun secret n'est
commité) :

```bash
cp .env.example .env
```

Renseigner au minimum dans `.env` :

- `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD` : mots de passe
  locaux quelconques ;
- laisser `JWT_SECRET=` vide dans `.env` : il est fourni à la main
  ci-dessous (étape 4) ;
- `ESIC_DEMO_PASSWORD` : voir l'étape 3.

### 3. Générer un `JWT_SECRET` et le mot de passe de démonstration

```bash
# Clé de signature HS256 (≥ 32 octets) — à exporter dans le shell qui
# lance le back-end, jamais dans .env commité.
export JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"

# Mot de passe des comptes de démonstration (≥ 12 caractères).
export ESIC_DEMO_PASSWORD='demo-password-1234'
```

---

## 4. Démarrage

### 4.1 Infrastructure (MySQL, Redis, Mailpit, Mosquitto)

```bash
docker compose config      # valide la syntaxe et les variables
docker compose up -d
docker compose ps          # mysql et redis doivent être "healthy"
```

### 4.2 Back-end (profil `demo`)

```bash
cd backend
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # ou votre chemin OpenJDK 21
set -a && source ../.env && set +a
export SPRING_PROFILES_ACTIVE=demo
# JWT_SECRET et ESIC_DEMO_PASSWORD proviennent de l'étape 3
./mvnw -q spring-boot:run
```

Au démarrage, `DemoDataInitializer` (actif **uniquement** sous le profil
`demo`) crée 4 comptes fictifs et journalise :

```
Amorçage demo : 4 comptes fictifs synchronisés (admin / formateur / 2 apprenants)
— statut ACTIVE et mot de passe aligné sur la valeur courante de ESIC_DEMO_PASSWORD. …
```

> **Base MySQL persistante.** Le volume MySQL survit d'un démarrage à
> l'autre. À **chaque** amorçage sous le profil `demo`, les 4 comptes
> fictifs sont *resynchronisés* : leur statut est ramené à `ACTIVE`
> (suspension éventuelle levée) et leur mot de passe est réaligné sur la
> valeur **courante** de `ESIC_DEMO_PASSWORD` — le hachage n'est réécrit
> que s'il ne correspond plus (idempotent avec le même mot de passe).
> Vous pouvez donc changer `ESIC_DEMO_PASSWORD` entre deux démarrages
> sans recréer la base ni les comptes. Rôles, profils apprenants et
> inscriptions sont conservés. **Ce comportement de synchronisation
> n'existe que sous le profil `demo`** ; sous `local`, `test` ou en
> production, aucun compte n'est créé ni modifié de cette façon.

### 4.3 Jeu de données de démonstration

Dans un autre terminal, depuis la racine du dépôt :

```bash
API_BASE=http://localhost:8080 ESIC_DEMO_PASSWORD='demo-password-1234' \
  ./scripts/seed-demo.sh
```

Le script est **idempotent** : il crée (ou récupère) un site, une
formation, un niveau, une année, une promotion, une classe `C-DEMO`, deux
profils apprenants, deux inscriptions et **une séance `PLANNED`**. Une
seconde exécution affiche les mêmes identifiants et « séance … (déjà
présente, PLANNED) ».

Chaque appel logique n'émet **qu'une seule requête POST** : le helper
`http_post` effectue une requête unique (corps dans un fichier temporaire
nettoyé par `trap`, statut capturé séparément) et refuse tout `HTTP >= 400`
par défaut ; seul un `409` est toléré dans les fonctions `ensure_*`, qui
retrouvent alors la ressource exacte par son code et échouent si elle
reste introuvable. La séance de démonstration n'ayant pas de contrainte
d'unicité, elle n'est POSTée qu'après un `GET` confirmant son absence.
Non-régression : `bash scripts/test/test-seed-demo.sh` (faux `curl`
déterministe) vérifie « un appel logique = un POST » et « une seconde
exécution ne crée ni séance ni inscription supplémentaire ».

Vérification locale du 30 août 2026 sur une base MySQL vierge dédiée
(`esic_demo_verify`, supprimée ensuite ; `esic_connect` non touchée) :
`course_session` = 0 avant, **1** après le 1ᵉʳ seed, **1** après le 2ᵈ
(mêmes `public_id`).

### 4.4 Front-end

```bash
cd frontend
npm ci
npm start        # http://localhost:4200 (proxifie /api vers :8080)
```

---

## 5. URLs

| Élément | URL |
|---|---|
| Application (front) | http://localhost:4200 |
| Santé du back-end | http://localhost:8080/actuator/health |
| Documentation API | http://localhost:8080/swagger-ui.html |
| Mailpit (emails locaux) | http://localhost:8025 |

---

## 6. Comptes de démonstration

Toutes les adresses sont **fictives** (domaine réservé `example.test`).
Mot de passe commun : la valeur de `ESIC_DEMO_PASSWORD` (par défaut du
guide : `demo-password-1234`). Elle est réappliquée aux 4 comptes à
chaque démarrage sous le profil `demo` (voir l'encadré § 4.2), même sur
une base MySQL déjà peuplée par une session précédente.

| Rôle | Adresse |
|---|---|
| `ADMIN` | `admin@example.test` |
| `TEACHER` | `formateur@example.test` |
| `STUDENT` | `apprenant1@example.test` |
| `STUDENT` | `apprenant2@example.test` |

---

## 7. Scénario de démonstration (UI, deux sessions)

1. **Connexion `ADMIN`** (`admin@example.test`) → menu **Séances** →
   la séance `Atelier émargement (démo)` apparaît en statut *Planifiée*.
   *(La séance peut aussi être créée via **Séances → Nouvelle séance** :
   formateur `formateur@example.test`, classe `C-DEMO`, date/heures, motif
   obligatoire.)*
2. **Connexion `TEACHER`** (`formateur@example.test`, autre navigateur) →
   **Séances** → ouvrir la fiche de la séance → **Ouvrir la séance** →
   confirmer. Le statut passe à *Ouverte*.
3. Sur la fiche ouverte, section **Émargement en cours** → **Afficher un
   code d'émargement** : le QR et le **code court** (8 caractères)
   s'affichent, avec une date d'expiration. Le code se renouvelle
   automatiquement avant expiration.
4. **Connexion `STUDENT` 1** (`apprenant1@example.test`, fenêtre privée) →
   menu **Émargement** → saisir le code court → **Valider ma présence** →
   confirmation « Présence enregistrée ».
5. Retour côté `TEACHER` : section **Présences** → **Rafraîchir** →
   l'apprenant 1 apparaît (numéro étudiant, heure, canal *Code court*),
   « 1 présent sur 2 attendus ».
6. `STUDENT` 1 tente une **seconde** validation avec le même code →
   message contrôlé « Votre présence a déjà été enregistrée pour ce point
   de contrôle. ».
7. **Connexion `STUDENT` 2** (`apprenant2@example.test`) → **Émargement** →
   saisir le code court courant → succès. Côté `TEACHER`, « 2 présents
   sur 2 attendus ».
8. Côté `TEACHER` : **Fermer la séance** → confirmer. Le QR disparaît, le
   statut passe à *Clôturée*.
9. `STUDENT` 2 tente une nouvelle validation → message « Ce code
   d'émargement est invalide ou a expiré. » (jeton purgé de Redis).

### Scénario équivalent par API (statuts HTTP réels relevés)

| Étape | Appel | Résultat |
|---|---|---|
| ADMIN lit la séance | `GET /api/v1/sessions/{id}` | `200` |
| TEACHER ouvre | `POST /api/v1/sessions/{id}/open` | `204` |
| TEACHER émet un jeton | `POST /api/v1/sessions/{id}/attendance-token` | `200` (code de 8 caractères, `ttlSeconds` 30) |
| STUDENT 1 valide (code court) | `POST /api/v1/attendance/validate` `{shortCode}` | `200` (`source=SHORT_CODE`) |
| STUDENT 1 revalide | idem | `409 ATT_ALREADY_RECORDED` |
| STUDENT 2 valide (jeton opaque) | `POST /api/v1/attendance/validate` `{token}` | `200` (`source=DYNAMIC_QR`) |
| TEACHER liste les présences | `GET /api/v1/sessions/{id}/attendance` | `200` (2/2) |
| STUDENT 1 liste les séances | `GET /api/v1/sessions` | `403` |
| TEACHER ferme | `POST /api/v1/sessions/{id}/close` | `204` |
| STUDENT valide après fermeture | `POST /api/v1/attendance/validate` | `409 ATT_TOKEN_INVALID` |
| TEACHER émet un jeton après fermeture | `POST /api/v1/sessions/{id}/attendance-token` | `409 ATT_SESSION_CLOSED` |
| (Redis en pause) TEACHER émet un jeton | idem | `503 ATT_TOKEN_BACKEND_UNAVAILABLE` |

Invariant de rotation Redis (relevé le 30 août 2026 sur `esic_demo_verify`) :
après deux émissions successives sur une séance ouverte, une validation
avec **l'ancien** code court → `409` (il n'est plus le couple courant
désigné par le pointeur `session -> token\ncode`) ; avec le **code
courant** → `200`.

---

## 8. Captures recommandées pour le rapport

1. Écran de connexion.
2. Tableau de bord (`ADMIN`).
3. Liste des séances.
4. Formulaire de création d'une séance.
5. Fiche de séance *Ouverte* avec QR + code court.
6. Écran **Émargement** de l'apprenant (confirmation de présence).
7. Section **Présences** côté formateur (2/2).
8. Fiche de séance *Clôturée*.

---

## 9. Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| Back-end refuse de démarrer, `JWT_SECRET doit contenir au moins 32 octets` | `JWT_SECRET` absent / trop court | Refaire l'étape 3 dans le shell qui lance `spring-boot:run`. |
| Back-end refuse de démarrer, `ESIC_DEMO_PASSWORD … obligatoire` | Variable absente ou < 12 caractères | `export ESIC_DEMO_PASSWORD='…'` (≥ 12 caractères). |
| `503 ATT_TOKEN_BACKEND_UNAVAILABLE` à l'émission d'un jeton | Redis arrêté / injoignable | `docker compose up -d redis` ; vérifier `docker compose ps`. |
| `scripts/seed-demo.sh` : « Échec de connexion ADMIN » | Back-end pas en profil `demo`, `ESIC_DEMO_PASSWORD` différent entre le back-end et le script, ou back-end lancé avant la mise à jour de la variable | Vérifier `SPRING_PROFILES_ACTIVE=demo` et la **même** valeur `ESIC_DEMO_PASSWORD` des deux côtés, puis **redémarrer le back-end** : l'amorçage `demo` réaligne alors les 4 comptes sur cette valeur (base MySQL déjà peuplée incluse). |
| Back-end : `Too many connections` (MySQL) | Trop de contextes / connexions | `docker compose restart mysql`. |
| `Le code d'émargement a expiré` | TTL court (30 s) | Le formateur ré-affiche un code (« Renouveler maintenant »). |
| Émargement refusé : « Vous n'êtes pas inscrit à une classe de cette séance » | Apprenant sans inscription active dans une classe de la séance | Ré-exécuter `scripts/seed-demo.sh` (crée les inscriptions dans `C-DEMO`). |
| Port `8080` / `4200` déjà utilisé | Autre processus | Arrêter le processus, ou lancer le back-end avec `--server.port=8081` et adapter `API_BASE`. |

---

## 10. Tranche V10 — gestion de l'assiduité et reporting

La tranche `feature/attendance-management-and-reporting` ajoute, au-dessus
du parcours d'émargement ci-dessus :

### 10.1 Nouveaux parcours démontrables

1. **Plusieurs points de contrôle par séance.** Sur la fiche séance
   (`/sessions/:id`), section « Points de contrôle » : le point `START`
   (« Arrivée ») est créé et ouvert avec la séance ; « Ajouter » crée un
   point `CUSTOM` / `END` ; chaque point s'ouvre / se ferme / s'annule
   (motif en confirmation en ligne). Le QR cible le point de contrôle
   **ouvert sélectionné** ; le jeton est émis par point de contrôle
   (`POST /api/v1/sessions/{id}/checkpoints/{cpId}/attendance-token`).
2. **Présence manuelle** (`POST .../attendance/manual`) : bouton
   « Saisie manuelle » — **sélecteur d'apprenant inscrit** (`mat-select`)
   alimenté par `GET /api/v1/sessions/{id}/attendance/candidates`
   (inscriptions actives des classes de la séance, dédupliquées ; nom +
   numéro étudiant + code de classe, jamais d'e-mail ni d'identifiant
   SQL ; états chargement / vide / erreur / 403) + statut (`PRESENT` /
   `LATE` / `ABSENT`) + motif obligatoire. L'identifiant d'inscription ne
   vit que dans la valeur du contrôle (jamais dans l'URL ni un storage) ;
   la liste se recharge si la séance change et s'efface à la perte du
   contexte de gestion (correctif PR #22 §2).
3. **Correction / annulation logique** (`POST .../attendance/{aid}/correct`
   `|/cancel`) : sur chaque ligne de présence, motif obligatoire,
   confirmation en ligne ; la ligne est conservée (`CANCELLED`).
   « Historique » (`GET .../attendance/{aid}/history`) affiche l'append-only
   `attendance_correction`.
4. **Justificatif métier SANS fichier** — apprenant : `/my-attendance`
   (« Mes présences ») ; sur une absence d'un point de contrôle fermé,
   « Déposer un justificatif » (catégorie / référence externe /
   commentaire ; `POST /api/v1/me/attendance/justifications`). Détail
   `/my-attendance/:id` : historique + modification tant que `PENDING`.
5. **Examen des justificatifs** — gestion : `/attendance-management/justifications`,
   « Examiner » → accepter (présence `ABSENT → EXCUSED_ABSENCE`) ou
   refuser (motif obligatoire ; présence reste `ABSENT`).
   `POST /api/v1/attendance/justifications/{id}/review`.
6. **Rapports + synthèse** — `/attendance-management` : cartes de synthèse
   (taux de présence en demi-journées, retards, absences injustifiées /
   justifiées, demi-journées en contexte d'alternance `COMPANY` exclues,
   `UNKNOWN` signalées, justificatifs en attente) ; rapports par séance /
   classe / apprenant, filtres `from` / `to` / `classGroup` /
   `studentProfile`, pagination serveur.
7. **Export CSV** — bouton « Exporter (CSV) » sur chaque rapport :
   `GET /api/v1/attendance/reports/{sessions|classes|students}/export`
   → `text/csv` (UTF-8 + BOM, séparateur `;`), **neutralisation
   d'injection de formule** (une cellule débutant par `=` `+` `-` `@` est
   préfixée d'une apostrophe), aucun email, aucun identifiant SQL. Les
   rapports affichent le **code lisible** de la classe (ex. `C-DEMO`),
   jamais l'UUID (correctif PR #22 §7).
8. **Tri serveur borné** — chaque rapport propose « Trier par »
   (`mat-select`) restreint à une liste blanche par rapport
   (`startsAt` / `attendanceRate` / `presentCount` pour les séances,
   `classCode` / `attendanceRate` / `absentHalfDays` pour les classes,
   `lastName` / `studentNumber` / `attendanceRate` / `absentHalfDays`
   pour les apprenants), au format `field,asc|desc`. Un champ ou sens
   inconnu renvoie `400 ATT_REPORT_INVALID_SORT` (correctif PR #22 §6).
9. **Export CSV borné à la séance** — sur la fiche séance, bouton
   « Exporter les présences de cette séance (CSV) » :
   `GET /api/v1/sessions/{id}/attendance/export` (mêmes protections CSV,
   contrôle fin identique à la consultation — un formateur affecté
   exporte sa séance —, `STUDENT` refusé, nom de fichier contrôlé ;
   correctif PR #22 §8).

### 10.2 Vérifications relevées

**Tranche V10 initiale (30 août 2026)** : back-end `./mvnw clean test` →
532 tests ; front `npm test` → 444 tests ; scénario API V10 relevé en
statuts HTTP (profil `demo`). Une démonstration `curl` manuelle sur base
isolée n'avait alors pas été exécutée (le compte `esic_app` n'a pas
`CREATE DATABASE`).

**Passe corrective PR #22 (30 août 2026)** :

- Back-end `./mvnw clean test` (MySQL 8.4, Redis 7) → **545 tests, 0
  échec**, `ModularityTests` vert, migration `V10` appliquée (schéma en
  version 10).
- Front-end `npm test -- --watch=false` → **451 tests, 0 échec** ;
  `npm run lint` « All files pass linting » ; `npm run build` bundle
  initial **482,24 kB** brut / **122,57 kB** transféré (seuil 500 kB).
- **Démonstration locale réelle** exécutée contre le back-end (profil
  `demo`) sur un **schéma jetable `esic_pr22_verify`** créé avec le
  compte root du conteneur MySQL, puis **supprimé** (la base principale
  `esic_connect` n'est jamais touchée). Séquence relevée en statuts HTTP
  et champs non sensibles (aucun mot de passe, JWT, jeton Redis ni code
  court réel affiché) :
  1. `scripts/seed-demo.sh` → site / formation / classe `C-DEMO` / 2
     apprenants / 2 inscriptions / 1 séance `PLANNED` ;
  2. ouverture de la séance `204` ;
  3. création + ouverture d'un 2ᵉ point de contrôle `CUSTOM` `201` /
     `204` ;
  4. jeton émis **sur ce point de contrôle** `200` (`checkpointPublicId`
     ciblé, `ttlSeconds` > 0) ;
  5. pointage apprenant 1 (code court) `200` (`status=PRESENT`,
     `source=SHORT_CODE`), re-pointage `409 ATT_ALREADY_RECORDED` ;
  6. `GET .../attendance/candidates` `200` (2 candidats ; champs
     `classCode, enrollmentPublicId, firstName, lastName, studentNumber,
     studentProfilePublicId` — aucun e-mail ni id SQL), présence manuelle
     via l'identifiant renvoyé `201` (`ABSENT`, `MANUAL`) ;
  7. correction `200` (`PRESENT`), historique `CREATED_MANUALLY >
     STATUS_CORRECTED` ;
  8. fermeture `204`, dépôt de justificatif apprenant 2 `201`
     (`PENDING`, `attendanceStatus=ABSENT`, identité masquée) ;
  9. examen administration `ACCEPTED` `200` (présence →
     `EXCUSED_ABSENCE`), formateur sur `review` `403` ;
  10. rapports JSON bornés à la classe : `classes` / `students` /
      `sessions` `200`, `classCode = C-DEMO` (jamais un UUID) ;
  11. tri `lastName,desc` `200`, `attendanceRate,asc` `200`, `email,asc`
      `400 ATT_REPORT_INVALID_SORT`, `startsAt,sideways` `400` ;
  12. export CSV global `200` (`Content-Disposition` + BOM + en-tête
      `session_id;titre;…`), export CSV de séance par le **formateur
      affecté** `200` (`filename="attendance-session_<uuid>.csv"`),
      `STUDENT` sur l'export de séance `403` ;
  13. cellule de titre `=SUM(A1:A9)+cmd|'/c calc'` → export CSV : forme
      neutralisée `'=SUM(A1:A9)` présente, forme brute `;=SUM(A1:A9)`
      absente ;
  14. après fermeture : `validate` avec l'ancien jeton `409`,
      `attendance-token` sur séance fermée `409` ;
  15. `GET .../candidates` — `ADMIN` `200`, `TEACHER` affecté `200`,
      `STUDENT` `403`, anonyme `401` ;
  16. contrôle §1 : `time_zone_id` d'une séance forcé à `Invalid/Zone`
      (SQL root) → `GET .../reports/summary` **`500` générique** (la
      valeur invalide n'est pas renvoyée, aucun chiffre trompeur) ;
      restauration → `200`.
  À la fin : back-end arrêté proprement, schéma `esic_pr22_verify`
  supprimé et privilège révoqué. La démonstration **UI de bout en bout**
  n'est pas exécutée automatiquement (parcours API + composants couverts
  par 451 tests front).

### 10.3 Limites de la tranche V10

- **Scan caméra : non livré** (le parcours fiable reste le code court).
- La séance est **exceptionnelle** (sans module planning).
- **Contexte d'alternance `UNKNOWN`** (aucun rythme affecté à la classe) :
  une demi-journée non émargée est comptée en `unknownHalfDays`,
  **jamais** en `absentHalfDays` — un rapport « utile » suppose un
  rythme d'alternance affecté à la classe.
- **Sélecteur de candidats à la présence manuelle** : fondé sur
  l'effectif **actif** des classes de la séance (pas de filtrage par date
  fine — cohérent avec ce qu'accepte la saisie manuelle) ; nom + numéro
  étudiant exposés au formateur, jamais l'e-mail.
- **`TEACHER` exclu des rapports agrégés** (il consulte les présences de
  ses séances via `GET /sessions/{id}/attendance`).
- **Justificatif = métadonnée métier**, aucune pièce jointe.
- Pas de contrôle réseau, pas de QR fixe de salle, pas de WebAuthn, pas
  de test e2e automatisé Angular → Spring Boot.
- Un `timeZoneId` **persistant invalide** fait échouer un rapport en
  `500` contrôlé (valeur jamais exposée) plutôt que de produire des
  chiffres trompeurs — état interne corrompu, non atteignable par l'API
  (correctif PR #22 §1).
