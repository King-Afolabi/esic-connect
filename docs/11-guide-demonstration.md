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
Amorçage demo : 4 comptes fictifs prêts (admin / formateur / 2 apprenants). …
```

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
guide : `demo-password-1234`).

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
| `scripts/seed-demo.sh` : « Échec de connexion ADMIN » | Back-end pas en profil `demo`, ou mauvais mot de passe | Vérifier `SPRING_PROFILES_ACTIVE=demo` et la même valeur `ESIC_DEMO_PASSWORD` des deux côtés. |
| Back-end : `Too many connections` (MySQL) | Trop de contextes / connexions | `docker compose restart mysql`. |
| `Le code d'émargement a expiré` | TTL court (30 s) | Le formateur ré-affiche un code (« Renouveler maintenant »). |
| Émargement refusé : « Vous n'êtes pas inscrit à une classe de cette séance » | Apprenant sans inscription active dans une classe de la séance | Ré-exécuter `scripts/seed-demo.sh` (crée les inscriptions dans `C-DEMO`). |
| Port `8080` / `4200` déjà utilisé | Autre processus | Arrêter le processus, ou lancer le back-end avec `--server.port=8081` et adapter `API_BASE`. |

---

## 10. Limites de cette tranche

- **Scan caméra : non livré.** Le parcours apprenant fiable est la saisie
  du **code court** affiché par le formateur ; le QR prépare un futur
  scan mobile et sert à la démonstration visuelle.
- La séance est **exceptionnelle** : créée manuellement, sans module
  planning.
- **Un seul point de contrôle** d'émargement par séance.
- Pas de présence manuelle, pas de correction, pas de justificatif, pas
  de calcul de demi-journée, pas de rapport ni d'export CSV.
- Pas de contrôle réseau, pas de QR fixe de salle, pas de WebAuthn.
- Pas de test e2e automatisé Angular → Spring Boot ; la démonstration UI
  de bout en bout est manuelle (le scénario **API** ci-dessus a été
  exécuté et ses statuts relevés).
