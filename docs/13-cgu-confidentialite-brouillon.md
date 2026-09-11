# CGU et politique de confidentialité — brouillon

**Statut : brouillon de travail, non publié, non validé juridiquement.**
Rédigé à partir de ce que l'application fait *réellement* aujourd'hui
(code, `docs/08-securite-rgpd.md`), pas de ce qu'elle devrait faire. À
faire relire par un référent RGPD / juriste avant toute publication ou
tout engagement contractuel — voir §6.

Ce document répond à une question simple posée en amont : *l'établissement
doit-il avoir des conditions d'utilisation pour respecter le RGPD ?*
Réponse courte : **oui**, et à ce jour il n'en existe aucune (ni CGU, ni
politique de confidentialité, ni mentions légales) dans le dépôt ni dans
l'application déployée — vérifié le 2026-09-11. Le cahier des charges
promet pourtant à l'apprenant d'« exercer ses droits RGPD » (§5.7), sans
qu'aucun mécanisme technique ne le permette encore (voir §5).

---

## 1. Pourquoi c'est nécessaire

ESIC Connect traite des données à caractère personnel d'apprenants et de
personnel (identité, présence, justificatifs médicaux le cas échéant,
réclamations, historique de connexion). Le RGPD impose, indépendamment
de la taille de la structure :

- d'informer les personnes concernées (article 13/14) — c'est le rôle
  d'une politique de confidentialité ;
- de définir une base légale par traitement (le plus souvent : intérêt
  légitime ou mission d'intérêt public de l'établissement pour le suivi
  de scolarité, obligation légale pour l'archivage) ;
- de limiter la conservation dans le temps (article 5.1.e) ;
- de permettre l'exercice des droits (accès, rectification, effacement,
  opposition, portabilité).

Ne pas avoir ce document n'empêche pas l'application de fonctionner,
mais expose l'établissement (pas l'éditeur du logiciel) en cas de
contrôle ou de plainte d'une personne concernée.

## 2. Ce que je recommande de publier, dans l'ordre

1. **Une politique de confidentialité courte et honnête** (§4 ci-dessous
   est un brouillon prêt à adapter) — priorité haute, faisable en une
   page.
2. **Des CGU minimales** (§3) — surtout utiles si des comptes formateurs
   externes ou des tuteurs entreprise se créent, pour cadrer les usages
   acceptables du compte.
3. **Une page mentions légales** (éditeur, hébergeur, directeur de
   publication) — obligatoire pour tout service en ligne, indépendamment
   du RGPD.

Ne **pas** écrire « conforme RGPD » tant que les points du §5 ne sont
pas réglés : c'est une affirmation vérifiable et actuellement fausse sur
au moins deux points documentés (rétention de l'audit et des pièces
jointes). Une formulation défendable : *« démarche de mise en
conformité RGPD en cours »*.

---

## 3. Brouillon de CGU (conditions générales d'utilisation)

> À adapter avec le nom légal de l'établissement, remplacer les
> `[crochets]`.

### 3.1 Objet
Les présentes conditions régissent l'accès et l'utilisation de
l'application ESIC Connect par les apprenants, formateurs, personnels
et responsables pédagogiques de `[nom de l'établissement]`.

### 3.2 Accès au service
L'accès est réservé aux personnes disposant d'un compte créé par
l'établissement (invitation nominative). Chaque compte est personnel et
protégé par un mot de passe et, pour les rôles à privilège, une
authentification à deux facteurs. L'utilisateur s'engage à ne pas
partager ses identifiants.

### 3.3 Usage autorisé
Le service est fourni pour la gestion de la scolarité : émargement,
consultation du planning, justificatifs, réclamations, communication
académique. Toute utilisation détournée (accès à des données ne
concernant pas l'utilisateur, tentative de contournement des contrôles
d'accès, usage du compte d'un tiers) est interdite et peut donner lieu à
une suspension de compte et, le cas échéant, à des poursuites.

