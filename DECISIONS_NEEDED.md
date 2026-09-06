# Décisions métier en attente

Décisions qui relèvent du porteur / responsable pédagogique et qu'un
développeur ne peut pas trancher seul. Chaque entrée décrit l'hypothèse,
l'état réel du dépôt, le conflit, et une recommandation.

---

## D-01 — Ordre et exclusivité des fenêtres d'émargement (Lot I)

**Statut : à trancher. Rien n'a été modifié dans la règle métier.**

### Hypothèse du mandat

« Une seule fenêtre d'émargement ouverte à la fois pour une séance :
l'arrivée doit être fermée avant d'ouvrir un intermédiaire ; un
intermédiaire ouvert doit être fermé avant le suivant ; la fin ne peut
pas s'ouvrir pendant que l'arrivée ou un intermédiaire est ouvert. »

### État réel du dépôt (le dépôt fait foi)

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

### Le cahier des charges

`docs/02` **ne mentionne nulle part** l'exclusivité stricte ni l'ordre
d'ouverture. Il décrit les points de contrôle comme des fenêtres
**indépendantes**, chacune avec ses horaires dérivés du planning
(§16.2–16.5), et traite l'incohérence par `TO_CONFIRM` (§16.3). Le
cahier est donc **silencieux**, et le dépôt implémente un modèle
« points indépendants + jeton unique ».

### Conflit

Imposer l'exclusivité stricte côté serveur :

- **contredirait** le modèle demi-journée du cahier (§16.3) et
- **casserait** des tests d'intégration verts qui l'encodent
  (`DailyAttendanceIntegrationTests`, `AttendanceIntegrationTests`),

ce que les règles du dépôt interdisent (« ne pas affaiblir une
assertion », « en cas de contradiction, le dépôt a raison », « demander
confirmation avant de modifier une règle de gestion »).

### Ce qui a été fait (sans toucher la règle)

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

### Recommandation (à valider par le porteur)

Trois options, par ordre de préférence du rédacteur :

1. **Statu quo + interface explicite (fait).** Le jeton unique garantit
   déjà « un émargement à la fois ». Le `TO_CONFIRM` gère l'ordre. Aucun
   changement de règle. **Recommandé.**
2. **Garde serveur minimale sur `END` seulement** : refuser d'ouvrir un
   point `END` tant qu'un `START` ou un point journalier nommé est
   `OPEN`. Impact : casse au moins un test d'intégration existant
   (`AttendanceIntegrationTests` ouvre `END` avec `START` ouvert) — à
   réécrire **avec l'accord du porteur**, car c'est un changement de
   règle.
3. **Ordre strict complet** (arrivée → pause → après-midi → pause → fin,
   un seul `OPEN` à la fois) : nécessite de réécrire
   `DailyAttendanceIntegrationTests` et de revoir le modèle demi-journée
   du cahier §16.3. Chantier à part entière, décision produit.

**Aucune de ces options n'est appliquée au-delà de l'option 1 tant que
le porteur n'a pas tranché.**
