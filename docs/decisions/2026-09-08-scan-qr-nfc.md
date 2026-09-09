# Note de décision — scan QR dans l'application + fondations NFC de salle

| Élément | Valeur |
|---|---|
| Date | 8 septembre 2026 |
| Branche | `feat/demo-readiness-e2e-ui` |
| Portée | **frontend + documentation uniquement** — zéro ligne de back-end, zéro migration |
| Exigences visées | `EF-ATT-001`, `EF-ATT-002`, `EF-ATT-009`, `EF-ATT-010`, `EF-ATT-008` (canal caméra) ; `EF-ORG-003` (URL d'affiche / NFC) |
| Déploiement | **NON fait** — recette physique NFC + tests téléphones à faire au retour du porteur sur le réseau de la Pi |

## 1. Ce qui existe déjà (constat de code)

- `POST /api/v1/attendance/validate` accepte **exactement l'un** de `token`
  (jeton opaque) ou `shortCode`. Le serveur résout séance, point de
  contrôle, expiration, inscription, anti-rejeu, retard et canal à partir
  du seul JWT + du jeton. **C'est la seule autorité.**
- `POST /api/v1/attendance/room-qr` accepte `roomReference` (jeton
  d'affiche opaque). Le serveur applique le contrôle de plage réseau
  (`EF-ATT-008`), la fenêtre « jusqu'au début » (RG-051), l'inscription et
  l'anti-rejeu.
- Le **QR dynamique du formateur** (`app-qr-display`, écran séance) encode
  déjà **la chaîne opaque brute** du jeton (`AttendanceTokenResponse.token`,
  Base64 URL-safe, 43 caractères, sans remplissage).
- L'**affiche du QR fixe** (`room-qr-poster`) encodait jusqu'ici la
  **référence opaque brute** (`staticQrReference`). Le back-end expose
  déjà `checkInPath` (`/attendance?ref=<jeton>`) dans `RoomStaticQrView`,
  prêt à être encodé en URL.
- L'écran d'émargement apprenant (`attendance-check-in`) proposait la
  **saisie manuelle** du code court et du code d'affiche. Aucun scan
  caméra.
- Deep link : `authGuard` renvoie vers `/login?redirect=<url complète, query comprise>` ;
  `login.safeTarget()` restaure toute route interne commençant par `/`.
  Un lien `/attendance?ref=…` ouvert non connecté revient donc au bon
  endroit après authentification, **sans code ajouté**.
- Le service worker sert `index.html` pour toute navigation, `?ref=`
  compris : rien à changer côté PWA.

## 2. Modèle retenu (types de contenu)

| Contenu scanné | Interprétation | Route appelée |
|---|---|---|
| Chaîne opaque ≈ 43 car. `[A-Za-z0-9_-]` | **jeton d'émargement dynamique** (`DYNAMIC_ATTENDANCE_TOKEN`) | `POST /attendance/validate` `{ token }` |
| URL interne ESIC `…/attendance?ref=<opaque>` | **référence de salle** (`STATIC_ROOM_REFERENCE`), après contrôle d'origine | `POST /attendance/room-qr` `{ roomReference }` |
| Référence d'affiche brute (compat anciennes impressions) | **référence de salle** (`STATIC_ROOM_REFERENCE`) | `POST /attendance/room-qr` `{ roomReference }` |
| Tout le reste (URL externe, QR d'une autre application, texte libre) | `UNSUPPORTED` | aucune — message de rejet |

**Aucune présence n'est créée à la simple ouverture d'une URL.** Ouvrir
`/attendance?ref=…` affiche l'écran d'émargement ; c'est l'appel serveur
déclenché par l'utilisateur (ou par le pré-remplissage du champ après
scan) qui enregistre la présence, sous tous les contrôles serveur
existants.

### Règles de sécurité tenues côté client

- Le frontend **ne décide jamais** qu'une présence est valide : il
  transmet une chaîne opaque et affiche le résultat normalisé du serveur.
- Le contenu scanné n'est **jamais** persisté (`localStorage` /
  `sessionStorage` interdits — RG-093). Il vit en mémoire le temps de
  l'appel.
- Le parseur **n'envoie jamais** de rôle, d'identité d'apprenant ni de
  salle « choisie » : seul le jeton part.
- Une **URL externe** (origine différente de celle de l'application, ou
  chemin autre que `/attendance`) est **refusée** et n'est jamais
  ouverte automatiquement.
- Aucune donnée personnelle dans un QR ou un tag : seul un jeton
  aléatoire opaque.

### Compatibilité navigateur

- `BarcodeDetector` sert d'**accélération** quand il est disponible
  (Android Chrome). **Jamais l'unique voie** : iOS Safari ne l'implémente
  pas.
- Fallback universel : décodage logiciel `jsQR` (Apache-2.0, zéro
  dépendance, ~250 kio non minifié → chunk **paresseux**, hors bundle
  initial) sur les trames vidéo via `<canvas>`.
- Caméra : `getUserMedia({ video: { facingMode: 'environment' } })`, puis
  sélection explicite de la caméra arrière via `enumerateDevices` quand
  plusieurs caméras existent. Exige **HTTPS ou `localhost`** — documenté.

## 3. NFC de salle — fondations, pas d'implémentation Web NFC

- Un tag **NFC NDEF** contient un **enregistrement URL HTTPS** : la
  **même URL** que le QR fixe de la salle (`…/attendance?ref=<opaque>`).
- Aucune dépendance à **Web NFC** (absent d'iOS ; le « tap to open URL »
  natif d'iOS/Android suffit).
- Le parseur partagé `AttendanceCheckInReferenceParser` traite l'URL NFC
  **exactement comme** l'URL QR fixe : un seul chemin de code.
- **Pas de nouveau canal `ROOM_STATIC_NFC`.** Le serveur ne reçoit
  aujourd'hui **aucune information fiable** distinguant un tap NFC d'une
  ouverture d'URL par appareil photo : la même URL est ouverte par
  plusieurs moyens. Le canal fiable reste `ROOM_STATIC_QR` / accès salle
  statique, avec ses contrôles de plage réseau et de fenêtre de séance.
  Cette limite est documentée honnêtement (`docs/deployment/NFC-ROOM-TAGS.md`).

## 4. Ce qui n'est PAS fait dans cette passe

- Aucune route back-end ajoutée ni modifiée : les routes d'émargement
  existantes suffisent.
- Recette physique sur iPhone / Android : `NOT_PERFORMED`.
- Programmation et pose réelles de tags NFC : `NOT_PERFORMED`.
- Déploiement : `NOT_PERFORMED` (interdit cette passe).
