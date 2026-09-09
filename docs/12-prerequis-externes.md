# Prérequis externes — ce que tu dois préparer

## Métadonnées

| Élément | Valeur |
|---|---|
| Version | 1.0 |
| Date | 3 septembre 2026 |
| Destinataire | Abubacar AFOLABI, porteur du produit |
| Objet | comptes, clés, matériel et décisions à préparer pour activer les briques externes |

---

## 0. Comment lire ce document

Le produit est construit pour fonctionner **entièrement en local**, sans
aucun compte externe. Chaque intégration est encapsulée derrière un
port : un adaptateur local est fourni, et l'adaptateur réel s'active par
configuration.

Tu n'es donc **jamais bloqué**. Ce document liste ce qu'il faut préparer
pour passer du local au réel, brique par brique, dans l'ordre où le
développement en aura besoin.

| Colonne | Signification |
|---|---|
| **Quand** | sprint de la roadmap où la brique réelle devient utile |
| **Sans ça** | ce qui fonctionne quand même |
| **Coût** | ordre de grandeur, à vérifier au moment de la souscription |

---

## 1. Récapitulatif

| # | Brique | Quand | Coût | Difficulté |
|---|---|---|---|---|
| 1 | Nom de domaine | S13 | ~12 €/an | faible |
| 2 | Certificat HTTPS | S13 | gratuit (Let's Encrypt) | faible |
| 3 | Hébergement | S13 | 5 à 30 €/mois | moyenne |
| 4 | Fournisseur de courriel | S3 utile, S13 requis | gratuit à 15 €/mois | faible |
| 5 | Cloudflare Turnstile | S2 | **gratuit** | très faible |
| 6 | WebAuthn / passkeys | S2 | **gratuit** | aucune — rien à souscrire |
| 7 | Raspberry Pi 4 | S12 | matériel déjà possédé | moyenne |
| 8 | Microsoft 365 / Entra ID | S11 | compte établissement | élevée — dépend de l'ESIC |
| 9 | Service d'IA | S12 | **gratuit** en local | faible |
| 10 | Antivirus de fichiers | S9 | **gratuit** (ClamAV) | faible |
| 11 | Notifications push | S10 | **gratuit** | faible |
| 12 | Logo et identité visuelle | S11 | — | faible |

**À retenir** : sur douze briques, **sept ne coûtent rien** et quatre ne
demandent aucune démarche externe. Le seul point qui dépend d'un tiers
que tu ne contrôles pas est l'accès Microsoft 365 de l'ESIC.

---

## 2. Nom de domaine

**Quand** : sprint 13, pour le déploiement.
**Sans ça** : tout fonctionne sur `localhost`.

À faire :

1. choisir un registrar (OVH, Gandi, Cloudflare Registrar, Namecheap) ;
2. réserver un domaine — par exemple `esic-connect.fr` ou un
   sous-domaine si l'ESIC en fournit un ;
3. me communiquer le domaine retenu.

**Attention** : WebAuthn lie les passkeys au **domaine exact**. Un
changement de domaine après enrôlement invalide les passkeys déjà
enregistrées. Il faut donc arrêter le domaine **avant** de déployer en
recette avec de vrais utilisateurs.

---

## 3. Certificat HTTPS

**Quand** : sprint 13.
**Sans ça** : le développement se fait en HTTP sur `localhost`, ce qui
est accepté par les navigateurs pour WebAuthn et les service workers.

À faire : rien à acheter. Let's Encrypt via Caddy ou Traefik délivre et
renouvelle automatiquement le certificat. Je fournis la configuration.

**Impératif** : hors `localhost`, WebAuthn, les notifications push et
l'installation PWA **exigent** HTTPS. Aucune de ces trois fonctions ne
peut être démontrée sur un domaine en HTTP.

---

## 4. Hébergement

**Quand** : sprint 13.
**Sans ça** : `docker compose up` sur ta machine.

Trois options, par ordre de simplicité :

| Option | Coût indicatif | Remarque |
|---|---|---|
| VPS unique (Hetzner, Scaleway, OVH) | 5 à 15 €/mois | tout en Docker Compose sur une machine ; le plus simple |
| Plateforme managée (Railway, Render, Clever Cloud) | 15 à 30 €/mois | moins d'administration, moins de contrôle |
| AWS (ECS, RDS, ElastiCache) | 40 €/mois et plus | correspond à l'architecture cible documentée, mais lourd |

Dimensionnement minimum pour un VPS : **2 vCPU, 4 Go de mémoire, 40 Go
de disque**. MySQL, Redis, l'application, le service d'IA et le broker y
tiennent.

À me communiquer : l'option retenue, et l'accès SSH si tu veux que je
prépare le déploiement.

---

## 5. Fournisseur de courriel

**Quand** : utile dès le sprint 3, indispensable au sprint 13.
**Sans ça** : Mailpit reçoit tous les messages en local et permet de les
lire dans un navigateur — le parcours d'invitation est complet, mais
aucun message ne sort de ta machine.

Options :

| Fournisseur | Offre gratuite | Remarque |
|---|---|---|
| Brevo (ex-Sendinblue) | 300 messages/jour | français, retours de délivrabilité, simple |
| Resend | 3 000 messages/mois | très simple, moderne |
| Amazon SES | 3 000 messages/mois la première année | cohérent avec l'architecture cible AWS |
| SMTP de l'ESIC | — | à demander au service informatique |

À faire :

1. créer le compte ;
2. **autoriser l'adresse d'expédition**, au choix :
   - domaine que tu contrôles → poser **SPF**, **DKIM** et idéalement
     **DMARC** chez le registrar (meilleure délivrabilité) ;
   - adresse dont tu ne contrôles pas le DNS (ex. une adresse d'école
     comme `…@etudiant-esci.fr`) → la déclarer en **expéditeur unique
     validé** dans Brevo (*Senders → Add a sender*), puis cliquer le lien
     de confirmation reçu sur cette adresse. Sans cette validation, Brevo
     renvoie `550` et **aucun message ne part** ;
3. créer une clé d'API ou des identifiants SMTP ;
4. me transmettre **hôte, port, identifiant, adresse d'expédition**.

**Ne me transmets jamais le mot de passe ou la clé dans un message
persistant** : place-les directement dans ton fichier `.env` local, qui
n'est pas versionné. Je te dirai le nom exact des variables.

Sans SPF et DKIM, les invitations partiront dans les indésirables : ce
n'est pas un défaut du produit.

Configuration détaillée, changement d'adresse d'expédition et
diagnostic pas à pas : `docs/deployment/BREVO-EMAIL.md`.

---

## 6. Cloudflare Turnstile

**Quand** : sprint 2.
**Sans ça** : l'adaptateur local accepte tout ; la protection n'est pas
démontrable.

À faire — **gratuit et sans carte bancaire** :

1. créer un compte sur `dash.cloudflare.com` ;
2. ouvrir **Turnstile** puis « Add site » ;
3. renseigner le domaine — ajouter `localhost` pour le développement ;
4. choisir le mode « Managed » ;
5. récupérer la **clé de site** (publique) et la **clé secrète** ;
6. mettre la clé secrète dans `.env`, la clé de site dans la
   configuration front.

Cloudflare fournit également des **clés de test** qui réussissent ou
échouent systématiquement : elles servent aux tests automatisés et
n'exigent aucun compte.

---

## 7. WebAuthn et passkeys

**Quand** : sprint 2.
**Rien à souscrire, rien à payer.**

WebAuthn est une norme du navigateur. Il n'y a ni service ni compte.

Ce dont tu as besoin pour **démontrer** la fonction :

- un ordinateur avec Touch ID, Windows Hello, ou une clé de sécurité ;
- ou un téléphone récent (iOS 16+ / Android 9+) ;
- **et** `localhost` ou un domaine en HTTPS.

Un navigateur sans authentificateur affichera quand même le parcours,
mais ne pourra pas créer de passkey. Prévois l'appareil de démonstration
à l'avance.

---

## 8. Raspberry Pi 4

**Quand** : sprint 12.
**Sans ça** : le simulateur logiciel reproduit intégralement le
protocole — identité, signal de vie, émargement, coupure réseau, file
locale, rejeu, doublon. La chaîne est développable, testable et
démontrable sans matériel.

Pour utiliser la vraie carte :

1. installer Raspberry Pi OS Lite 64 bits ;
2. connecter la carte au même réseau que le serveur ;
3. installer Python 3.11 et le client MQTT ;
4. me donner son adresse IP ou son nom sur le réseau ;
5. je fournis le script de la borne et sa procédure d'enrôlement.

Optionnel : un écran tactile pour l'interface locale, un lecteur NFC USB
si tu veux la lecture de badge. Ni l'un ni l'autre n'est nécessaire.

---

## 9. Microsoft 365, Teams et Entra ID

**Quand** : sprint 11.
**Sans ça** : les séances distancielles acceptent un lien saisi à la
main, et le flux iCalendar permet déjà l'abonnement depuis Outlook. La
seule chose qui manque est la **création automatique** de la réunion
Teams.

C'est la brique qui dépend le plus d'un tiers. À obtenir auprès du
service informatique de l'ESIC :

1. l'autorisation d'enregistrer une application dans **Entra ID**
   (anciennement Azure AD) ;
2. l'enregistrement de l'application, qui produit un
   **identifiant client**, un **identifiant de locataire** et un
   **secret client** ;
3. les permissions Microsoft Graph : `OnlineMeetings.ReadWrite`,
   `Calendars.ReadWrite`, `User.Read.All` ;
4. le **consentement administrateur**, obligatoire pour ces permissions.

Si l'ESIC refuse ou tarde, une alternative existe : un locataire
Microsoft 365 Developer gratuit permet de développer et de démontrer
l'intégration sur des comptes fictifs. À demander sur
`developer.microsoft.com/microsoft-365/dev-program`.

**Demande cet accès tôt** : c'est le délai le plus long et le moins
maîtrisable.

**État au sprint 11.** Le code est prêt et attend ces quatre valeurs :
port `MeetingProvider` (réunion Teams), port `ExternalCalendarWriter`
(calendrier), adaptateur Microsoft Graph et adaptateur inactif choisi par
configuration. Sans `APP_INTEGRATION_MICROSOFT_ENABLED`, `_TENANT_ID`,
`_CLIENT_ID` et `_CLIENT_SECRET`, l'intégration est **inactive** et
`GET /api/v1/integrations/microsoft/status` le déclare — le produit ne
fabrique aucun lien de réunion. **Aucun appel n'a jamais atteint
Microsoft** : `EF-INT-002` et `EF-INT-003` restent `PARTIAL`, dette T-16
dans `docs/CURRENT-STATE.md`.

---

## 10. Service d'intelligence artificielle

**Quand** : sprint 12.
**Aucun compte, aucun coût.**

Le service est un conteneur Python / FastAPI qui tourne à côté de
l'application. Il repose sur des règles, de la similarité de chaînes et
un modèle statistique local (`scikit-learn`). Aucune donnée ne sort de
ton infrastructure.

Si tu souhaitais un jour appeler un modèle distant, il faudrait une clé
d'API et une décision explicite sur la protection des données. **Ce
n'est ni prévu ni nécessaire** : les données d'apprenants ne doivent pas
être envoyées à un service externe non approuvé.

---

## 11. Antivirus de fichiers

**Quand** : sprint 9, pour les pièces jointes des justificatifs.
**Sans ça** : seuls les contrôles structurels s'appliquent (extension,
type déclaré, contenu réel, taille) — il ne faut alors **jamais** écrire
que les fichiers sont garantis sans logiciel malveillant.