### 3.4 Disponibilité
Le service est fourni « en l'état ». `[Nom de l'établissement]` s'efforce
d'assurer sa disponibilité mais ne garantit pas une continuité de
service absolue (maintenance, incident technique).

### 3.5 Responsabilité
`[Nom de l'établissement]` est responsable du traitement des données
personnelles au sens du RGPD (voir politique de confidentialité,
§4). L'utilisateur est responsable de la véracité des informations
qu'il transmet (justificatifs, réclamations).

### 3.6 Modification des conditions
Les présentes conditions peuvent être modifiées ; les utilisateurs en
sont informés par notification dans l'application.

### 3.7 Droit applicable
Droit français.

---

## 4. Brouillon de politique de confidentialité

### 4.1 Qui traite vos données ?
`[Nom de l'établissement]`, responsable de traitement. Contact :
`[adresse e-mail du référent RGPD ou de la direction]`.

### 4.2 Quelles données, pour quoi faire

| Donnée | Finalité | Base légale |
|---|---|---|
| Identité (nom, prénom, e-mail, numéro étudiant) | Gestion du compte et de la scolarité | Mission d'intérêt public / intérêt légitime de l'établissement |
| Présence et assiduité (émargement, retards, absences) | Suivi pédagogique, obligations réglementaires de l'établissement | Obligation légale / mission d'intérêt public |
| Justificatifs d'absence (catégorie, commentaire, pièce jointe éventuelle) | Traitement des demandes de justification | Intérêt légitime |
| Réclamations | Traitement des demandes et litiges | Intérêt légitime |
| Connexion et sécurité (journal d'audit, appareils de confiance, adresse IP au moment de la connexion) | Sécurité du compte, détection d'usage frauduleux | Intérêt légitime |
| Planning et affectations | Organisation pédagogique | Mission d'intérêt public |

Aucune donnée n'est utilisée à des fins commerciales, de profilage
publicitaire, ou revendue à un tiers.

### 4.3 Qui a accès à vos données ?
Le personnel de l'établissement habilité selon son rôle (administration,
responsable pédagogique, formateur pour ses classes) — jamais l'ensemble
du personnel, un contrôle d'accès technique restreint chaque rôle à son
périmètre. Aucun tiers commercial n'y a accès. Un sous-traitant technique
peut intervenir pour l'envoi d'e-mails (préciser le prestataire retenu,
ex. Brevo) et l'hébergement — ces prestataires n'accèdent qu'aux données
strictement nécessaires à leur prestation.

### 4.4 Combien de temps vos données sont-elles conservées ?
**Point à finaliser avant publication** — à ce jour :

- Présences, corrections, justificatifs : conservées sans purge
  automatique programmée (limite technique actuelle, documentée en
  interne dans `docs/08-securite-rgpd.md`). Durée cible du cahier des
  charges : 5 années scolaires pour l'assiduité, 12 mois pour les pièces
  jointes de justificatifs — **non encore mis en œuvre techniquement**.
- Journal d'audit (traçabilité de sécurité) : conservé sans purge
  automatique à ce jour.
- Notifications applicatives : conservées sans durée définie à ce jour.

Tant que ces durées ne sont pas techniquement mises en œuvre, la formule
la plus honnête à publier est : *« Vos données sont conservées pendant
la durée de votre scolarité et [X années] après, sauf obligation légale
contraire. Une politique de purge automatique est en cours de mise en
place. »* — à valider avec la direction avant publication.

### 4.5 Vos droits
Vous disposez d'un droit d'accès, de rectification, d'effacement,
d'opposition et de portabilité sur vos données, ainsi que du droit
d'introduire une réclamation auprès de la CNIL. Pour exercer vos droits,
contactez `[adresse e-mail du référent RGPD]`.

**Limite actuelle à assumer publiquement** : ces droits s'exercent
aujourd'hui **par contact direct avec l'établissement**, pas par une
fonction en libre-service dans l'application (aucun bouton « exporter
mes données » ou « supprimer mon compte » n'existe à ce jour côté
technique). Ne pas laisser croire le contraire dans la politique
publiée.

### 4.6 Sécurité
Mots de passe chiffrés (jamais stockés en clair), authentification à
deux facteurs pour les comptes à privilège, chiffrement des échanges
(HTTPS), traçabilité des accès sensibles.

### 4.7 Cookies
Un seul cookie technique est utilisé, nécessaire au maintien de la
session (renouvellement de connexion) — pas de cookie publicitaire ni de
mesure d'audience tierce. `[à confirmer/adapter si un outil d'analytics
est ajouté un jour]`.

---

## 5. Ce qui manque encore techniquement pour tenir ces engagements

Constaté en code au 2026-09-11, à traiter avant de publier une version
définitive qui les mentionnerait comme acquis :

1. **Aucune fonction d'export de ses propres données** (portabilité,
   article 20) — à ajouter si on veut honorer §4.5 en libre-service.
2. **Aucune fonction de suppression de compte / droit à l'effacement**
   en libre-service — actuellement seul l'archivage logique existe pour
   les entités académiques ; rien d'équivalent pour un compte
   utilisateur et ses données personnelles.
3. **Aucune purge automatique** des données de présence, du journal
   d'audit, des notifications, ni des pièces jointes de justificatifs
   au-delà de leur durée annoncée.
4. **Aucune page CGU/politique de confidentialité/mentions légales**
   dans l'application — ce document n'est, à ce stade, qu'un brouillon
   hors de l'application.

Aucun de ces points ne bloque la soutenance ni l'usage pédagogique
immédiat de l'application ; ils deviennent nécessaires si l'application
doit être exploitée en production avec de vraies données d'élèves de
façon durable.

## 6. Prochaines étapes suggérées

1. Faire valider le contenu des §3 et §4 par la direction de
   l'établissement et, si possible, un DPO/juriste.
2. Compléter les `[crochets]` avec les informations réelles de
   l'établissement.
3. Publier les deux documents comme pages statiques accessibles depuis
   le pied de page de l'application et depuis l'écran d'inscription.
4. Prioriser, dans un sprint ultérieur, l'export de ses données et la
   suppression de compte (§5, points 1 et 2) si l'application doit
   passer en exploitation réelle prolongée.
