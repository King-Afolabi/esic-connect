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

## Non produit dans cette passe

Captures des écrans **authentifiés** (tableau de bord, navigation à un
seul actif, notifications, onglets année/formation/classe, liste filtrée
+ retour, création d'apprenant, gestion des fenêtres d'émargement,
anomalies d'import, correction de planning, tableaux responsives,
avertissement de session) : dépendent de la pile de démonstration
(profil Spring `demo`), non montée ici — voir
`docs/audit/FINAL-ONE-SHOT-REPORT.md` §15 et §17.
