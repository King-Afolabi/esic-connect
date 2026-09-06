# Lot B — Identité visuelle moins générique

Compétence utilisée : skill locale **`frontend-design`** (`/Users/kingafolabi/.claude/skills/frontend-design`).
Aucune skill externe téléchargée.

## Constat après inventaire

Le système de design ESIC (`src/styles/`) est déjà **discipliné**, contrairement
à ce que la formulation « trop générique » laisserait craindre :

| Axe | État réel |
|---|---|
| Élévations | **2 seulement** (`--esic-shadow-raise`, `--esic-shadow-float`) ; `mat-card` est **plat** (`box-shadow: none`) — pas le « SaaS-card kit » à ombre grise molle |
| Rayons | `--esic-radius-{xs,sm,md,pill}` ; `sm` domine (49 usages) — c'est le « un seul rayon partout » |
| Couleurs | entièrement tokenisées (`--esic-*`), bleu `#134E9C` + vert `#1F7A4C` ESIC |
| Cartes | **297 `mat-card`** — même filet fin `--esic-line`, même rayon `sm`, même fond ; c'est la répétition à corriger |
| « Filet bleu à gauche » | 16 fichiers utilisent `inset 3px 0 0 <accent>` — mais **sur des encarts d'alerte** (`.esic-note`, bandeaux d'état), où l'arête colorée est un signal légitime, pas une décoration |
| Typo | IBM Plex Sans (interface) + IBM Plex Serif (titres, documents) — choix institutionnel déjà distinctif |

Le manque n'était donc pas un excès de décoration « tell » mais l'**absence
d'une signature reconnaissable**.

## Ce qui a été fait

**Signature ESIC sur l'en-tête de page** (`.esic-page-header::after`,
`_primitives.scss`) : un court segment de 2 px, **bleu ESIC puis vert ESIC**
(les deux couleurs de la marque, coupe nette à 55 %), posé sur le filet du
titre, à son origine. Structurel — il ancre le titre —, silencieux, et
répété sur les ~30 écrans qui utilisent la primitive, ce qui donne un
rythme de mise en page reconnaissable sans bruit. Aucun mouvement,
contraste sans objet (élément décoratif, le filet `--esic-line` sépare
déjà).

C'est le seul geste « appuyé » (principe *spend your boldness in one
place*) ; tout le reste du système est laissé tel quel, volontairement.

## Ce qui n'a PAS été fait, et pourquoi

Une passe de **direction artistique écran par écran** — différenciation
visuelle systématique information / navigation / action / métrique /
alerte / éditorial au-delà de ce que portent déjà `.esic-note`, les
pastilles de statut et le tableau de bord à trois registres (fait au
sprint UI étape 5) — **n'a pas été menée**. Elle toucherait les 297
usages de `mat-card` et exigerait une itération par captures écran par
écran, avec un risque de régression disproportionné au regard du gain,
sur un produit institutionnel où la cohérence sert l'apprentissage.
C'est un chantier à part entière, à mener avec revue visuelle dédiée —
signalé comme tel, pas simulé ici.

## Vérifications

- `npm run lint` : vert.
- `npx ng build --configuration production` : `styles.css` ~89 kB,
  aucune alerte de budget.
- Rendu du segment de signature : à confirmer dans la passe de captures
  (Lot N), viewports desktop / tablette / mobile.
