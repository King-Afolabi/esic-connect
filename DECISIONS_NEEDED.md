# Registre des décisions métier

Décisions qui relèvent du porteur / responsable pédagogique et qu'un
développeur ne peut pas trancher seul. Chaque entrée décrit l'hypothèse,
l'état réel du dépôt, le conflit, la décision prise et sa vérification.

| Réf | Objet | Statut | Date |
|---|---|---|---|
| D-01 | Ordre et exclusivité des fenêtres d'émargement | **DÉCISION PRISE — OPTION 1 (statu quo)** | 6 septembre 2026 |

Aucune décision n'est en attente à ce jour.

---

## D-01 — Ordre et exclusivité des fenêtres d'émargement (Lot I)

**DÉCISION PRISE : OPTION 1 — STATU QUO.** Validée par le porteur le
6 septembre 2026. Aucun changement de règle métier, aucun changement de
code. Cette entrée n'est plus une action humaine restante : elle est
conservée comme trace de la décision et de sa vérification.

### Décision retenue

Conserver le modèle actuel :

- chaque point de contrôle possède son propre cycle de vie
  (`PLANNED → OPEN → CLOSED` / `CANCELLED`) ;
- plusieurs points de contrôle peuvent être `OPEN` simultanément ;
- **un seul jeton d'émargement fait autorité** à un instant donné pour
  une séance (le pointeur Redis `esic:attendance:session:{id}`) ;
- l'interface indique clairement quel point de contrôle accepte
  actuellement les émargements ;
- les incohérences de séquence restent classées `PARTIAL` ou
  `TO_CONFIRM` par le calcul journalier ;
- **aucun ordre strict** n'est imposé entre les points de contrôle ;
- **le verrouillage de `END`** prévu par l'option 2 n'est pas ajouté.

### Vérification exigée par le mandat (code + tests + documentation)

| Point vérifié | Constat sur le dépôt |
|---|---|
| Ouverture automatique autour de l'horaire prévu | **Non.** Aucun ordonnanceur (`@Scheduled`) n'ouvre un point de contrôle. La seule ouverture automatique : le **premier** point de contrôle (`START`) est ouvert quand le **formateur ouvre la séance** (`CourseSessionService.open()` → `firstCheckpoint(session).open(now)`), déclenché par une action humaine, jamais par une horloge. Les autres points (`MORNING_ARRIVAL`, `MORNING_BREAK_RETURN`, `AFTERNOON_ARRIVAL`, `AFTERNOON_BREAK_RETURN`, `END`, `CUSTOM`) s'ouvrent un par un via `AttendanceCheckpointService.open()`, qui exige séance `OPEN` + point `PLANNED`, **sans aucun contrôle** de l'état des autres points. |
| Fermeture automatique après la fenêtre autorisée | **Non.** `AttendanceCheckpointService.close()` est entièrement manuel (exige le point `OPEN`), sans logique temporelle. La seule fermeture automatique : à la **fermeture de la séance par le formateur** (`CourseSessionService.close()`), tous les points encore `OPEN` sont fermés avec elle. Aucun `@Scheduled` ne balaie les fenêtres expirées. |
| Durées configurées avant / après l'horaire | `app.attendance.token-ttl` — défaut **`PT30S`** (env `ATTENDANCE_TOKEN_TTL`) ; profil de test : `PT1H`. C'est la durée de vie du QR dynamique et du code court, renouvelée à chaque émission. `app.attendance.room-qr-open-before` — défaut **`PT15M`** (env `ATTENDANCE_ROOM_QR_OPEN_BEFORE`) : le **QR fixe de salle** n'est accepté que de `début − 15 min` jusqu'au **début** de la séance (`RoomQrAttendanceService`, refus strict `ROOM_QR_SESSION_STARTED` après le début). Aucune durée « après l'horaire » ne ferme un statut : c'est un contrôle par requête au moment de la validation, pas un ordonnanceur. |
| Comportement si un point reste `OPEN` après l'expiration de sa fenêtre | Le **statut reste `OPEN` indéfiniment** jusqu'à fermeture humaine ou fermeture de la séance. Mais un point `OPEN` sans jeton vivant **n'accepte plus rien** par QR dynamique / code court : le jeton a expiré (`token-ttl`), `resolve()` renvoie vide, la validation est refusée. Le QR fixe de salle, lui, n'est plus accepté après le début de la séance quel que soit le statut du point. **`OPEN` ≠ « accepte les émargements ».** |
| Impossibilité d'utiliser un jeton expiré | **Confirmée.** Clés Redis `token → payload` et `code → token` posées avec TTL ; après expiration, `GET` renvoie `null` → `resolve()` → `Optional.empty()` → refus. Aucun repli en mémoire. Redis indisponible → `503 TOKEN_BACKEND_UNAVAILABLE`, jamais une acceptation dégradée. Couvert par `AttendanceTokenServiceTests.resolveReturnsEmptyForUnknownOrExpired`. |
| Invalidation du précédent jeton lorsqu'un nouveau est émis | **Confirmée.** `AttendanceTokenService.issue()` écrit le nouveau couple, **bascule le pointeur d'autorité de la séance**, puis supprime l'ancien couple. `resolve()` n'accepte un jeton que s'il est **exactement égal au jeton pointé** (et au code court pointé si un code a été présenté) : dès la bascule du pointeur, tout ancien jeton résiduel est refusé même si sa suppression tarde ou échoue. Émettre un jeton pour un **autre point de contrôle** de la même séance fait aussi tourner le pointeur → un seul point accepte réellement les émargements à un instant donné, même si plusieurs sont `OPEN`. Couvert par `AttendanceTokenServiceTests` : `issueRotatesAndInvalidatesThePreviousPair`, `residualOldTokenIsRejectedWhenSessionPointerHasRotated`, `normalRotationLeavesOnlyTheNewPairUsable`. |

