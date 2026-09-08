# Équiper une salle d'un tag NFC d'émargement

| Élément | Valeur |
|---|---|
| État | **Fondations livrées** (parcours d'ouverture d'URL) — **pose et recette physique : `NOT_PERFORMED`** |
| Dépend de | `EF-ORG-003` (QR fixe de salle) — le tag NFC réutilise **exactement** la même URL |
| Web NFC | **non requis, non utilisé** — on s'appuie sur le « tap → ouvre l'URL » natif d'Android et d'iOS |

## 1. Principe

Un tag NFC de salle **ne remplace pas** le QR fixe et **n'ajoute aucune
sécurité** : c'est un second moyen d'ouvrir **la même URL**. Rapprocher un
téléphone du tag ouvre `…/attendance?ref=<référence-opaque>` dans le
navigateur, c'est-à-dire l'écran d'émargement de la salle, avec le champ
« code du QR de salle » pré-rempli. **Rien n'est envoyé** tant que
l'apprenant ne valide pas.

Le serveur applique alors **exactement les mêmes contrôles** que pour le
QR fixe :

- plage réseau autorisée de l'établissement (`EF-ATT-008`) ;
- fenêtre « jusqu'au début de la séance » (RG-051) ;
- inscription de l'apprenant, anti-rejeu, unicité par point de contrôle.

Le tag ne contient qu'une **référence opaque aléatoire** : aucun nom,
aucun numéro étudiant, aucun identifiant de séance, aucun mot de passe,
aucun secret technique global.

## 2. Matériel

1. Acheter des tags **NFC NDEF** réinscriptibles (NTAG213 / NTAG215 /
   NTAG216 — ~144 à 888 octets, largement suffisant pour une URL).
2. Si le tag est posé **sur une surface métallique** (porte, montant,
   armoire), prendre des tags **anti-métal** (« on-metal », avec
   ferrite) : un tag standard sur métal est illisible.
3. Prévoir une application d'encodage (téléphone Android avec « NFC
   Tools » ou équivalent, ou un encodeur USB).

## 3. Encodage

1. Récupérer l'URL exacte de la salle :
   - Fiche du site → salle → **« Afficher »** le QR fixe → section
     **« URL pour tag NFC »** → **« Copier l'URL »**.
   - Rôles autorisés : `ADMIN`, `SUPER_ADMIN`, `SCHOOL_ADMINISTRATION`
     (même matrice que l'affichage / l'impression du QR).
2. Dans l'application d'encodage, créer un **enregistrement unique de
   type URI / URL** et y coller cette URL **telle quelle**
   (`https://<origine-esic>/attendance?ref=<référence-opaque>`).
3. Ne rien ajouter d'autre : pas de texte, pas de contact, pas de
   deuxième enregistrement.
4. **Verrouiller** le tag en écriture une fois vérifié (optionnel mais
   recommandé — évite une réécriture accidentelle ou malveillante ; un
   tag verrouillé n'est plus réinscriptible).

## 4. Test avant pose

Tester **les deux plateformes** :

- **Android** : NFC activé, écran allumé, déverrouillé → rapprocher →
  une notification propose d'ouvrir l'URL → l'écran d'émargement de la
  salle s'affiche.
- **iPhone** (Xr / 11 et ultérieurs : lecture NFC en arrière-plan) →
  rapprocher le haut du téléphone → une bannière propose d'ouvrir l'URL.
  Sur iPhone 7/8/X, il faut ouvrir l'app **Raccourcis** ou le lecteur
  NFC du centre de contrôle.

Vérifier que l'URL ouverte est bien celle de **cette** salle et que le
champ « code du QR de salle » est pré-rempli.

## 5. Pose

- Coller le tag **à côté du QR fixe imprimé** (même écriteau), à hauteur
  de main, près de la porte.
- Le QR fixe **reste** : c'est l'alternative sans NFC (tous les
  téléphones lisent un QR, tous ne lisent pas le NFC en arrière-plan).

## 6. Renouvellement

**Renouveler le QR fixe d'une salle invalide immédiatement le tag NFC** :
la référence opaque change. Après tout renouvellement :

1. réimprimer et remplacer l'affiche QR ;
2. **reprogrammer le tag NFC** avec la nouvelle URL (section « URL pour
   tag NFC », après renouvellement) — ou le remplacer s'il a été
   verrouillé.

## 7. Ce qui n'est **pas** fait

- Il n'existe **pas** de canal `ROOM_STATIC_NFC` distinct. Le serveur ne
  reçoit aujourd'hui **aucune information fiable** permettant de
  distinguer un tap NFC d'une ouverture d'URL par appareil photo : la
  même URL est ouverte par plusieurs moyens. Le canal enregistré reste
  `ROOM_STATIC_QR` (accès salle statique). Ajouter un canal NFC fiable
  supposerait que le tag transmette une preuve propre (jeton signé
  spécifique, lecteur NFC câblé sur une borne) — hors périmètre.
- La **recette physique** (pose réelle, lecture sur iPhone et Android de
  modèles variés, comportement écran verrouillé) n'a **pas** été
  réalisée : `NOT_PERFORMED`, à faire au retour du porteur sur site.
- Web NFC (`NDEFReader`) n'est **pas** utilisé : absent d'iOS, non requis
  ici.
