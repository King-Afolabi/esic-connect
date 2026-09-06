# Captures — rapport de campagne

Générées par `tests/13-accessibility-axe.spec.ts`
(`npx playwright test --project=chromium tests/13-accessibility-axe.spec.ts`),
commit de référence `04b727d`, contre `http://localhost:4200` (front
`ng serve`, back `:8080` profil `local`).

Nommage : `pub-<zone>-<viewport>.png`.

| Fichier | Route | Rôle | Viewport | État présenté | Résultat attendu |
|---|---|---|---|---|---|
| `pub-login-desktop-1440x900.png` | `/login` | anonyme | 1440×900 | connexion au repos | carte centrée, identité ESIC, formulaire complet |
| `pub-login-mobile-portrait-390x844.png` | `/login` | anonyme | 390×844 | connexion, portrait | composition verticale **compacte** (logo réduit, marges resserrées), formulaire prioritaire |
| `pub-login-mobile-landscape-844x390.png` | `/login` | anonyme | 844×390 | connexion, paysage court | **deux colonnes** : identité à gauche, formulaire à droite ; défilement vertical propre si nécessaire |
| `pub-login-tablet-portrait-768x1024.png` | `/login` | anonyme | 768×1024 | connexion, tablette portrait | carte centrée, une colonne |
| `pub-login-tablet-landscape-1024x768.png` | `/login` | anonyme | 1024×768 | connexion, tablette paysage | carte centrée (hauteur suffisante → pas de bascule 2 colonnes) |
| `pub-login-expired-*.png` (×5) | `/login?reason=expired` | anonyme | idem | bandeau « Votre session a expiré » | encart d'information à filet, formulaire inchangé, mêmes règles responsive |
| `pub-forgot-password-*.png` (×5) | `/mot-de-passe-oublie` | anonyme | idem | demande de réinitialisation | même gabarit `.esic-auth`, réponse neutre |

## axe (mêmes routes)

WCAG 2.0/2.1 niveau A + AA — **0 violation `critical` ni `serious`**.
Navigation clavier + focus visible OK. Aucun débordement horizontal au
zoom 200 % (portrait 390×844).

---

# Passe « navigation & dashboard réorganisés » — écrans authentifiés

Générées par `tests/14-report-screenshots.spec.ts`
(`npx playwright test --project=chromium tests/14-report-screenshots.spec.ts`),
commit déployé **`7404b08`** (branche `feat/ui-redesign-bootstrap-material`,
**non fusionnée, non déployée**), contre `http://localhost:4200` — front
`ng serve`, back `:8080` **profil `demo`**, base `esic_connect_demo`
(pile **locale**, pas un déploiement). Second facteur franchi
(`ESIC_DEMO_TOTP_SECRET=JBSWY3DPEHPK3PXP`, valeur d'exemple de
`.env.example`). Données fictives : aucun nom, e-mail ou identifiant réel.

Nommage : `NN-zone-etat-viewport.png`.

## Notifications (Lot §1)

| Fichier | Route | Rôle | Viewport | État | Résultat attendu |
|---|---|---|---|---|---|
| `01-notifications-liste-active-desktop-1440x900.png` | `/notifications/centre` | PEDAGOGICAL_MANAGER + TEACHER | 1440×900 | onglet « Notifications » actif | 1 seul titre de page, 2 onglets, « Notifications » actif |
| `02-notifications-preferences-active-desktop-1440x900.png` | `/notifications/preferences` | idem | 1440×900 | onglet « Préférences » actif | même en-tête, onglet « Préférences » seul actif |
| `03-notifications-retour-sans-etat-residuel-desktop-1440x900.png` | `/notifications/centre` | idem | 1440×900 | retour à la liste depuis Préférences | **exactement un onglet actif** (« Notifications »), une entrée de rail surlignée — aucun résidu |
| `04-notifications-liste-active-mobile-390x844.png` | `/notifications/centre` | idem | 390×844 | mobile | onglets à défilement contenu, pas de débordement |
| `05-notifications-preferences-active-mobile-390x844.png` | `/notifications/preferences` | idem | 390×844 | mobile | idem |

## Suivi d'assiduité (Lot §2)