**Conclusion de la vérification.** Le dépôt implémente exactement le
modèle de l'option 1 : « points de contrôle indépendants + jeton
d'autorité unique ». Seule l'**expiration du jeton** existe ; il n'y a
**pas** de fermeture automatique du statut `OPEN`. Conformément au
mandat, aucune fermeture automatique n'a été inventée sans
spécification ; la distinction entre **statut `OPEN`** et **jeton
utilisable** est documentée ci-dessus, dans `docs/03-architecture.md`
(`DEC-D01`) et dans `docs/CURRENT-STATE.md`.

**Tests temporels.** La clause « si la fermeture automatique est déjà
présente, compléter les tests temporels » ne s'applique pas : elle n'est
pas présente. L'expiration du jeton et la rotation sont déjà couvertes
par `AttendanceTokenServiceTests` (20 tests) ; aucun test nouveau n'était
requis.

---

### Annexe — analyse d'origine (conservée)

#### Hypothèse du mandat (Lot I)

« Une seule fenêtre d'émargement ouverte à la fois pour une séance :
l'arrivée doit être fermée avant d'ouvrir un intermédiaire ; un
intermédiaire ouvert doit être fermé avant le suivant ; la fin ne peut
pas s'ouvrir pendant que l'arrivée ou un intermédiaire est ouvert. »

#### État réel du dépôt (le dépôt fait foi)

1. **Chaque point de contrôle a son cycle de vie propre**
   `PLANNED → OPEN → CLOSED` / `CANCELLED`
   (`AttendanceCheckpointStatus`, `AttendanceCheckpointService`).
   `open()` exige : séance `OPEN` + point de contrôle `PLANNED`. **Aucun
   contrôle** que les autres points soient `CLOSED`. Plusieurs points
   peuvent donc être `OPEN` simultanément — c'est le comportement testé.