À faire : rien à acheter. **ClamAV** s'ajoute comme conteneur dans la
composition Docker. Je fournis la configuration ; prévois environ 1 Go
de mémoire supplémentaire pour les signatures.

---

## 12. Notifications push

**Quand** : sprint 10.
**Aucun compte, aucun coût.**

Les notifications push web reposent sur le protocole **VAPID** : une
paire de clés générée en une commande, sans service tiers. Je la
générerai ; la clé privée ira dans `.env`.

Contraintes à connaître :

- HTTPS obligatoire hors `localhost` ;
- sur iOS, l'application doit être **ajoutée à l'écran d'accueil** avant
  de pouvoir recevoir des notifications ;
- l'utilisateur doit accorder l'autorisation, et peut la retirer.

---

## 13. Identité visuelle

**Quand** : sprint 11, pour les rapports PDF et les attestations.

À me fournir :

- le **logo de l'ESIC** en PNG ou SVG, fond transparent, largeur
  minimale 500 px ;
- les couleurs officielles si elles existent, au format hexadécimal ;
- la mention légale à faire figurer en pied de document ;
- éventuellement une police d'écriture, avec sa licence.

**État au sprint 11 : aucun de ces éléments n'a été fourni.** Les
documents PDF sont produits avec un bandeau et une signature
**typographique** — « ESIC CONNECT », le nom de l'établissement,
l'identifiant du document et la mention de document électronique — mais
**sans image de logo**, parce qu'aucun fichier n'existe dans le dépôt et
qu'en dessiner un serait inventer une identité visuelle. Dette T-17 dans
`docs/CURRENT-STATE.md` ; l'insertion se réduit à un `PDImageXObject`
dans `PdfDocumentWriter.header` le jour où le fichier arrive.

