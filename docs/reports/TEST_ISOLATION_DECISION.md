# Décision — isolation de la suite de tests (FINAL-021)

| Élément | Valeur |
|---|---|
| Checkpoint | F3 — qualité / outillage |
| Date | 31 août 2026 |
| Décision | **Migration vers Testcontainers différée.** Le profil `test` reste sur l'infrastructure locale (`compose.yaml`). |
| Statut FINAL-021 | `PARTIAL` — dette assumée et documentée, non résolue dans ce lot |

## Constat

- **31 classes `@SpringBootTest`** + **16 classes `@DataJpaTest`** ;
  **29 classes portent une `@TestConfiguration` imbriquée**.
- Spring met en cache **un contexte applicatif (et un pool HikariCP) par
  combinaison distincte de configuration** → dans la pratique, un
  contexte par classe portant sa propre `@TestConfiguration`.
- Le profil `test` (`src/test/resources/application-test.yml`) pointe
  aujourd'hui vers **les mêmes conteneurs MySQL / Redis que le profil
  `local`** (aucune base isolée). Le pool HikariCP de test est déjà
  plafonné (`maximum-pool-size: 4`, `minimum-idle: 0`, `idle-timeout`
  court) pour rester sous `max_connections` de MySQL 8 (151) malgré
  l'accumulation de contextes.
- Conséquences réelles :
  1. un `./mvnw test` local **écrit dans la base de développement**
     (`esic_connect`) — les tests d'intégration ne tronquent pas, d'où
     des lignes résiduelles (déjà relevé dans l'audit F1 §4.5) ;
  2. fragilité potentielle « Too many connections » si le nombre de
     contextes cachés continue de croître ;
  3. un test qui dépend de l'ordre ou de l'état d'un autre reste
     possible.

## Pourquoi différer

1. **Blast radius.** Introduire `@Testcontainers` proprement implique :
   - remplacer la résolution `${MYSQL_*}` / `${REDIS_*}` du profil `test`
     par des propriétés dynamiques (`@DynamicPropertySource`) — donc
     toucher **une classe de base partagée** dont héritent 47 classes,
     ou ajouter un `ContextCustomizerFactory` ;
   - garantir que Flyway s'exécute une fois par conteneur et non par
     contexte ;
   - réconcilier les **29 `@TestConfiguration` imbriquées** avec un
     conteneur partagé unique (sinon on multiplie les conteneurs et le
     temps de démarrage explose).
   Ce n'est pas une modification « tests seulement sans risque
   fonctionnel » telle que cadrée pour F3.

2. **Stabilité exigée.** La consigne F3 est explicite : *n'appliquer la
   migration que si les 682 tests restent stables ; sinon documenter le
   report.* Le risque de casser des invariants sensibles à la
   transaction (rollback total de l'import — T3 —, e-mail `AFTER_COMMIT`
   — T4 —, audit sans trace si rollback — T5 —, cas concurrents
   `REQUIRES_NEW`) est réel et non maîtrisable dans le temps d'un
   checkpoint de finalisation.

3. **Bénéfice partiellement déjà obtenu.** Le pool plafonné + `minimum-idle: 0`
   contient déjà la saturation de connexions ; la CI utilise une base
   dédiée (`esic_connect_ci`, service GitHub Actions éphémère) — donc en
   CI l'isolation existe **de fait** (conteneur jeté à chaque run). Le
   déficit réel est **local**.

## Contournement en attendant

- **En CI** : déjà isolé (base `esic_connect_ci` d'un service éphémère,
  détruit après le job — voir `.github/workflows/backend-ci.yml`).
- **En local** : pour ne pas polluer `esic_connect`, lancer la suite
  contre une base jetable :
  ```bash
  # créer une base de test dédiée (compte root du conteneur)
  docker exec -i esic-connect-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
    -e "CREATE DATABASE IF NOT EXISTS esic_test; GRANT ALL ON esic_test.* TO '$MYSQL_USER'@'%';"
  MYSQL_DATABASE=esic_test ./mvnw clean test
  # puis, si souhaité :
  docker exec -i esic-connect-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
    -e "DROP DATABASE esic_test;"
  ```

## Incident observé — `EnrollmentDirectoryTests` sous `TZ=UTC` (1er septembre 2026)

Pendant la 1re passe corrective du grand lot G1, **un** run
`TZ=UTC ./mvnw clean test` a produit **deux erreurs** dans
`EnrollmentDirectoryTests` : réponse HTTP nulle sur `POST
/api/v1/auth/login`, puis `NullPointerException` dans la fixture
`adminToken()` (`login.get("accessToken")` sur un corps nul). Le run
`TZ=UTC` **suivant** de la même passe, puis tous les runs de la 2e passe,
sont repassés verts (809 puis 811 tests, 0 échec).

Campagne bornée de vérification (2e passe corrective) :
`EnrollmentDirectoryTests` **seul**, sous `TZ=UTC`, **5 répétitions
isolées** → **5/5 vertes** (`Tests run: 3, Failures: 0, Errors: 0` à
chaque itération).

**Qualification retenue** : *« incident intermittent observé une fois,
non reproduit lors des répétitions et du run final ; cause non
déterminée »*. Ce n'est **pas** un « problème d'infrastructure
confirmé ».

Mécanisme **plausible mais non prouvé** : `adminToken()` fait un
`saveAndFlush` puis un appel HTTP réel `/auth/login` ; un corps de
réponse nul correspond à une connexion avortée avant réception complète.
La contention du pool HikariCP **plafonné à 4** partagé entre la
vingtaine de contextes `@SpringBootTest` cachés lors d'un `clean test`
**complet** (voir la section « Constat » ci-dessus) peut provoquer un
échec transitoire d'acquisition de connexion sur la route de login
(BCrypt + accès MySQL). La correction structurelle est la migration
Testcontainers déjà planifiée ci-dessous ; aucune modification de code ou
de test n'a été faite pour cet incident (non reproductible).

## Travail à planifier (hors périmètre de ce lot)

1. Ajouter `org.testcontainers:mysql` + `:junit-jupiter` (scope `test`).
2. Créer une classe de base `AbstractIntegrationTest` avec conteneurs
   MySQL + Redis **statiques partagés** et `@DynamicPropertySource`.
3. Faire hériter les 31 `@SpringBootTest` de cette base ; fusionner les
   `@TestConfiguration` imbriquées récurrentes dans une configuration de
   test partagée pour limiter le nombre de contextes cachés.
4. Vérifier : total de tests inchangé, `ModularityTests` vert, invariants
   T1–T6, cas concurrents, temps de suite acceptable.
5. Retirer la dépendance à `compose.yaml` pour `./mvnw test`.