| Fichier | Route | Rôle | Viewport | État | Résultat attendu |
|---|---|---|---|---|---|
| `10-suivi-assiduite-synthese-desktop-1440x900.png` | `/attendance-management/summary` | ADMIN | 1440×900 | « Synthèse » | strip à 5 onglets, « Synthèse » **listée, en premier, active** |
| `11-suivi-assiduite-par-seance-desktop-1440x900.png` | `.../sessions` | ADMIN | 1440×900 | « Par séance » | 1 seul onglet actif |
| `12-suivi-assiduite-par-classe-desktop-1440x900.png` | `.../classes` | ADMIN | 1440×900 | « Par classe » | 1 seul onglet actif |
| `13-suivi-assiduite-par-apprenant-desktop-1440x900.png` | `.../students` | ADMIN | 1440×900 | « Par apprenant » | 1 seul onglet actif |
| `14-suivi-assiduite-justificatifs-filtre-actif-desktop-1440x900.png` | `.../justifications` | ADMIN | 1440×900 | onglet « Justificatifs » + filtre « Acceptés » engagé | onglet actif + contrôle segmenté de statut distinct des onglets |
| `15-suivi-assiduite-par-apprenant-mobile-390x844.png` | `.../students` | ADMIN | 390×844 | mobile | onglets à défilement contenu |

## Tableau de bord (Lot §3)

| Fichier | Route | Rôle | Viewport |
|---|---|---|---|
| `20-dashboard-1440x900.png` | `/dashboard` | PEDAGOGICAL_MANAGER + TEACHER | 1440×900 |
| `21-dashboard-1280x720.png` | `/dashboard` | idem | 1280×720 |
| `22-dashboard-390x844.png` | `/dashboard` | idem | 390×844 |
| `23-dashboard-844x390.png` | `/dashboard` | idem | 844×390 |
| `24-dashboard-768x1024.png` | `/dashboard` | idem | 768×1024 |
| `25-dashboard-1024x768.png` | `/dashboard` | idem | 1024×768 |

Résultat attendu : « Accès rapides » remonté juste sous la bande
d'identité ; grille de détail à **deux colonnes** dès ≈ 52 rem de largeur
de contenu, **une colonne** en pile en dessous ; aucun débordement
horizontal ; listes de cartes bornées en hauteur.

## Organisation & planning (Lot §4)

| Fichier | Route | Rôle | Viewport | Résultat attendu |
|---|---|---|---|---|
| `30-organisation-planning-hub-actif-desktop-1440x900.png` | `/organisation-planning` | ADMIN | 1440×900 | hub : en-tête + 4 sous-sections en liens ; **une seule** entrée de rail surlignée (« Organisation & planning ») |
| `31-organisation-planning-sous-section-academic-desktop-1440x900.png` | `/academic/academic-years` | ADMIN | 1440×900 | route d'origine intacte ; entrée groupée **toujours** surlignée |
| `32-organisation-planning-sous-section-planning-desktop-1440x900.png` | `/planning/import` | ADMIN | 1440×900 | idem |
| `33-organisation-planning-hub-mobile-390x844.png` | `/organisation-planning` | ADMIN | 390×844 | hub empilé, lisible |

## Captures métier complémentaires

| Fichier | Route | Rôle | État |
|---|---|---|---|
| `40-liste-apprenants-filtree-desktop-1440x900.png` | `/students?q=a` | ADMIN | liste filtrée |
| `41-liste-apprenants-retour-filtres-conserves-desktop-1440x900.png` | `/students` (retour) | ADMIN | après ouverture d'une fiche puis retour |
| `42-creation-manuelle-apprenant-desktop-1440x900.png` | `/students/nouveau` | ADMIN | formulaire de création manuelle (Lot H) |
| `48-avertissement-expiration-session-desktop-1440x900.png` | `/dashboard` | PEDAGOGICAL_MANAGER + TEACHER | capturé **uniquement si** un déclencheur de test est exposé — sinon omis (comportement couvert par `session-timeout-warning.spec`) |

## Non produit dans cette passe

- Captures des vues « anomalies d'import », « correction de planning »,
  « émargement avec explication de fenêtre » et « tableau à défilement
  contenu » : parcours métier lourds (import de fichier, publication),
  hors du périmètre de cette passe UI ; comportements inchangés,
  couverts par leurs specs de composant.
- `aria-current` du rail au **premier chargement** d'une URL profonde :
  non posé de façon fiable (voir `FINAL-DEPLOYED-UI-REPORT.md` §18). Le
  surlignage visible (`.active`) est correct dans tous les cas et
  visible sur les captures.

## Passe précédente (écrans publics) — conservée

Les captures `pub-*.png` ci-dessus restent celles de
`tests/13-accessibility-axe.spec.ts` (commit `04b727d`).