L'émetteur imprimé sur les documents est configurable par
`APP_REPORTING_ISSUER` : c'est là que se règle le nom exact de
l'établissement, sans toucher au code.

---

## 14. Décisions à prendre

Ces points ne s'achètent pas : ils se décident. Ils bloquent la clôture
du sprint 13.

| # | Décision | Pourquoi c'est nécessaire |
|---|---|---|
| 1 | Durée de conservation des présences | proposée à 5 années scolaires, à valider par la direction et le référent RGPD |
| 2 | Durée de conservation des justificatifs | proposée à 12 mois |
| 3 | Sort des pièces jointes supprimées | suppression immédiate ou rétention définie |
| 4 | Plages réseau de l'établissement | indispensables au QR fixe de salle : demander les blocs CIDR au service informatique |
| 5 | Horaires officiels des séances et des pauses | conditionnent les quatre points de contrôle |
| 6 | Qui peut générer une attestation | administration seule, ou aussi responsable pédagogique |
| 7 | Téléchargement du rapport par l'apprenant | activé par défaut ou sur autorisation |
| 8 | Personne référente RGPD | signataire du registre des traitements |

---

## 15. Ce qu'il ne faut jamais faire

- écrire une clé, un mot de passe ou un secret dans un fichier suivi par
  Git — même « de test », même « temporaire » ;
- utiliser une liste réelle d'apprenants pendant le développement ; les
  jeux de données sont fictifs ;
- envoyer des données réelles à un service externe non approuvé ;
- déployer sans HTTPS ;
- réutiliser le même mot de passe pour la base de données, Redis et le
  compte administrateur.

Les secrets vivent dans `.env`, qui est ignoré par Git, et à terme dans
un gestionnaire de secrets fourni par l'hébergeur.

---

## 16. Ordre conseillé

| Moment | Action |
|---|---|
| **Maintenant** | Turnstile (10 minutes, gratuit) ; demander l'accès Entra ID à l'ESIC (délai long) ; réunir logo et couleurs |
| **Avant le sprint 9** | rien à souscrire — ClamAV s'installe seul |
| **Avant le sprint 12** | préparer la Raspberry Pi si tu veux le matériel réel |
| **Avant le sprint 13** | domaine, hébergement, fournisseur de courriel, SPF/DKIM |
| **En continu** | trancher les huit décisions du §14 |

Rien de tout cela n'est bloquant aujourd'hui : le développement démarre
et se poursuit intégralement en local.