2. **Le point `START` est ouvert d'office à l'ouverture de la séance**
   (`CourseSessionService`, « START … est ouvert d'office »). Un test
   d'intégration (`AttendanceIntegrationTests`) ouvre ensuite un point
   `END` alors que `START` est encore `OPEN`, sans échec attendu.
3. **La vraie exclusion mutuelle est le jeton d'émargement**, pas le
   statut : `AttendanceTokenService` ne tient qu'**un** pointeur
   d'autorité par séance
   (`esic:attendance:session:{id} -> token\ncode\ncheckpointId`). Émettre
   un jeton pour un autre point de contrôle **invalide immédiatement** le
   précédent. Un seul point de contrôle accepte donc réellement les
   émargements à un instant donné, même si plusieurs sont `OPEN`.
4. **L'incohérence de séquence est gérée après coup, pas empêchée à la
   porte** : `docs/02 §16.3` et `EF-ATT-004` (CURRENT-STATE) —
   « un retour de pause sans l'arrivée qui le précède est une incohérence
   → `TO_CONFIRM` ; l'inverse est incomplet → `PARTIAL` ». Le calcul de
   demi-journée est **conçu** pour tolérer des points de contrôle tous
   ouverts et validés en lot.
5. **`DailyAttendanceIntegrationTests`** encode ce modèle : il crée puis
   ouvre `MORNING_ARRIVAL`, `MORNING_BREAK_RETURN`, `AFTERNOON_ARRIVAL`,
   `AFTERNOON_BREAK_RETURN` sur la même séance **sans jamais les fermer
   entre deux**, pour produire les cas `PARTIAL` / `TO_CONFIRM` du
   cahier.

#### Le cahier des charges

`docs/02` **ne mentionne nulle part** l'exclusivité stricte ni l'ordre
d'ouverture. Il décrit les points de contrôle comme des fenêtres
**indépendantes**, chacune avec ses horaires dérivés du planning
(§16.2–16.5), et traite l'incohérence par `TO_CONFIRM` (§16.3). Le
cahier est donc **silencieux**, et le dépôt implémente un modèle
« points indépendants + jeton unique ».

#### Conflit (pourquoi les options 2 et 3 n'ont pas été retenues)

Imposer l'exclusivité stricte côté serveur :

- **contredirait** le modèle demi-journée du cahier (§16.3) et
- **casserait** des tests d'intégration verts qui l'encodent
  (`DailyAttendanceIntegrationTests`, `AttendanceIntegrationTests`),

ce que les règles du dépôt interdisent (« ne pas affaiblir une
assertion », « en cas de contradiction, le dépôt a raison », « demander
confirmation avant de modifier une règle de gestion »).

#### Ce qui avait déjà été fait (sans toucher la règle), et reste en place

- **Interface** (`session-detail`) : quand plusieurs points de contrôle
  sont `OPEN`, une note explique qu'**un seul accepte les émargements à
  la fois** et qu'afficher un code pour un autre ferme le précédent ; le
  code affiché indique désormais **à quel point de contrôle** il
  appartient (« Émargement actif : « … » »).
- Les protections serveur **déjà en place** sont conservées et
  documentées : séance doit être `OPEN` ; point de contrôle doit être
  `PLANNED` pour être ouvert ; transition concurrente perdante
  (`@Version`) → `409 ATT_CHECKPOINT_INVALID_STATE`, jamais `500` ;
  rotation de jeton à autorité unique ; `RG-055` — une validation unique
  par apprenant et par point de contrôle.

#### Options telles qu'elles avaient été présentées

1. **Statu quo + interface explicite.** Le jeton unique garantit déjà
   « un émargement à la fois ». Le `TO_CONFIRM` gère l'ordre. Aucun
   changement de règle. **← RETENUE.**
2. **Garde serveur minimale sur `END` seulement** : refuser d'ouvrir un
   point `END` tant qu'un `START` ou un point journalier nommé est
   `OPEN`. Impact : casse au moins un test d'intégration existant. Non
   retenue.
3. **Ordre strict complet** (arrivée → pause → après-midi → pause → fin,
   un seul `OPEN` à la fois) : réécriture de
   `DailyAttendanceIntegrationTests` et révision du modèle demi-journée
   du cahier §16.3. Non retenue.
