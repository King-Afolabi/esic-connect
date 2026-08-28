\`\`\`markdown  
\# Cahier des charges fonctionnel et technique — ESIC Connect  
  
\## Métadonnées du document  
  
| Élément | Valeur |  
|---|---|  
| Nom du projet | ESIC Connect |  
| Type de document | Cahier des charges fonctionnel et technique |  
| Établissement | ESIC |  
| Porteur du projet | Abubacar AFOLABI |  
| Certification préparée | RNCP 39394 — Expert en systèmes d’information et sécurité |  
| Version | 1.0 |  
| Date | 27 août 2026 |  
| Statut | Version initiale à valider |  
| Document de référence | \`docs/01-cadrage.md\` |  
| Durée de réalisation du prototype | Trois jours |  
| Environnement initial | Développement local conteneurisé |  
| Architecture cible | Cloud AWS |  
| Niveau de confidentialité | Interne au projet |  
  
\---  
  
\# 1. Objet du document  
  
Le présent cahier des charges décrit les exigences fonctionnelles,  
techniques, organisationnelles, sécuritaires et réglementaires du projet  
\*\*ESIC Connect\*\*.  
  
Il constitue la référence permettant :  
  
\- de définir les besoins du projet ;  
\- de fixer le périmètre du prototype ;  
\- de décrire les parcours utilisateurs ;  
\- d’identifier les règles de gestion ;  
\- de préciser les critères d’acceptation ;  
\- de guider le développement assisté par Claude Code ;  
\- d’organiser les tests et la recette ;  
\- d’assurer la traçabilité avec les blocs du titre RNCP 39394 ;  
\- de distinguer les fonctions réellement réalisées des fonctions  
  simulées, conçues ou reportées.  
  
Le document doit être lu conjointement avec :  
  
\- \`docs/01-cadrage.md\` ;  
\- \`docs/03-architecture.md\` ;  
\- \`docs/04-modele-donnees.md\` ;  
\- \`docs/05-backlog.md\` ;  
\- \`docs/06-risques.md\` ;  
\- \`docs/07-securite-rgpd.md\` ;  
\- \`docs/08-tests-recette.md\` ;  
\- \`docs/09-matrice-rncp.md\` ;  
\- \`docs/CURRENT-STATE.md\`.  
  
\---  
  
\# 2. Présentation générale  
  
\## 2.1 Description synthétique  
  
\*\*ESIC Connect\*\* est une plateforme web responsive et mobile de gestion  
pédagogique destinée à centraliser :  
  
\- les utilisateurs ;  
\- les formations ;  
\- les promotions ;  
\- les classes ;  
\- les rythmes d’alternance ;  
\- les plannings ;  
\- les séances ;  
\- les présences ;  
\- les absences ;  
\- les retards ;  
\- les justificatifs ;  
\- les réclamations ;  
\- les notifications ;  
\- les rapports d’assiduité ;  
\- les événements de sécurité ;  
\- les dispositifs connectés.  
  
Le système couvre le processus suivant :  
  
\`\`\`text  
Création des référentiels  
→ Importation des apprenants  
→ Activation des comptes  
→ Importation du planning  
→ Contrôle et publication  
→ Création des séances  
→ Notification des utilisateurs  
→ Ouverture de la séance  
→ Émargement  
→ Contrôles journaliers  
→ Traitement des absences  
→ Production des rapports  
\`\`\`  
  
\## 2.2 Contexte actuel  
  
À l’ESIC, le responsable pédagogique crée actuellement le planning et le  
met à disposition des apprenants sur Microsoft Teams.  
  
Les semaines et journées de cours sont indiquées dans le planning  
partagé sur le canal concerné.  
  
Il n’existe pas, dans le fonctionnement observé, de logiciel centralisé  
permettant de :  
  
\- construire et publier les plannings ;  
\- générer automatiquement les séances ;  
\- affecter les formateurs ;  
\- gérer les présences ;  
\- traiter les justificatifs ;  
\- consolider les rapports ;  
\- détecter les incohérences ;  
\- conserver une piste d’audit homogène.  
  
Les réunions Microsoft Teams ne sont pas automatiquement créées depuis  
le planning. Lorsqu’un cours se déroule à distance, le formateur ou le  
responsable pédagogique crée généralement la réunion et partage le lien.  
  
Les listes d’apprenants et les présences peuvent être conservées sur :  
  
\- des documents papier ;  
\- des feuilles d’émargement ;  
\- des fichiers Excel ;  
\- d’autres supports construits manuellement.  
  
L’administration contrôle les absences et les justificatifs en  
sollicitant, selon les situations :  
  
\- le formateur ;  
\- le responsable pédagogique ;  
\- les conseillers ;  
\- les autres acteurs administratifs.  
  
\## 2.3 Problèmes identifiés  
  
Les principaux problèmes sont :  
  
1\. l’absence d’automatisation ;  
2\. le manque de centralisation ;  
3\. la faible fiabilité des documents papier ;  
4\. la perte ou la dégradation possible des feuilles ;  
5\. les erreurs de ressaisie ;  
6\. les doublons ;  
7\. la difficulté de vérifier la réalité d’une présence ;  
8\. la difficulté de calculer les taux d’assiduité ;  
9\. la production manuelle des attestations ou certificats d’assiduité ;  
10\. le manque de traçabilité ;  
11\. le manque de visibilité immédiate ;  
12\. l’absence de parcours homogène pour les cours hybrides ;  
13\. la difficulté à gérer les remplacements ;  
14\. la difficulté à suivre les comptes non activés ;  
15\. la difficulté à identifier les anomalies.  
  
\---  
  
\# 3. Objectifs  
  
\## 3.1 Objectif principal  
  
Concevoir un système d’information centralisé, sécurisé et performant  
permettant de gérer le parcours allant de l’intégration des apprenants  
jusqu’à la production des rapports d’assiduité.  
  
\## 3.2 Objectifs opérationnels  
  
La solution doit permettre de :  
  
\- réduire les opérations manuelles ;  
\- diminuer les erreurs ;  
\- supprimer progressivement les feuilles papier ;  
\- améliorer la fiabilité des présences ;  
\- automatiser la création des séances ;  
\- avertir les utilisateurs des changements ;  
\- simplifier le travail des responsables pédagogiques ;  
\- simplifier l’appel réalisé par les formateurs ;  
\- donner aux apprenants une visibilité sur leur situation ;  
\- automatiser les calculs d’assiduité ;  
\- produire des exports ;  
\- préparer la génération d’attestations ;  
\- garantir la traçabilité des modifications ;  
\- renforcer la sécurité ;  
\- démontrer une intégration de l’IA et de l’IoT.  
  
\## 3.3 Objectifs mesurables du prototype  
  
Le prototype sera considéré comme fonctionnel si les parcours suivants  
sont démontrables :  
  
1\. connexion d’un utilisateur ;  
2\. application de ses rôles ;  
3\. création d’une formation ;  
4\. création d’une classe ;  
5\. importation d’une liste d’apprenants ;  
6\. prévisualisation de l’import ;  
7\. confirmation de l’import ;  
8\. création des comptes ;  
9\. importation d’un planning CSV ;  
10\. prévisualisation des séances ;  
11\. publication du planning ;  
12\. consultation du planning par le formateur ;  
13\. ouverture d’une séance ;  
14\. génération d’un QR code ;  
15\. émargement d’un apprenant ;  
16\. affichage immédiat de la présence ;  
17\. calcul d’une demi-journée ;  
18\. correction auditée ;  
19\. production d’un rapport ;  
20\. export CSV ou Excel ;  
21\. démonstration d’un mécanisme de sécurité avancé ;  
22\. démonstration ou simulation d’un événement IoT ;  
23\. démonstration d’une assistance d’importation.  
  
\---  
  
\# 4. Périmètre  
  
\## 4.1 Périmètre fonctionnel global  
  
La solution cible comprend les domaines suivants :  
  
\- identité et accès ;  
\- référentiels pédagogiques ;  
\- gestion des apprenants ;  
\- gestion des formateurs ;  
\- gestion des promotions et classes ;  
\- rythmes pédagogiques et alternance ;  
\- importation des plannings ;  
\- création manuelle contrôlée des plannings ;  
\- gestion des séances ;  
\- gestion des salles ;  
\- remplacement et annulation ;  
\- émargement ;  
\- gestion des cours distanciels et hybrides ;  
\- justificatifs ;  
\- réclamations ;  
\- messagerie ;  
\- notifications ;  
\- tableaux de bord ;  
\- rapports ;  
\- audit ;  
\- cybersécurité ;  
\- intelligence artificielle ;  
\- IoT ;  
\- supervision ;  
\- intégrations externes.  
  
\## 4.2 Périmètre obligatoire du prototype  
  
Le prototype de trois jours doit prioritairement comprendre :  
  
\- authentification ;  
\- gestion des rôles ;  
\- cumul des rôles ;  
\- gestion des utilisateurs ;  
\- gestion des formations ;  
\- gestion des classes ;  
\- gestion des promotions ;  
\- gestion des inscriptions annuelles ;  
\- import CSV des apprenants ;  
\- prévisualisation de l’import ;  
\- confirmation de l’import ;  
\- statut d’activation ;  
\- import CSV du planning ;  
\- prévisualisation du planning ;  
\- publication du planning ;  
\- création des séances ;  
\- consultation du planning ;  
\- ouverture d’une séance ;  
\- QR code temporaire ;  
\- émargement ;  
\- au moins deux points de contrôle ;  
\- calcul simple de présence ;  
\- correction d’une présence ;  
\- rapport simple ;  
\- export CSV ;  
\- audit ;  
\- cache Redis ;  
\- tests critiques ;  
\- documentation OpenAPI ;  
\- lancement local par Docker Compose.  
  
\## 4.3 Périmètre souhaité  
  
Si le temps le permet :  
  
\- import Excel \`.xlsx\` ;  
\- classeur Excel multifeuille ;  
\- mot de passe oublié ;  
\- invitation locale par email ;  
\- PWA installable ;  
\- WebAuthn ;  
\- notifications internes ;  
\- remplacement d’un formateur ;  
\- justificatifs ;  
\- réclamations ;  
\- rapport Excel ;  
\- simulateur MQTT ;  
\- Raspberry Pi 4 ;  
\- assistance intelligente d’importation.  
  
\## 4.4 Périmètre expérimental  
  
Les fonctions suivantes pourront être réalisées sous forme de preuve  
technique indépendante ou de simulation :  
  
\- MFA TOTP ;  
\- authentification adaptative ;  
\- Cloudflare Turnstile ;  
\- notifications push ;  
\- détection d’anomalies par Isolation Forest ;  
\- lecteur de planning semi-structuré ;  
\- PDF texte ;  
\- file de messages ;  
\- Dead Letter Queue ;  
\- intégration Microsoft Graph ;  
\- synchronisation Teams ;  
\- synchronisation Outlook ;  
\- borne NFC.  
  
\## 4.5 Hors périmètre  
  
Sont exclus du prototype :  
  
\- reconnaissance faciale centralisée ;  
\- collecte ou stockage d’empreintes digitales ;  
\- géolocalisation permanente ;  
\- reconnaissance universelle de tous les PDF ;  
\- application iOS publiée ;  
\- déploiement généralisé dans l’établissement ;  
\- haute disponibilité réelle ;  
\- Kubernetes ;  
\- remplacement complet de Microsoft Teams ;  
\- décision disciplinaire automatisée ;  
\- suppression automatique d’un apprenant ;  
\- reconnaissance biométrique effectuée par le serveur.  
  
\---  
  
\# 5. Terminologie  
  
| Terme | Définition |  
|---|---|  
| Formation | Parcours pédagogique, par exemple BTS, Bachelor ou Master |  
| Niveau | Niveau au sein d’un cursus, par exemple BTS 1, BTS 2, Master 1 ou Master 2 |  
| Promotion | Cohorte associée à une année ou période pédagogique |  
| Classe | Groupe principal auquel un apprenant appartient pour une période |  
| Inscription | Association historique entre un apprenant, une classe et une période |  
| Séance | Occurrence datée et planifiée d’un cours |  
| Demi-journée | Période pédagogique du matin ou de l’après-midi |  
| Point de contrôle | Moment auquel l’apprenant doit confirmer sa présence |  
| Planning | Organisation prévisionnelle des séances d’une classe |  
| Publication | Mise à disposition d’une version validée du planning |  
| Émargement | Action de confirmer une présence |  
| Justificatif | Document destiné à expliquer une absence ou un retard |  
| Réclamation | Demande adressée par un utilisateur à un acteur compétent |  
| WebAuthn | Mécanisme d’authentification cryptographique utilisant l’authentificateur du terminal |  
| PWA | Application web progressive installable |  
| MQTT | Protocole léger de communication pour objets connectés |  
| IA | Fonction d’assistance ou d’analyse reposant sur un modèle ou un ensemble de règles |  
| MVP | Version minimale démontrable du produit |  
| Audit | Historique des opérations significatives |  
| DLQ | File contenant les messages dont le traitement a échoué |  
  
\---  
  
\# 6. Utilisateurs et rôles  
  
\## 6.1 Principes généraux  
  
Un utilisateur peut posséder plusieurs rôles.  
  
Les autorisations doivent être contrôlées :  
  
\- au niveau de la route ;  
\- au niveau du service métier ;  
\- au niveau de la ressource ;  
\- au niveau du périmètre pédagogique.  
  
Le cumul des rôles ne doit pas permettre de contourner les restrictions  
de périmètre.  
  
Un utilisateur possédant plusieurs rôles doit pouvoir sélectionner un  
contexte d’utilisation dans l’interface.  
  
Exemple :  
  
\`\`\`text  
Utilisateur : responsable@example.test  
  
Rôles :  
\- PEDAGOGICAL\_MANAGER  
\- TEACHER  
  
Contextes disponibles :  
\- Gérer mes formations  
\- Consulter mes séances de formateur  
\`\`\`  
  
\## 6.2 \`SUPER\_ADMIN\`  
  
Le super administrateur est responsable des opérations techniques  
critiques.  
  
\### Droits  
  
\- gérer les comptes \`ADMIN\` ;  
\- configurer les paramètres critiques ;  
\- consulter les journaux de sécurité ;  
\- gérer les dispositifs ;  
\- révoquer les sessions ;  
\- suspendre un compte compromis ;  
\- gérer les domaines autorisés ;  
\- configurer les plages réseau ESIC ;  
\- consulter les incidents ;  
\- configurer les intégrations ;  
\- lancer certaines procédures de maintenance ;  
\- supprimer un doublon après contrôle ;  
\- consulter les résultats des sauvegardes.  
  
\### Contraintes  
  
\- le compte \`SUPER\_ADMIN\` doit être distinct du compte quotidien ;  
\- le MFA doit être obligatoire ;  
\- les actions doivent être fortement auditées ;  
\- le rôle ne doit pas être utilisé pour les tâches courantes ;  
\- aucune suppression définitive ne doit être faite sans confirmation.  
  
\## 6.3 \`ADMIN\`  
  
\### Droits  
  
\- gérer les utilisateurs ;  
\- attribuer les rôles non critiques ;  
\- gérer les formations ;  
\- gérer les référentiels ;  
\- gérer les années scolaires ;  
\- consulter les imports ;  
\- traiter les doublons ;  
\- consulter les erreurs d’invitation ;  
\- relancer une invitation ;  
\- gérer les paramètres fonctionnels ;  
\- consulter les audits fonctionnels ;  
\- assister les responsables pédagogiques.  
  
\### Restrictions  
  
\- ne peut pas modifier les secrets techniques ;  
\- ne peut pas effacer les audits ;  
\- ne peut pas créer un autre super administrateur.  
  
\## 6.4 \`SCHOOL\_ADMINISTRATION\`  
  
\### Droits  
  
\- importer des apprenants ;  
\- rechercher un apprenant ;  
\- rechercher une classe ;  
\- consulter les présences ;  
\- gérer les justificatifs ;  
\- intervenir dans les réclamations ;  
\- produire les rapports ;  
\- exporter les données ;  
\- consulter les statistiques globales autorisées ;  
\- suspendre ou archiver des comptes selon une procédure autorisée.  
  
\## 6.5 \`PEDAGOGICAL\_MANAGER\`  
  
Le responsable pédagogique est propriétaire fonctionnel de son  
périmètre.  
  
\### Droits  
  
\- gérer une ou plusieurs formations ;  
\- créer des classes ;  
\- gérer les promotions ;  
\- importer des apprenants ;  
\- déplacer un apprenant vers une nouvelle classe ;  
\- importer un planning ;  
\- créer un planning ;  
\- sauvegarder un brouillon ;  
\- publier un planning ;  
\- affecter un formateur ;  
\- affecter un remplaçant ;  
\- annuler une séance ;  
\- créer une séance exceptionnelle ;  
\- gérer les liens distanciels ;  
\- traiter les corrections anciennes ;  
\- traiter les justificatifs ;  
\- consulter les tableaux de bord ;  
\- produire les rapports ;  
\- autoriser un apprenant à télécharger son rapport ;  
\- traiter les réclamations ;  
\- gérer les autorisations de suivi à distance.  
  
\### Restrictions  
  
\- ne consulte que ses formations ;  
\- ne supprime pas définitivement un utilisateur ;  
\- ne supprime pas l’historique pédagogique ;  
\- ne modifie pas les paramètres techniques.  
  
\## 6.6 \`TEACHER\`  
  
\### Droits  
  
\- consulter son planning ;  
\- consulter ses séances ;  
\- consulter les séances de remplacement ;  
\- demander une annulation ;  
\- proposer un remplaçant ;  
\- ouvrir une séance ;  
\- afficher le QR code ;  
\- consulter les présences en temps réel ;  
\- enregistrer manuellement une présence ;  
\- ajouter un apprenant provisoire ;  
\- saisir un motif de retard ;  
\- joindre un justificatif transmis en classe ;  
\- corriger une présence pendant la période autorisée ;  
\- autoriser exceptionnellement un émargement tardif ;  
\- enregistrer une demande de départ anticipé ;  
\- transmettre une réclamation ;  
\- clôturer la séance.  
  
\### Restrictions  
  
\- ne crée pas librement une séance normale ;  
\- ne publie pas le planning ;  
\- ne valide pas son propre remplacement ;  
\- ne supprime pas une présence ;  
\- ne modifie pas une présence ancienne sans autorisation.  
  
\## 6.7 \`STUDENT\`  
  
\### Droits  
  
\- activer son compte ;  
\- configurer son moyen d’authentification ;  
\- consulter son planning ;  
\- recevoir les notifications ;  
\- consulter les changements ;  
\- scanner un QR code ;  
\- saisir un code temporaire ;  
\- confirmer sa présence ;  
\- consulter son historique ;  
\- consulter son taux d’assiduité ;  
\- consulter ses demi-journées ;  
\- déposer un justificatif ;  
\- créer une réclamation ;  
\- consulter les réponses ;  
\- télécharger son rapport lorsque cette fonction est autorisée ;  
\- consulter l’historique des modifications de ses présences.  
  
\### Restrictions  
  
\- ne consulte aucune donnée d’un autre apprenant ;  
\- ne modifie pas directement une présence ;  
\- ne peut pas valider une présence hors séance autorisée ;  
\- ne peut pas utiliser un jeton expiré.  
  
\---  
  
\# 7. Référentiels pédagogiques  
  
\## 7.1 Formations  
  
Le système doit permettre de gérer :  
  
\- BTS ;  
\- Bachelor ;  
\- Master ;  
\- autres formations configurables.  
  
Une formation doit comporter :  
  
| Champ | Obligatoire | Description |  
|---|---:|---|  
| Identifiant | Oui | Identifiant interne non prédictible |  
| Code | Oui | Code unique et lisible |  
| Nom | Oui | Intitulé |  
| Type | Oui | BTS, Bachelor, Master ou autre |  
| Description | Non | Présentation |  
| Statut | Oui | Actif, inactif ou archivé |  
| Responsable principal | Oui | Responsable pédagogique principal |  
| Date de création | Oui | Horodatage |  
| Date de modification | Oui | Horodatage |  
  
\## 7.2 Niveaux  
  
Les niveaux doivent être configurables.  
  
Exemples :  
  
\- BTS 1 ;  
\- BTS 2 ;  
\- Bachelor 1 ;  
\- Bachelor 2 ;  
\- Bachelor 3 ;  
\- Master 1 ;  
\- Master 2.  
  
\## 7.3 Année scolaire  
  
La période pédagogique est définie depuis le planning ou depuis les  
paramètres de la promotion.  
  
Elle ne doit pas être limitée à une convention figée.  
  
Une année scolaire contient :  
  
\- un nom ;  
\- une date de début ;  
\- une date de fin ;  
\- un statut ;  
\- éventuellement une période d’archivage.  
  
\## 7.4 Promotion  
  
Une promotion représente une cohorte rattachée à une formation et à une  
période.  
  
Exemple :  
  
\`\`\`text  
Formation : ESI  
Promotion : 2026-2027  
Classe : ESI 2026-2027  
\`\`\`  
  
\## 7.5 Classe  
  
Une classe doit contenir :  
  
\- un code unique dans son contexte ;  
\- un nom ;  
\- une formation ;  
\- un niveau ;  
\- une promotion ;  
\- une année scolaire ;  
\- un responsable pédagogique ;  
\- une capacité ;  
\- un rythme pédagogique ;  
\- un statut ;  
\- une liste d’inscriptions.  
  
\## 7.6 Appartenance à une classe  
  
Un apprenant ne peut appartenir qu’à une seule classe principale active  
pour une même période.  
  
Il peut toutefois posséder plusieurs inscriptions historiques.  
  
Lors d’un changement de classe :  
  
1\. l’ancienne inscription est clôturée ;  
2\. son historique est conservé ;  
3\. la nouvelle inscription est créée ;  
4\. les données déjà enregistrées ne sont pas écrasées ;  
5\. l’opération est auditée.  
  
\## 7.7 Cours communs à plusieurs classes  
  
Une séance peut concerner plusieurs classes lorsque plusieurs groupes  
suivent un cours commun.  
  
Le système doit donc distinguer :  
  
\- la classe principale de l’apprenant ;  
\- les classes concernées par une séance ;  
\- la liste réelle des participants attendus.  
  
Une table d’association entre les séances et les classes doit être  
prévue.  
  
\## 7.8 Groupes temporaires  
  
Les groupes temporaires ne sont pas requis dans le MVP.  
  
Le modèle doit néanmoins permettre une évolution future vers :  
  
\- groupes de langues ;  
\- groupes d’options ;  
\- groupes de projet ;  
\- regroupements temporaires.  
  
\---  
  
\# 8. Rythmes pédagogiques et alternance  
  
\## 8.1 Objectif  
  
Le système doit distinguer :  
  
\- une absence réelle ;  
\- une journée sans cours ;  
\- une journée en entreprise ;  
\- une semaine hors établissement prévue ;  
\- une exception imposant une présence à l’école.  
  
\## 8.2 Rythmes obligatoires du MVP  
  
Le système doit prendre en charge les trois rythmes suivants :  
  
\### Rythme A — Trois jours à l’école et deux jours en entreprise  
  
Exemple :  
  
\`\`\`text  
Lundi     : école  
Mardi     : école  
Mercredi  : école  
Jeudi     : entreprise  
Vendredi  : entreprise  
\`\`\`  
  
Le rythme réel doit rester configurable.  
  
\### Rythme B — Une semaine à l’école sur quatre  
  
Une semaine de cours est suivie de semaines normalement prévues en  
entreprise.  
  
\### Rythme C — Deux semaines à l’école sur quatre  
  
Deux semaines sont planifiées à l’école et deux semaines hors école.  
  
\## 8.3 Rythme personnalisé  
  
Le système doit permettre de définir :  
  
\- un rythme au niveau de la classe ;  
\- une exception au niveau d’une période ;  
\- une exception individuelle ;  
\- une présence exceptionnelle à l’école ;  
\- un cours exceptionnel hors calendrier habituel.  
  
\## 8.4 Règles  
  
\- une période en entreprise ne doit pas être comptabilisée comme une  
  absence ;  
\- seules les séances publiées créent une attente de présence ;  
\- une séance exceptionnelle peut remplacer la règle d’alternance ;  
\- toute exception doit être datée ;  
\- toute exception doit être auditée ;  
\- le calcul d’assiduité doit se baser sur les séances réellement  
  attendues pour l’apprenant.  
  
\---  
  
\# 9. Gestion des utilisateurs  
  
\## 9.1 Données minimales  
  
Les données communes sont :  
  
| Champ | Obligatoire |  
|---|---:|  
| Identifiant technique | Oui |  
| Nom | Oui |  
| Prénom | Oui |  
| Adresse électronique | Oui |  
| Téléphone | Non |  
| Statut | Oui |  
| Rôles | Oui |  
| Date de création | Oui |  
| Date de dernière modification | Oui |  
  
\## 9.2 Données apprenant  
  
Les champs supplémentaires peuvent comprendre :  
  
| Champ | Priorité |  
|---|---|  
| Numéro étudiant | Obligatoire |  
| Date de naissance | Facultative |  
| Statut d’alternance | Obligatoire si concerné |  
| Entreprise d’alternance | Facultative |  
| Classe active | Obligatoire |  
| Historique des inscriptions | Obligatoire |  
| Date d’entrée | Facultative |  
| Date de sortie | Facultative |  
| Autorisation de suivi distant | Selon situation |  
| Téléchargement du rapport autorisé | Oui/non |  
  
\## 9.3 Adresse électronique  
  
L’adresse électronique doit être unique par utilisateur.  
  
Une même adresse peut être conservée pendant tout le parcours de  
l’apprenant, du BTS au Master.  
  
Le changement de classe ou d’année ne crée pas un nouveau compte.  
  
\## 9.4 Statuts de compte  
  
\- \`PENDING\_ACTIVATION\` ;  
\- \`ACTIVE\` ;  
\- \`SUSPENDED\` ;  
\- \`LOCKED\` ;  
\- \`ARCHIVED\`.  
  
\## 9.5 Suspension et archivage  
  
Lorsqu’un apprenant quitte l’établissement :  
  
\- son compte n’est pas supprimé ;  
\- il est suspendu ou archivé ;  
\- il ne peut plus se connecter ;  
\- son historique est conservé ;  
\- une réactivation est possible ;  
\- la réactivation nécessite une action autorisée ;  
\- la justification est auditée.  
  
\## 9.6 Gestion groupée  
  
Le système doit permettre :  
  
\- la suspension groupée ;  
\- l’archivage groupé ;  
\- le déplacement groupé ;  
\- l’activation groupée ;  
\- la relance groupée des invitations.  
  
Avant une opération de masse, le système doit afficher :  
  
\- le nombre d’utilisateurs concernés ;  
\- les conséquences ;  
\- les erreurs ;  
\- les éléments ignorés ;  
\- une demande de confirmation.  
  
\## 9.7 Suppression  
  
La suppression fonctionnelle est remplacée par l’archivage.  
  
La suppression définitive est réservée :  
  
\- aux doublons avérés ;  
\- aux données de démonstration ;  
\- aux demandes validées selon la procédure ;  
\- aux administrateurs autorisés.  
  
Une suppression définitive doit être :  
  
\- exceptionnelle ;  
\- confirmée ;  
\- auditée ;  
\- impossible si elle détruit un historique requis.  
  
\---  
  
\# 10. Importation des apprenants  
  
\## 10.1 Acteurs autorisés  
  
Les rôles autorisés sont :  
  
\- \`ADMIN\` ;  
\- \`SCHOOL\_ADMINISTRATION\` ;  
\- \`PEDAGOGICAL\_MANAGER\`.  
  
Le responsable pédagogique est limité à son périmètre.  
  
\## 10.2 Formats  
  
\- CSV obligatoire ;  
\- XLSX souhaité ;  
\- classeur multifeuille souhaité.  
  
\## 10.3 Volume  
  
Le système doit accepter au minimum :  
  
\- 100 apprenants par import ;  
\- plusieurs imports successifs ;  
\- plusieurs classes dans un classeur.  
  
\## 10.4 Colonnes de référence  
  
\`\`\`text  
student\_number  
last\_name  
first\_name  
email  
phone  
birth\_date  
formation\_code  
level\_code  
promotion\_code  
class\_code  
academic\_year  
work\_study  
work\_study\_pattern  
company\_name  
\`\`\`  
  
\## 10.5 Classeur multifeuille  
  
Chaque feuille peut correspondre à une classe.  
  
La correspondance peut être déterminée par :  
  
1\. le nom de la feuille ;  
2\. une colonne \`class\_code\` ;  
3\. une sélection manuelle ;  
4\. une suggestion de l’assistant d’importation.  
  
Aucune affectation ne doit être appliquée sans confirmation.  
  
\## 10.6 Mode simulation  
  
L’importation doit fonctionner en deux phases :  
  
\### Phase 1 — Simulation  
  
\- lecture ;  
\- normalisation ;  
\- validation ;  
\- détection des doublons ;  
\- détection des utilisateurs existants ;  
\- calcul des changements ;  
\- affichage des erreurs ;  
\- aucune écriture métier définitive.  
  
\### Phase 2 — Application  
  
\- confirmation ;  
\- création ;  
\- mise à jour ;  
\- changement de classe ;  
\- invitation ;  
\- rapport d’importation ;  
\- audit.  
  
\## 10.7 Gestion d’un utilisateur existant  
  
Si l’adresse ou le numéro étudiant existe :  
  
\- le système ne crée pas de doublon ;  
\- il affiche le compte existant ;  
\- il propose une mise à jour ;  
\- il affiche la classe actuelle ;  
\- il affiche la classe cible ;  
\- il demande confirmation ;  
\- il clôture l’ancienne inscription si nécessaire ;  
\- il crée la nouvelle inscription ;  
\- il conserve l’historique.  
  
\## 10.8 Gestion des erreurs  
  
Chaque erreur doit indiquer :  
  
\- le fichier ;  
\- la feuille ;  
\- le numéro de ligne ;  
\- la colonne ;  
\- la valeur reçue ;  
\- le motif ;  
\- la correction attendue ;  
\- la gravité.  
  
Niveaux :  
  
\- \`INFO\` ;  
\- \`WARNING\` ;  
\- \`ERROR\` ;  
\- \`BLOCKING\`.  
  
\## 10.9 Critères d’acceptation  
  
\### IMP-STU-01  
  
\*\*Étant donné\*\* un fichier valide de 100 apprenants,    
\*\*quand\*\* le responsable lance la simulation,    
\*\*alors\*\* toutes les lignes doivent être analysées sans création  
définitive de compte.  
  
\### IMP-STU-02  
  
\*\*Étant donné\*\* un apprenant déjà présent,    
\*\*quand\*\* il apparaît dans une nouvelle classe,    
\*\*alors\*\* le système doit proposer une mise à jour sans doublon.  
  
\### IMP-STU-03  
  
\*\*Étant donné\*\* une ligne invalide,    
\*\*quand\*\* l’import est analysé,    
\*\*alors\*\* la ligne, la colonne et la raison doivent être affichées.  
  
\### IMP-STU-04  
  
\*\*Étant donné\*\* une opération de masse,    
\*\*quand\*\* l’utilisateur confirme,    
\*\*alors\*\* le bilan doit indiquer les créations, mises à jour, erreurs et  
lignes ignorées.  
  
\---  
  
\# 11. Invitation et activation  
  
\## 11.1 Invitation automatique  
  
Après création d’un compte apprenant :  
  
1\. le compte reçoit le statut \`PENDING\_ACTIVATION\` ;  
2\. un jeton est généré ;  
3\. le jeton est associé au compte ;  
4\. sa durée de validité est fixée à un mois ;  
5\. un email d’invitation est préparé ;  
6\. l’événement est journalisé.  
  
\## 11.2 Contenu de l’invitation  
  
Le message doit indiquer :  
  
\- l’identité de la plateforme ;  
\- la raison de l’invitation ;  
\- l’établissement ;  
\- un lien temporaire ;  
\- la date d’expiration ;  
\- la procédure en cas d’erreur ;  
\- les informations de sécurité.  
  
\## 11.3 Traçabilité  
  
Statuts internes :  
  
\- \`QUEUED\` ;  
\- \`SENT\_TO\_PROVIDER\` ;  
\- \`PROCESSING\_FAILED\`.  
  
Statuts externes, si disponibles :  
  
\- \`DELIVERED\` ;  
\- \`BOUNCED\` ;  
\- \`REJECTED\` ;  
\- \`COMPLAINED\` ;  
\- \`UNKNOWN\`.  
  
\## 11.4 Réémission  
  
Un acteur autorisé peut :  
  
\- corriger l’adresse ;  
\- révoquer l’ancien jeton ;  
\- générer un nouveau jeton ;  
\- relancer l’envoi ;  
\- consulter la nouvelle tentative.  
  
\---  
  
\# 12. Formateurs internes, externes et remplaçants  
  
\## 12.1 Formateur interne  
  
Un formateur interne peut utiliser :  
  
\- son compte Microsoft institutionnel ;  
\- une adresse autorisée par l’établissement ;  
\- à terme, une connexion Microsoft 365.  
  
\## 12.2 Formateur externe  
  
Un formateur externe peut utiliser une adresse personnelle ou  
professionnelle externe.  
  
Les contrôles ne doivent pas se baser uniquement sur le domaine du mail.  
  
\## 12.3 Affectation pédagogique  
  
Un formateur peut enseigner :  
  
\- plusieurs matières ;  
\- dans plusieurs classes ;  
\- à différentes dates ;  
\- pour plusieurs formations.  
  
La matière ne doit donc pas posséder un formateur unique global.  
  
L’affectation doit se faire :  
  
\- au niveau de la séance ;  
\- au niveau d’une période ;  
\- ou au niveau d’une association classe-matière-période.  
  
\## 12.4 Remplacement  
  
Le responsable pédagogique peut :  
  
\- désigner un remplaçant ;  
\- sélectionner les séances ;  
\- renseigner un motif ;  
\- définir une période ;  
\- notifier les acteurs.  
  
Le formateur initial peut :  
  
\- proposer un remplaçant ;  
\- demander son remplacement ;  
\- demander une annulation.  
  
Il ne peut pas valider lui-même le remplacement.  
  
\## 12.5 Notification du remplacement  
  
Le responsable pédagogique choisit si les apprenants doivent être  
notifiés.  
  
Le formateur initial et le remplaçant doivent toujours être notifiés.  
  
\---  
  
\# 13. Gestion du planning  
  
\## 13.1 Sources actuelles  
  
Les plannings existants peuvent provenir de :  
  
\- Microsoft Excel ;  
\- Google Sheets exporté ;  
\- tableaux contenant les jours ;  
\- colonnes matin et après-midi ;  
\- cellules regroupant le cours et le formateur.  
  
\## 13.2 Stratégie d’intégration  
  
Le MVP doit proposer un modèle structuré.  
  
L’architecture cible doit prévoir un assistant capable de transformer un  
planning existant vers ce modèle.  
  
\## 13.3 Colonnes de référence  
  
\`\`\`text  
academic\_year  
formation\_code  
promotion\_code  
class\_code  
session\_date  
half\_day  
start\_time  
end\_time  
course\_code  
course\_name  
teacher\_email  
room\_code  
attendance\_mode  
remote\_link  
work\_study\_exception  
notes  
\`\`\`  
  
\## 13.4 Planning annuel par classe  
  
Un planning peut être importé pour :  
  
\- une classe ;  
\- une année scolaire ;  
\- une période ;  
\- une version.  
  
Le responsable peut ensuite compléter progressivement :  
  
\- la matière ;  
\- le formateur ;  
\- la salle ;  
\- le lien distant ;  
\- les exceptions.  
  
\## 13.5 Création manuelle  
  
Le responsable pédagogique doit pouvoir :  
  
\- créer un planning depuis l’interface ;  
\- ajouter une plage ;  
\- modifier une plage ;  
\- dupliquer une semaine ;  
\- répéter une séance ;  
\- appliquer un rythme ;  
\- enregistrer un brouillon ;  
\- publier.  
  
\## 13.6 Statuts  
  
\- \`DRAFT\` ;  
\- \`VALIDATING\` ;  
\- \`READY\_TO\_PUBLISH\` ;  
\- \`PUBLISHED\` ;  
\- \`ARCHIVED\` ;  
\- \`REJECTED\`.  
  
\## 13.7 Versionnement  
  
Le système conserve au minimum les trois dernières versions.  
  
Pour chaque version :  
  
\- numéro ;  
\- auteur ;  
\- date ;  
\- motif ;  
\- nombre de changements ;  
\- statut ;  
\- version précédente.  
  
Le responsable peut revenir à une version précédente.  
  
\## 13.8 Publication  
  
La publication :  
  
\- crée ou met à jour les séances ;  
\- rend le planning visible ;  
\- prépare les notifications ;  
\- invalide les caches concernés ;  
\- conserve une preuve ;  
\- refuse les conflits bloquants.  
  
\## 13.9 Modification d’une séance publiée  
  
Toute modification doit :  
  
\- créer une nouvelle version ;  
\- identifier les champs modifiés ;  
\- notifier le formateur ;  
\- notifier les apprenants lorsque nécessaire ;  
\- mettre à jour les calendriers ;  
\- invalider le cache ;  
\- être auditée.  
  
\## 13.10 Assistant intelligent d’importation  
  
\### Objectifs  
  
L’assistant doit réduire le travail nécessaire lorsque le fichier ne  
respecte pas exactement le modèle.  
  
\### Capacités  
  
\- détecter la ligne d’en-tête ;  
\- reconnaître des synonymes ;  
\- séparer un cours et un formateur présents dans une même cellule ;  
\- identifier un jour ;  
\- reconnaître le matin ou l’après-midi ;  
\- normaliser les horaires ;  
\- proposer une matière existante ;  
\- proposer un formateur existant ;  
\- signaler un résultat incertain ;  
\- produire un score de confiance.  
  
\### Règles  
  
\- aucune transformation incertaine n’est automatiquement publiée ;  
\- les propositions restent modifiables ;  
\- le fichier d’origine est conservé pour la traçabilité ;  
\- la confirmation humaine est obligatoire.  
  
\### Statuts des propositions  
  
\- \`CONFIDENT\` ;  
\- \`TO\_REVIEW\` ;  
\- \`UNRESOLVED\`.  
  
\---  
  
\# 14. Gestion des salles  
  
\## 14.1 Référentiel  
  
Une salle peut comporter :  
  
\- code ;  
\- nom ;  
\- bâtiment ;  
\- étage ;  
\- capacité ;  
\- équipement ;  
\- état ;  
\- QR code fixe ;  
\- plage réseau autorisée ;  
\- identifiant de borne IoT.  
  
\## 14.2 Affectation  
  
La salle peut être :  
  
\- connue à l’import ;  
\- affectée plus tard ;  
\- modifiée avant la séance ;  
\- gérée par l’administration ;  
\- laissée provisoirement indéterminée.  
  
\## 14.3 Conflits  
  
Le système doit signaler :  
  
\- deux séances dans la même salle ;  
\- une capacité insuffisante ;  
\- une salle inactive ;  
\- une salle absente ;  
\- une incohérence entre le mode et la salle.  
  
\---  
  
\# 15. Gestion des séances  
  
\## 15.1 Création  
  
Une séance normale provient d’un planning publié.  
  
Une séance exceptionnelle peut être créée par un responsable  
pédagogique avec :  
  
\- classe ;  
\- matière ;  
\- formateur ;  
\- date ;  
\- horaire ;  
\- salle ou lien ;  
\- motif ;  
\- type d’exception.  
  
\## 15.2 Statuts  
  
\- \`DRAFT\` ;  
\- \`PLANNED\` ;  
\- \`OPEN\` ;  
\- \`CLOSED\` ;  
\- \`CANCELLED\` ;  
\- \`POSTPONED\`.  
  
\## 15.3 Horaires de référence  
  
L’établissement fonctionne habituellement selon les plages suivantes :  
  
\`\`\`text  
Matin :  
\- début : 09:00  
\- fin indicative : 12:30  
  
Après-midi :  
\- début : 13:30  
\- fin habituelle : 17:00  
  
Vendredi :  
\- fin habituelle : 16:00  
\`\`\`  
  
Ces horaires doivent être configurables.  
  
\## 15.4 Annulation  
  
Le responsable pédagogique peut annuler une séance.  
  
Le formateur peut demander l’annulation.  
  
Une annulation doit contenir :  
  
\- auteur ;  
\- demandeur ;  
\- motif ;  
\- date ;  
\- décision ;  
\- notifications ;  
\- éventuel commentaire.  
  
Une séance annulée n’est pas automatiquement reportée.  
  
Le responsable pédagogique définit une nouvelle date si nécessaire.  
  
\## 15.5 Demande d’annulation  
  
Statuts :  
  
\- \`REQUESTED\` ;  
\- \`APPROVED\` ;  
\- \`REJECTED\` ;  
\- \`CANCELLED\`.  
  
\---  
  
\# 16. Cours présentiels, distanciels et hybrides  
  
\## 16.1 Présentiel  
  
L’apprenant est attendu sur le site et utilise :  
  
\- le QR fixe de la salle avant le début ;  
\- le QR dynamique du formateur après le début ;  
\- ou une validation manuelle exceptionnelle.  
  
\## 16.2 Distanciel collectif  
  
Une séance peut être déclarée à distance pour toute la classe.  
  
Le lien peut être :  
  
\- ajouté manuellement ;  
\- partagé dans l’application ;  
\- synchronisé à terme avec Teams.  
  
\## 16.3 Distanciel individuel  
  
Un apprenant peut être autorisé à distance alors que le reste de la  
classe est en présentiel.  
  
L’autorisation peut être valable :  
  
\- pour une séance ;  
\- pour une période ;  
\- pour l’année scolaire.  
  
Elle doit comporter :  
  
\- l’apprenant ;  
\- l’auteur ;  
\- le motif ;  
\- la période ;  
\- le statut ;  
\- la date de décision.  
  
\## 16.4 Mode hybride  
  
Une même séance peut contenir :  
  
\- des participants en présentiel ;  
\- des participants à distance.  
  
Le canal de présence doit être enregistré.  
  
Valeurs :  
  
\- \`ROOM\_STATIC\_QR\` ;  
\- \`TEACHER\_DYNAMIC\_QR\` ;  
\- \`REMOTE\_QR\` ;  
\- \`REMOTE\_CODE\` ;  
\- \`TEACHER\_MANUAL\` ;  
\- \`PEDAGOGICAL\_MANUAL\` ;  
\- \`IOT\_TERMINAL\`.  
  
\---  
  
\# 17. Émargement  
  
\## 17.1 Principes  
  
L’émargement doit être :  
  
\- rapide ;  
\- sécurisé ;  
\- accessible ;  
\- traçable ;  
\- compatible avec les cours hybrides ;  
\- résistant au rejeu ;  
\- compatible avec un contrôle humain.  
  
\## 17.2 Points de contrôle journaliers  
  
Le système doit permettre quatre contrôles :  
  
1\. arrivée du matin ;  
2\. retour de la pause du matin ;  
3\. arrivée ou retour après la pause de midi ;  
4\. retour de la pause de l’après-midi.  
  
Les horaires exacts sont définis par le planning ou les paramètres de la  
séance.  
  
\## 17.3 Types de point de contrôle  
  
\- \`MORNING\_ARRIVAL\` ;  
\- \`MORNING\_BREAK\_RETURN\` ;  
\- \`AFTERNOON\_ARRIVAL\` ;  
\- \`AFTERNOON\_BREAK\_RETURN\`.  
  
\## 17.4 Calcul par demi-journée  
  
\### Matin  
  
Le matin est validé lorsque :  
  
\- l’arrivée du matin est validée ;  
\- le retour de la pause du matin est validé.  
  
\### Après-midi  
  
L’après-midi est validé lorsque :  
  
\- l’arrivée de l’après-midi est validée ;  
\- le retour de la pause de l’après-midi est validé.  
  
\## 17.5 Calcul journalier  
  
| Résultat | Règle générale |  
|---|---|  
| Journée complète | Quatre validations cohérentes |  
| Demi-journée matin | Deux validations cohérentes du matin |  
| Demi-journée après-midi | Deux validations cohérentes de l’après-midi |  
| Partiel | Validations incomplètes |  
| À confirmer | Incohérence ou incident signalé |  
| Absent | Aucune validation et aucune correction |  
| Excusé | Absence justifiée et validée |  
  
\## 17.6 Tolérance de retard  
  
Règle validée :  
  
\- de 0 à 15 minutes après le début : \`PRESENT\` ;  
\- de 16 à 30 minutes : \`LATE\` ;  
\- après 30 minutes : \`LATE\` avec validation manuelle ;  
\- après la fenêtre normale : autorisation exceptionnelle du formateur.  
  
\## 17.7 Ouverture de l’émargement  
  
L’émargement peut être ouvert :  
  
\- 15 minutes avant le début ;  
\- automatiquement ou par le formateur ;  
\- jusqu’à 15 minutes après le début dans le parcours standard.  
  
Après cette période :  
  
\- le QR fixe n’est plus accepté ;  
\- le QR dynamique du formateur peut être utilisé ;  
\- le formateur contrôle la situation ;  
\- une présence tardive peut être enregistrée exceptionnellement.  
  
\## 17.8 QR fixe de salle  
  
Le QR fixe :  
  
\- est imprimé ;  
\- est associé à une salle ;  
\- ne contient pas de donnée personnelle ;  
\- est utilisable avant et jusqu’au début de la séance ;  
\- nécessite une séance active ou imminente ;  
\- nécessite le réseau ESIC ;  
\- est rejeté hors fenêtre ;  
\- est rejeté hors plage réseau autorisée.  
  
Le QR fixe identifie une ressource de salle, pas directement une séance.  
  
Le serveur détermine :  
  
\- la salle ;  
\- la séance active ;  
\- l’apprenant ;  
\- son inscription ;  
\- la fenêtre d’émargement.  
  
\## 17.9 Contrôle réseau  
  
Le système doit vérifier temporairement si la requête provient d’une  
plage réseau autorisée.  
  
Les plages réseau sont configurées par le super administrateur.  
  
L’adresse IP :  
  
\- est utilisée pendant la décision ;  
\- n’est pas conservée dans l’audit métier ;  
\- ne doit pas apparaître dans les rapports ;  
\- peut être présente temporairement dans les journaux techniques du  
  serveur selon la configuration, avec accès limité et durée maîtrisée.  
  
\## 17.10 QR dynamique du formateur  
  
Le QR dynamique :  
  
\- est lié à une séance ;  
\- change périodiquement ;  
\- est généré côté serveur ;  
\- utilise Redis ;  
\- possède une expiration ;  
\- peut être affiché sur l’écran du formateur ;  
\- est utilisable dans la salle ou à distance selon le mode.  
  
\### Fréquence cible  
  
Le code visuel peut changer toutes les 10 secondes.  
  
Le serveur peut accepter :  
  
\- le code courant ;  
\- éventuellement le code immédiatement précédent pendant une courte  
  période de grâce.  
  
Cette tolérance vise à absorber :  
  
\- la latence ;  
\- le temps de scan ;  
\- les variations d’horloge.  
  
\## 17.11 Durée globale d’émargement  
  
La disponibilité du QR dynamique doit dépendre :  
  
\- du statut de la séance ;  
\- de la fenêtre d’émargement ;  
\- d’une décision du formateur ;  
\- du point de contrôle en cours.  
  
Après la fenêtre initiale, le formateur peut réafficher un QR pour une  
validation tardive jusqu’à la limite autorisée.  
  
\## 17.12 Code alternatif  
  
Chaque QR dynamique peut être accompagné d’un code court :  
  
\- limité dans le temps ;  
\- lié à la séance ;  
\- saisissable dans l’application ;  
\- soumis aux mêmes vérifications.  
  
Le code alternatif est destiné :  
  
\- au distanciel ;  
\- aux problèmes de caméra ;  
\- aux utilisateurs suivant le cours sur le même téléphone.  
  
\## 17.13 Prévention du rejeu  
  
Le serveur doit vérifier :  
  
\- l’identifiant du jeton ;  
\- sa date d’expiration ;  
\- son point de contrôle ;  
\- sa séance ;  
\- son état ;  
\- l’unicité de la validation ;  
\- l’utilisateur ;  
\- son autorisation.  
  
\## 17.14 Présence manuelle  
  
Le formateur peut enregistrer manuellement une présence lorsque :  
  
\- l’apprenant n’a pas de smartphone ;  
\- la caméra ne fonctionne pas ;  
\- WebAuthn est indisponible ;  
\- un incident technique est constaté ;  
\- un retard exceptionnel est justifié ;  
\- l’apprenant suit à distance depuis un ordinateur.  
  
La saisie doit comprendre :  
  
\- le motif ;  
\- le canal ;  
\- l’heure ;  
\- l’auteur ;  
\- la justification éventuelle.  
  
\## 17.15 Apprenant non inscrit  
  
Le formateur peut créer une entrée provisoire pour un nouvel apprenant.  
  
Statut :  
  
\- \`UNREGISTERED\_GUEST\` ;  
\- ou \`PENDING\_REGISTRATION\`.  
  
Données minimales :  
  
\- nom ;  
\- prénom ;  
\- email si connu ;  
\- commentaire ;  
\- séance ;  
\- auteur.  
  
Cette entrée :  
  
\- ne crée pas automatiquement une inscription officielle ;  
\- est signalée au responsable pédagogique ;  
\- doit être régularisée ;  
\- reste séparée d’un compte officiel tant que la correspondance n’est  
  pas validée.  
  
\---  
  
\# 18. WebAuthn et authentification adaptée  
  
\## 18.1 Objectif  
  
WebAuthn doit permettre :  
  
\- une connexion simplifiée sur un appareil enregistré ;  
\- une confirmation locale de l’émargement ;  
\- une réduction de l’usage répétitif des mots de passe ;  
\- une meilleure résistance à l’hameçonnage.  
  
\## 18.2 Première connexion  
  
Le parcours cible est :  
  
1\. saisie de l’adresse électronique ;  
2\. mot de passe ;  
3\. second facteur lorsque requis ;  
4\. activation ou vérification du compte ;  
5\. proposition d’enregistrement d’une passkey ;  
6\. confirmation locale ;  
7\. appareil ajouté comme appareil de confiance.  
  
\## 18.3 Connexions suivantes  
  
Sur un appareil reconnu :  
  
\- l’utilisateur peut utiliser WebAuthn ;  
\- l’authentificateur local peut être une empreinte, Face ID ou un PIN ;  
\- l’utilisateur n’a pas à saisir un second facteur à chaque ouverture ;  
\- la session peut être renouvelée selon la politique de sécurité.  
  
\## 18.4 Réauthentification forte  
  
Une vérification renforcée est demandée lorsque :  
  
\- l’utilisateur change d’appareil ;  
\- l’utilisateur change de pays de manière inhabituelle ;  
\- l’utilisateur réinitialise l’application ;  
\- le mot de passe est réinitialisé ;  
\- une passkey est supprimée ;  
\- une nouvelle passkey est ajoutée ;  
\- une connexion est jugée inhabituelle ;  
\- une action critique est demandée.  
  
Cette approche correspond à une authentification adaptative : les  
contrôles supplémentaires sont déclenchés selon le risque plutôt qu’à  
chaque action ordinaire. (\[cheatsheetseries.owasp.org\](https://cheatsheetseries.owasp.org/cheatsheets/Authentication\_Cheat\_Sheet.html?utm\_source=openai))  
  
\## 18.5 Protection des données biométriques  
  
ESIC Connect :  
  
\- ne stocke aucune empreinte ;  
\- ne stocke aucun modèle facial ;  
\- ne reçoit pas les données biométriques ;  
\- reçoit une réponse cryptographique ;  
\- laisse la vérification au système d’exploitation.  
  
\## 18.6 Solutions de secours  
  
\- mot de passe ;  
\- code TOTP ;  
\- codes de récupération ;  
\- procédure de récupération ;  
\- validation manuelle contrôlée ;  
\- réenrôlement d’un appareil.  
  
\---  
  
\# 19. Présence à distance  
  
\## 19.1 QR distant  
  
Le formateur peut :  
  
\- partager son écran ;  
\- afficher le QR dynamique ;  
\- afficher le code court ;  
\- envoyer une notification d’ouverture.  
  
\## 19.2 Appareil unique  
  
Si l’apprenant suit le cours sur son téléphone :  
  
\- il utilise le code court ;  
\- ou un lien profond ouvre ESIC Connect ;  
\- il confirme localement son identité ;  
\- la présence est transmise au serveur.  
  
\## 19.3 Connexion et déconnexion  
  
Le système doit pouvoir enregistrer :  
  
\- l’heure de validation du point de contrôle ;  
\- le canal distant ;  
\- l’heure de connexion déclarée ou intégrée ;  
\- l’heure de déconnexion si une intégration est disponible.  
  
Dans le MVP, les quatre points de contrôle sont prioritaires sur une  
mesure continue de la connexion.  
  
\## 19.4 Autorisation individuelle  
  
L’autorisation à distance doit être contrôlée lors de l’émargement.  
  
Sans autorisation :  
  
\- le canal distant est refusé ;  
\- le formateur peut signaler l’exception ;  
\- le responsable peut régulariser.  
  
\---  
  
\# 20. Départ anticipé  
  
\## 20.1 Demande  
  
L’apprenant signale son départ au formateur.  
  
Le formateur peut :  
  
\- accepter ;  
\- refuser ;  
\- recommander favorablement la demande ;  
\- transmettre au responsable pédagogique.  
  
\## 20.2 Données  
  
\- apprenant ;  
\- séance ;  
\- heure de départ ;  
\- motif ;  
\- avis du formateur ;  
\- décision ;  
\- auteur ;  
\- commentaire.  
  
\## 20.3 Impact  
  
Le départ anticipé peut produire :  
  
\- \`PARTIAL\` ;  
\- \`EXCUSED\_PARTIAL\` dans une évolution ;  
\- \`TO\_CONFIRM\`.  
  
\---  
  
\# 21. Justificatifs  
  
\## 21.1 Acteurs  
  
Un justificatif peut être déposé par :  
  
\- l’apprenant ;  
\- le formateur pour le compte de l’apprenant ;  
\- le responsable pédagogique ;  
\- l’administration scolaire.  
  
\## 21.2 Portée  
  
Un justificatif peut concerner :  
  
\- une séance ;  
\- une demi-journée ;  
\- une journée ;  
\- une période.  
  
\## 21.3 Formats  
  
\- JPEG ;  
\- PNG ;  
\- PDF.  
  
\## 21.4 Taille  
  
Taille maximale :  
  
\`\`\`text  
5 Mo par fichier  
\`\`\`  
  
\## 21.5 Sécurité des fichiers  
  
Le système doit :  
  
\- vérifier l’extension ;  
\- vérifier le type MIME ;  
\- vérifier la taille ;  
\- générer un nom interne ;  
\- ne pas exécuter les fichiers ;  
\- stocker les fichiers hors répertoire public ;  
\- limiter les droits d’accès ;  
\- prévoir une analyse antivirus ;  
\- empêcher les traversées de chemin.  
  
\## 21.6 Statuts  
  
\- \`SUBMITTED\` ;  
\- \`UNDER\_REVIEW\` ;  
\- \`ACCEPTED\` ;  
\- \`REJECTED\` ;  
\- \`ADDITIONAL\_INFORMATION\_REQUIRED\` ;  
\- \`EXPIRED\`.  
  
\## 21.7 Validation  
  
Le responsable pédagogique ou le formateur autorisé peut valider ou  
refuser.  
  
L’administration peut intervenir selon son périmètre.  
  
Un refus exige un motif.  
  
\## 21.8 Délai  
  
L’apprenant dispose d’un mois pour transmettre son justificatif.  
  
Le délai doit être configurable.  
  
\## 21.9 Effet d’une validation  
  
Un justificatif accepté transforme :  
  
\`\`\`text  
ABSENT → EXCUSED  
\`\`\`  
  
Il ne transforme pas une absence en présence.  
  
\## 21.10 Conservation  
  
Les justificatifs sont conservés pendant 12 mois dans la configuration  
initiale, puis supprimés ou archivés selon la politique validée.  
  
Les métadonnées nécessaires à la traçabilité peuvent être conservées  
plus longtemps si cela est justifié.  
  
\---  
  
\# 22. Réclamations et messagerie  
  
\## 22.1 Principe  
  
La réclamation doit offrir un échange conversationnel sans devenir une  
messagerie instantanée générale.  
  
\## 22.2 Destinataires  
  
\- formateur ;  
\- responsable pédagogique ;  
\- administration scolaire.  
  
\## 22.3 Portée  
  
Une réclamation peut concerner :  
  
\- une séance ;  
\- une demi-journée ;  
\- une période ;  
\- un justificatif ;  
\- un planning ;  
\- une présence ;  
\- une question administrative.  
  
\## 22.4 Données  
  
\- auteur ;  
\- catégorie ;  
\- sujet ;  
\- description ;  
\- séance ou période ;  
\- destinataire fonctionnel ;  
\- priorité ;  
\- statut ;  
\- pièces jointes ;  
\- messages ;  
\- historique.  
  
\## 22.5 Conversation  
  
Chaque message comporte :  
  
\- auteur ;  
\- rôle utilisé ;  
\- date ;  
\- contenu ;  
\- pièce jointe éventuelle ;  
\- visibilité.  
  
\## 22.6 Transfert  
  
Le formateur peut transférer au responsable pédagogique.  
  
Le responsable pédagogique peut transférer à l’administration.  
  
Chaque transfert doit être :  
  
\- motivé ;  
\- daté ;  
\- audité ;  
\- visible dans l’historique.  
  
\## 22.7 Statuts  
  
\- \`OPEN\` ;  
\- \`IN\_PROGRESS\` ;  
\- \`WAITING\_FOR\_STUDENT\` ;  
\- \`TRANSFERRED\` ;  
\- \`RESOLVED\` ;  
\- \`CLOSED\` ;  
\- \`REJECTED\` ;  
\- \`REOPENED\`.  
  
\## 22.8 Réouverture  
  
Une réclamation clôturée peut être rouverte.  
  
La réouverture exige :  
  
\- un motif ;  
\- un nouveau message ;  
\- une notification ;  
\- une trace d’audit.  
  
\---  
  
\# 23. Notifications  
  
\## 23.1 Canaux  
  
\- notification dans l’application ;  
\- email ;  
\- notification push PWA ;  
\- Microsoft Teams en perspective.  
  
\## 23.2 Notifications prioritaires  
  
Les notifications prioritaires sont :  
  
1\. rappel d’un cours à venir ;  
2\. heure de début ;  
3\. formateur ;  
4\. salle ou lien distant ;  
5\. modification d’une séance ;  
6\. annulation ;  
7\. changement de formateur ;  
8\. ouverture de l’émargement ;  
9\. résultat d’une correction ;  
10\. mise à jour d’une réclamation.  
  
\## 23.3 Paramètres  
  
Un utilisateur peut configurer certains canaux.  
  
Les notifications critiques peuvent rester obligatoires.  
  
\## 23.4 Centre de notifications  
  
Fonctions :  
  
\- consulter ;  
\- marquer comme lu ;  
\- marquer toutes comme lues ;  
\- ouvrir la ressource ;  
\- filtrer ;  
\- supprimer l’affichage sans effacer l’audit métier.  
  
\---  
  
\# 24. Rapports  
  
\## 24.1 Acteurs autorisés  
  
\### Responsable pédagogique  
  
\- rapports de son périmètre ;  
\- rapports individuels ;  
\- rapports de classe ;  
\- rapports de formation.  
  
\### Administration  
  
\- rapports globaux ;  
\- rapports individuels ;  
\- rapports d’assiduité ;  
\- certificats ou attestations futurs.  
  
\### Apprenant  
  
\- consultation personnelle ;  
\- téléchargement uniquement si autorisé.  
  
\## 24.2 Unité de calcul  
  
Le calcul prioritaire repose sur les demi-journées.  
  
\`\`\`text  
Deux demi-journées validées = une journée  
Une demi-journée validée = 0,5 journée  
\`\`\`  
  
Les horaires restent affichés, mais la mesure principale ne dépend pas  
d’une connexion permanente à l’application.  
  
\## 24.3 Rapports obligatoires  
  
\### Rapport journalier d’une classe  
  
\- classe ;  
\- date ;  
\- séances ;  
\- liste des apprenants ;  
\- présence du matin ;  
\- présence de l’après-midi ;  
\- retards ;  
\- absences ;  
\- excusés ;  
\- anomalies.  
  
\### Rapport mensuel d’une classe  
  
\- période ;  
\- demi-journées attendues ;  
\- demi-journées présentes ;  
\- demi-journées absentes ;  
\- demi-journées excusées ;  
\- taux d’assiduité ;  
\- retards ;  
\- évolution.  
  
\### Rapport annuel d’une classe  
  
\- année scolaire ;  
\- statistiques mensuelles ;  
\- taux global ;  
\- répartition par matière ;  
\- répartition par modalité ;  
\- apprenants nécessitant un suivi.  
  
\### Rapport individuel  
  
\- identité ;  
\- numéro étudiant ;  
\- formation ;  
\- historique de classe ;  
\- année scolaire ;  
\- périodes attendues ;  
\- présences ;  
\- absences ;  
\- retards ;  
\- absences excusées ;  
\- taux d’assiduité ;  
\- détails par séance.  
  
\## 24.4 Exports  
  
Priorités :  
  
1\. Excel ;  
2\. CSV ;  
3\. impression ;  
4\. PDF.  
  
\## 24.5 Identité visuelle  
  
Les rapports officiels doivent prévoir :  
  
\- le logo ESIC ;  
\- le nom du rapport ;  
\- la période ;  
\- la date de génération ;  
\- l’auteur ou le système émetteur ;  
\- un identifiant de document ;  
\- une mention indiquant qu’il s’agit d’un document électronique.  
  
\## 24.6 Autorisation de téléchargement étudiant  
  
Le champ suivant doit être disponible :  
  
\`\`\`text  
student\_report\_download\_enabled = true | false  
\`\`\`  
  
Lorsque la valeur est \`false\` :  
  
\- le bouton est grisé ;  
\- une explication est affichée ;  
\- l’apprenant peut éventuellement demander l’autorisation.  
  
\---  
  
\# 25. Tableaux de bord et graphiques  
  
\## 25.1 Responsable pédagogique  
  
\- taux d’assiduité par formation ;  
\- taux par classe ;  
\- évolution mensuelle ;  
\- retards ;  
\- absences non justifiées ;  
\- comptes non activés ;  
\- invitations échouées ;  
\- réclamations ouvertes ;  
\- séances sans formateur ;  
\- changements récents.  
  
\## 25.2 Administration  
  
\- taux global ;  
\- comparaison des formations ;  
\- volume de justificatifs ;  
\- délais de traitement ;  
\- utilisateurs suspendus ;  
\- anomalies ;  
\- exports récents.  
  
\## 25.3 Formateur  
  
\- séances du jour ;  
\- participants attendus ;  
\- présents ;  
\- absents ;  
\- retardataires ;  
\- apprenants non inscrits ;  
\- demandes en attente.  
  
\## 25.4 Apprenant  
  
\- prochain cours ;  
\- prochaine action d’émargement ;  
\- taux d’assiduité ;  
\- absences ;  
\- justificatifs ;  
\- réclamations ;  
\- notifications.  
  
\## 25.5 Accessibilité des graphiques  
  
Tout graphique doit disposer :  
  
\- d’un titre ;  
\- d’une légende ;  
\- de valeurs accessibles ;  
\- d’un tableau équivalent ;  
\- d’une palette contrastée ;  
\- d’une absence de dépendance exclusive à la couleur.  
  
\---  
  
\# 26. Authentification  
  
\## 26.1 Identifiant  
  
L’adresse électronique vérifiée est utilisée comme identifiant de  
connexion.  
  
\## 26.2 Mot de passe  
  
\- longueur minimale ;  
\- hachage Argon2id ou BCrypt ;  
\- blocage des mots de passe courants ;  
\- aucune conservation en clair ;  
\- aucune journalisation ;  
\- aucun changement périodique arbitraire.  
  
\## 26.3 MFA  
  
\### Obligatoire  
  
\- \`SUPER\_ADMIN\` ;  
\- \`ADMIN\` ;  
\- opérations sensibles du \`PEDAGOGICAL\_MANAGER\`.  
  
\### Adaptatif pour les apprenants  
  
Le second facteur n’est pas demandé à chaque utilisation.  
  
Il est demandé notamment lors :  
  
\- de la première connexion ;  
\- d’un nouvel appareil ;  
\- d’un changement inhabituel ;  
\- d’une récupération de compte ;  
\- d’une réinitialisation de l’application ;  
\- d’un changement de moyen d’authentification.  
  
\## 26.4 Tentatives échouées  
  
Après trois échecs :  
  
\- ralentissement progressif ;  
\- challenge anti-bot ;  
\- notification éventuelle ;  
\- verrouillage temporaire si les tentatives continuent.  
  
Aucun verrouillage définitif automatique.  
  
\## 26.5 Session  
  
\### Inactivité  
  
Expiration après 30 minutes d’inactivité.  
  
\### Durée absolue  
  
Une durée maximale configurable doit être prévue.  
  
\### Appareil de confiance  
  
Un appareil de confiance peut bénéficier d’une reconnexion simplifiée,  
sans empêcher :  
  
\- la révocation ;  
\- la réauthentification ;  
\- le contrôle du risque.  
  
\## 26.6 Stockage des jetons  
  
La stratégie recommandée est :  
  
\- jeton d’accès court ;  
\- cookie \`HttpOnly\` ;  
\- attribut \`Secure\` ;  
\- politique \`SameSite\` appropriée ;  
\- rotation du jeton de renouvellement ;  
\- protection CSRF ;  
\- absence de stockage du jeton sensible dans \`localStorage\`.  
  
La session doit être invalidée lors :  
  
\- de la déconnexion ;  
\- de l’expiration ;  
\- d’une réinitialisation de mot de passe ;  
\- d’une révocation ;  
\- d’un incident ;  
\- d’une désactivation du compte.  
  
Les identifiants de session doivent être difficiles à prédire, protégés  
pendant tout leur cycle de vie et correctement invalidés. (\[cheatsheetseries.owasp.org\](https://cheatsheetseries.owasp.org/cheatsheets/Session\_Management\_Cheat\_Sheet.html?utm\_source=openai))  
  
\---  
  
\# 27. Mot de passe oublié  
  
\## 27.1 Parcours  
  
1\. saisie de l’adresse ;  
2\. réponse neutre ;  
3\. limitation du nombre de demandes ;  
4\. challenge anti-bot si nécessaire ;  
5\. génération d’un jeton ;  
6\. envoi ;  
7\. contrôle de l’expiration ;  
8\. définition du nouveau mot de passe ;  
9\. invalidation du jeton ;  
10\. révocation des sessions ;  
11\. notification ;  
12\. audit.  
  
\## 27.2 Sécurité  
  
Le système ne doit pas révéler si l’adresse existe.  
  
Le jeton doit être :  
  
\- aléatoire ;  
\- à usage unique ;  
\- limité dans le temps ;  
\- stocké sous forme protégée ;  
\- invalidé après utilisation.  
  
\---  
  
\# 28. Protection anti-bot  
  
\## 28.1 Solution cible  
  
Cloudflare Turnstile est retenu comme solution cible.  
  
\## 28.2 Formulaires concernés  
  
\- connexion après risque détecté ;  
\- mot de passe oublié ;  
\- activation ;  
\- récupération de compte ;  
\- formulaires publics futurs.  
  
\## 28.3 Validation  
  
La validation côté serveur est obligatoire.  
  
Un contrôle uniquement présent dans Angular ne constitue pas une  
protection suffisante.  
  
Les jetons Turnstile doivent être transmis au serveur, validés par  
l’API Siteverify, puis refusés lorsqu’ils sont expirés ou déjà utilisés.  
Les jetons Turnstile sont à usage unique et valables cinq minutes.  
(\[developers.cloudflare.com\](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/?utm\_source=openai))  
  
\---  
  
\# 29. Autorisations  
  
\## 29.1 Modèle  
  
Le système utilise :  
  
\- RBAC pour les rôles ;  
\- contrôle de périmètre pour les formations ;  
\- contrôle de propriété pour les données individuelles ;  
\- contrôle contextuel pour les séances.  
  
\## 29.2 Règles  
  
\- refus par défaut ;  
\- vérification côté serveur ;  
\- aucun droit basé uniquement sur l’affichage Angular ;  
\- vérification à chaque opération ;  
\- identifiants non prédictibles ;  
\- tests systématiques des réponses \`403\`.  
  
\## 29.3 Cumul des rôles  
  
Le cumul ne doit jamais donner un accès transversal non prévu.  
  
Exemple :  
  
Un responsable pédagogique également formateur peut :  
  
\- gérer ses formations ;  
\- enseigner ses séances ;  
\- mais ne peut pas consulter les formations d’un autre responsable.  
  
\---  
  
\# 30. Audit  
  
\## 30.1 Actions obligatoirement auditées  
  
\- connexion réussie ;  
\- connexion échouée ;  
\- déconnexion ;  
\- activation ;  
\- récupération de compte ;  
\- changement de mot de passe ;  
\- ajout ou suppression d’un facteur ;  
\- ajout ou suppression d’une passkey ;  
\- création d’un utilisateur ;  
\- changement de rôle ;  
\- changement de statut ;  
\- import d’apprenants ;  
\- import de planning ;  
\- confirmation d’import ;  
\- publication ;  
\- modification de planning ;  
\- annulation ;  
\- remplacement ;  
\- ouverture et clôture de séance ;  
\- correction d’une présence ;  
\- ajout manuel ;  
\- validation d’un justificatif ;  
\- refus d’un justificatif ;  
\- transfert d’une réclamation ;  
\- export de données ;  
\- modification des plages réseau ;  
\- gestion d’un dispositif ;  
\- suppression d’un doublon ;  
\- opération de masse ;  
\- action du super administrateur.  
  
\## 30.2 Contenu  
  
\- identifiant de l’événement ;  
\- acteur ;  
\- rôle ou contexte ;  
\- action ;  
\- catégorie ;  
\- ressource ;  
\- date et heure ;  
\- résultat ;  
\- ancienne valeur si pertinente ;  
\- nouvelle valeur si pertinente ;  
\- motif ;  
\- identifiant de corrélation.  
  
\## 30.3 Données à exclure  
  
\- mot de passe ;  
\- secret ;  
\- jeton complet ;  
\- donnée biométrique ;  
\- contenu sensible inutile ;  
\- adresse IP dans l’audit métier.  
  
\## 30.4 Conservation  
  
La durée exacte doit être validée selon :  
  
\- les finalités ;  
\- les obligations administratives ;  
\- les besoins d’audit ;  
\- la sécurité ;  
\- les droits des personnes.  
  
Une politique à plusieurs niveaux doit être prévue :  
  
\- audit actif ;  
\- archivage intermédiaire ;  
\- purge ou anonymisation.  
  
\---  
  
\# 31. Cache Redis  
  
\## 31.1 Usages autorisés  
  
\- jetons de QR ;  
\- codes temporaires ;  
\- rate limiting ;  
\- sessions ;  
\- données de planning ;  
\- paramètres de salle ;  
\- droits calculés ;  
\- compteurs ;  
\- tableaux de bord ;  
\- révocation de jetons ;  
\- événements temporaires.  
  
\## 31.2 Usages interdits  
  
Redis ne doit pas devenir la source principale pour :  
  
\- les présences définitives ;  
\- les utilisateurs ;  
\- les inscriptions ;  
\- les décisions de justificatif ;  
\- l’audit durable.  
  
\## 31.3 Clés  
  
Les clés doivent être :  
  
\- préfixées ;  
\- versionnées si nécessaire ;  
\- contextualisées ;  
\- non exposées au client.  
  
Exemples :  
  
\`\`\`text  
attendance:token:{sessionId}:{checkpoint}  
rate-limit:login:{identityHash}  
schedule:class:{classId}:{version}  
permissions:user:{userId}:{context}  
\`\`\`  
  
\## 31.4 Invalidation  
  
Le cache est invalidé après :  
  
\- modification du planning ;  
\- changement de rôle ;  
\- changement de classe ;  
\- publication ;  
\- remplacement ;  
\- annulation ;  
\- changement de paramètre.  
  
\## 31.5 Performance  
  
L’objectif de moins de 100 ms concerne prioritairement :  
  
\- lecture d’un planning en cache ;  
\- génération d’un code ;  
\- lecture d’un référentiel ;  
\- contrôle d’un jeton.  
  
Il doit être mesuré et documenté.  
  
\---  
  
\# 32. Emails asynchrones  
  
\## 32.1 Types  
  
\- activation ;  
\- mot de passe oublié ;  
\- modification du planning ;  
\- annulation ;  
\- remplacement ;  
\- réclamation ;  
\- justificatif ;  
\- alerte de sécurité.  
  
\## 32.2 Traitement  
  
L’envoi ne doit pas bloquer la requête métier.  
  
Le système crée une tâche.  
  
Un worker traite la tâche.  
  
\## 32.3 Nouvelle tentative  
  
La politique cible prévoit :  
  
\- plusieurs tentatives ;  
\- attente croissante ;  
\- statut d’erreur ;  
\- passage en DLQ ;  
\- possibilité de relance manuelle.  
  
\## 32.4 Environnement local  
  
Le prototype peut utiliser Mailpit ou un service SMTP local.  
  
La délivrabilité externe peut être simulée.  
  
\---  
  
\# 33. Intelligence artificielle  
  
\## 33.1 Priorité du projet  
  
La priorité IA est l’assistance intelligente à l’importation.  
  
\## 33.2 Entrées  
  
\- noms de feuilles ;  
\- en-têtes ;  
\- contenu des cellules ;  
\- exemples de lignes ;  
\- référentiels internes ;  
\- règles de format.  
  
\## 33.3 Sorties  
  
\- proposition de correspondance ;  
\- valeur normalisée ;  
\- score de confiance ;  
\- liste d’erreurs ;  
\- suggestion de correction ;  
\- résumé.  
  
\## 33.4 Approche MVP  
  
Le MVP peut utiliser :  
  
\- dictionnaire de synonymes ;  
\- règles ;  
\- expressions régulières ;  
\- mesures de similarité ;  
\- correspondance approximative ;  
\- service Python ;  
\- éventuellement un modèle local ou une API autorisée.  
  
\## 33.5 Détection d’anomalies  
  
En évolution :  
  
\- scan tardif inhabituel ;  
\- tentatives répétées ;  
\- utilisation simultanée ;  
\- appareil partagé ;  
\- événement IoT anormal ;  
\- séquence de présence incohérente.  
  
\## 33.6 Contrôle humain  
  
Chaque proposition doit pouvoir être :  
  
\- acceptée ;  
\- corrigée ;  
\- rejetée.  
  
\## 33.7 Traçabilité  
  
Le système doit conserver :  
  
\- version du mécanisme ;  
\- entrée ou référence de l’entrée ;  
\- proposition ;  
\- score ;  
\- décision humaine ;  
\- correction.  
  
\## 33.8 Données  
  
Le prototype utilise des données synthétiques.  
  
Aucune liste réelle d’apprenants ne doit être envoyée à un service  
public d’IA non approuvé.  
  
\---  
  
\# 34. IoT et Raspberry Pi  
  
\## 34.1 Matériel disponible  
  
\- Raspberry Pi 4 ;  
\- smartphone ;  
\- aucun lecteur NFC/RFID au lancement.  
  
\## 34.2 Prototype  
  
La Raspberry Pi peut démontrer :  
  
\- son identité ;  
\- un signal de vie ;  
\- un événement simulé ;  
\- une communication MQTT ;  
\- une confirmation serveur ;  
\- une file locale ;  
\- une reprise après coupure.  
  
\## 34.3 Cas d’usage  
  
La Pi représente une borne de salle.  
  
Le téléphone peut :  
  
\- déclencher un événement ;  
\- appeler une page locale ;  
\- simuler un badge ;  
\- servir d’interface à la borne.  
  
\## 34.4 NFC futur  
  
Un lecteur NFC USB ou compatible GPIO peut être ajouté ultérieurement.  
  
Il n’est pas obligatoire pour le prototype.  
  
\## 34.5 MQTT  
  
Topics proposés :  
  
\`\`\`text  
esic/devices/{deviceId}/heartbeat  
esic/devices/{deviceId}/attendance  
esic/devices/{deviceId}/status  
esic/devices/{deviceId}/commands  
\`\`\`  
  
\## 34.6 Message  
  
\`\`\`json  
{  
  "eventId": "uuid",  
  "deviceId": "room-a-terminal-01",  
  "eventType": "ATTENDANCE\_SCAN",  
  "sessionId": "uuid",  
  "checkpoint": "MORNING\_ARRIVAL",  
  "subjectReference": "pseudonymous-reference",  
  "occurredAt": "2026-08-27T09:01:00Z",  
  "sequence": 42  
}  
\`\`\`  
  
\## 34.7 Sécurité  
  
\- identité du dispositif ;  
\- authentification ;  
\- TLS en cible ;  
\- secret hors du code ;  
\- protection contre le rejeu ;  
\- identifiant unique d’événement ;  
\- numéro de séquence ;  
\- liste d’autorisation ;  
\- révocation ;  
\- journalisation.  
  
\---  
  
\# 35. Architecture logicielle  
  
\## 35.1 Style  
  
Le prototype utilise un monolithe modulaire Spring Boot.  
  
Ce choix réduit :  
  
\- le temps d’implémentation ;  
\- la complexité ;  
\- le nombre de déploiements ;  
\- les problèmes de communication interservices.  
  
\## 35.2 Modules back-end  
  
\`\`\`text  
auth  
identity  
user  
academic  
enrollment  
alternation  
planning  
room  
session  
attendance  
justification  
claim  
notification  
reporting  
audit  
security  
integration  
iot  
ai  
shared  
\`\`\`  
  
\## 35.3 Front-end  
  
Modules ou espaces :  
  
\`\`\`text  
auth  
student  
teacher  
pedagogical-manager  
school-administration  
admin  
super-admin  
shared  
\`\`\`  
  
\## 35.4 Service IA  
  
Python et FastAPI peuvent être séparés afin de :  
  
\- isoler les traitements ;  
\- utiliser les bibliothèques Python ;  
\- démontrer une intégration intertechnologies.  
  
\## 35.5 Base principale  
  
MySQL constitue la source de vérité.  
  
\## 35.6 Données temporaires  
  
Redis gère :  
  
\- les jetons ;  
\- le cache ;  
\- les limites ;  
\- les sessions ;  
\- certaines files temporaires.  
  
\---  
  
\# 36. Architecture de déploiement local  
  
\`\`\`text  
Navigateur / PWA  
       |  
       v  
Angular / Nginx  
       |  
       v  
Spring Boot  
  |      |       |        |  
  v      v       v        v  
MySQL  Redis  FastAPI   Mailpit  
                  |  
                  v  
             Modèles IA  
  
Raspberry Pi 4  
       |  
      MQTT  
       |  
       v  
Mosquitto  
       |  
       v  
Spring Boot  
\`\`\`  
  
\## 36.1 Docker Compose  
  
Services prévus :  
  
\- \`frontend\` ;  
\- \`backend\` ;  
\- \`mysql\` ;  
\- \`redis\` ;  
\- \`ai-service\` ;  
\- \`mosquitto\` ;  
\- \`mailpit\`.  
  
\---  
  
\# 37. Architecture cible AWS  
  
La cible pourra comprendre :  
  
\- S3 et CloudFront pour Angular ;  
\- ECS, App Runner ou EC2 pour Spring Boot ;  
\- RDS MySQL ;  
\- ElastiCache ou Valkey ;  
\- AWS IoT Core ;  
\- SQS ;  
\- Dead Letter Queue ;  
\- SES ;  
\- CloudWatch ;  
\- Secrets Manager ;  
\- WAF ;  
\- certificat TLS ;  
\- sauvegardes ;  
\- VPC.  
  
Cette architecture est une cible, pas une fonctionnalité obligatoirement  
déployée dans le prototype.  
  
\---  
  
\# 38. Exigences non fonctionnelles  
  
\## 38.1 Performance  
  
| Référence | Exigence |  
|---|---|  
| NFR-PERF-01 | Les lectures simples en cache doivent viser moins de 100 ms localement |  
| NFR-PERF-02 | L’émargement doit être traité sans attente perceptible excessive |  
| NFR-PERF-03 | Un import de 100 apprenants doit être analysé dans un délai acceptable |  
| NFR-PERF-04 | Les emails doivent être asynchrones |  
| NFR-PERF-05 | Les rapports lourds peuvent être générés de façon asynchrone |  
  
\## 38.2 Disponibilité  
  
\- mécanismes de santé ;  
\- redémarrage reproductible ;  
\- sauvegarde ;  
\- restauration ;  
\- mode de démonstration local ;  
\- vidéo de secours.  
  
\## 38.3 Maintenabilité  
  
\- architecture modulaire ;  
\- conventions ;  
\- migrations ;  
\- tests ;  
\- documentation ;  
\- commentaires utiles ;  
\- journal des décisions.  
  
\## 38.4 Commentaires du code  
  
Les commentaires doivent expliquer :  
  
\- une règle métier complexe ;  
\- une décision de sécurité ;  
\- un algorithme non évident ;  
\- une contrainte ;  
\- une solution temporaire.  
  
Ils ne doivent pas paraphraser chaque ligne.  
  
Les fonctions et classes publiques importantes doivent être documentées  
de façon concise.  
  
\## 38.5 Accessibilité  
  
\- navigation clavier ;  
\- libellés ;  
\- contrastes ;  
\- messages explicites ;  
\- tableau alternatif aux graphiques ;  
\- solution sans caméra ;  
\- solution sans biométrie ;  
\- responsive design.  
  
\## 38.6 Compatibilité  
  
Cible :  
  
\- navigateurs modernes ;  
\- Android ;  
\- iOS via navigateur ;  
\- ordinateurs ;  
\- smartphones ;  
\- tablettes.  
  
\## 38.7 Internationalisation  
  
Le MVP est en français.  
  
L’architecture peut prévoir l’externalisation des textes.  
  
\---  
  
\# 39. Données et conservation  
  
\## 39.1 Présences  
  
Durée proposée :  
  
\`\`\`text  
5 années scolaires  
\`\`\`  
  
Cette durée doit être validée avec :  
  
\- la direction ;  
\- le DPO ou référent ;  
\- les obligations applicables ;  
\- la finalité des audits.  
  
Le RGPD ne fixe pas une durée universelle pour toutes les données :  
l’organisme doit définir et justifier la durée selon la finalité, puis  
prévoir archivage et purge. (\[cnil.fr\](https://cnil.fr/fr/passer-laction/les-durees-de-conservation-des-donnees?utm\_source=openai))  
  
\## 39.2 Justificatifs  
  
Durée initiale :  
  
\`\`\`text  
12 mois  
\`\`\`  
  
\## 39.3 Comptes archivés  
  
Les données doivent être séparées entre :  
  
\- compte actif ;  
\- compte suspendu ;  
\- archivage intermédiaire ;  
\- anonymisation ou suppression.  
  
\## 39.4 Audits  
  
La conservation peut être pluriannuelle, sous réserve d’une  
justification formalisée.  
  
\## 39.5 Purge  
  
Une tâche doit permettre :  
  
\- d’identifier les données arrivées à échéance ;  
\- de produire une prévisualisation ;  
\- de supprimer ou anonymiser ;  
\- de conserver une preuve de purge ;  
\- de ne pas détruire les données sous litige.  
  
\---  
  
\# 40. RGPD  
  
\## 40.1 Données minimales  
  
Données indispensables :  
  
\- nom ;  
\- prénom ;  
\- email ;  
\- numéro étudiant ;  
\- classe ;  
\- historique d’inscription ;  
\- présence ;  
\- statut du compte.  
  
\## 40.2 Données conditionnelles  
  
\- téléphone ;  
\- date de naissance ;  
\- entreprise ;  
\- justificatif ;  
\- informations de réclamation ;  
\- moyen d’authentification enregistré.  
  
\## 40.3 Principes  
  
\- minimisation ;  
\- finalité ;  
\- transparence ;  
\- sécurité ;  
\- contrôle d’accès ;  
\- limitation de conservation ;  
\- exactitude ;  
\- traçabilité.  
  
\## 40.4 Droits  
  
Le système doit préparer les procédures concernant :  
  
\- accès ;  
\- rectification ;  
\- limitation ;  
\- opposition lorsque applicable ;  
\- suppression lorsque applicable ;  
\- export.  
  
\## 40.5 Analyse d’impact  
  
Une analyse d’impact devra être évaluée si le système est déployé  
réellement avec :  
  
\- suivi systématique ;  
\- authentification renforcée ;  
\- analyse comportementale ;  
\- données à grande échelle ;  
\- dispositifs connectés.  
  
\---  
  
\# 41. API REST  
  
\## 41.1 Principes  
  
\- préfixe \`/api\` ;  
\- versionnement ;  
\- JSON ;  
\- validation ;  
\- codes HTTP cohérents ;  
\- pagination ;  
\- filtres ;  
\- documentation OpenAPI ;  
\- erreurs structurées ;  
\- identifiant de corrélation.  
  
\## 41.2 Routes indicatives  
  
\`\`\`text  
POST   /api/v1/auth/login  
POST   /api/v1/auth/logout  
POST   /api/v1/auth/refresh  
GET    /api/v1/auth/me  
POST   /api/v1/auth/forgot-password  
POST   /api/v1/auth/reset-password  
  
GET    /api/v1/users  
POST   /api/v1/users  
PATCH  /api/v1/users/{id}  
POST   /api/v1/users/{id}/suspend  
POST   /api/v1/users/{id}/restore  
  
GET    /api/v1/programs  
POST   /api/v1/programs  
GET    /api/v1/classes  
POST   /api/v1/classes  
  
POST   /api/v1/student-imports/simulate  
POST   /api/v1/student-imports/{id}/confirm  
GET    /api/v1/student-imports/{id}  
  
POST   /api/v1/schedule-imports/simulate  
POST   /api/v1/schedule-imports/{id}/confirm  
POST   /api/v1/schedules/{id}/publish  
GET    /api/v1/schedules/{id}/versions  
  
GET    /api/v1/sessions  
POST   /api/v1/sessions/{id}/open  
POST   /api/v1/sessions/{id}/close  
POST   /api/v1/sessions/{id}/cancel  
POST   /api/v1/sessions/{id}/substitute  
  
GET    /api/v1/sessions/{id}/attendance-token  
POST   /api/v1/attendance/validate  
POST   /api/v1/attendance/manual  
PATCH  /api/v1/attendance/{id}  
  
POST   /api/v1/justifications  
PATCH  /api/v1/justifications/{id}/decision  
  
POST   /api/v1/claims  
POST   /api/v1/claims/{id}/messages  
POST   /api/v1/claims/{id}/transfer  
POST   /api/v1/claims/{id}/reopen  
  
GET    /api/v1/reports/class  
GET    /api/v1/reports/student  
GET    /api/v1/reports/export  
  
GET    /api/v1/audit-events  
\`\`\`  
  
\---  
  
\# 42. Modèle de données conceptuel  
  
\## 42.1 Entités principales  
  
\- \`User\` ;  
\- \`Role\` ;  
\- \`UserRole\` ;  
\- \`TrustedDevice\` ;  
\- \`WebAuthnCredential\` ;  
\- \`Program\` ;  
\- \`Level\` ;  
\- \`AcademicYear\` ;  
\- \`Promotion\` ;  
\- \`ClassGroup\` ;  
\- \`StudentProfile\` ;  
\- \`TeacherProfile\` ;  
\- \`Enrollment\` ;  
\- \`PedagogicalAssignment\` ;  
\- \`WorkStudyPattern\` ;  
\- \`WorkStudyException\` ;  
\- \`Subject\` ;  
\- \`Room\` ;  
\- \`Schedule\` ;  
\- \`ScheduleVersion\` ;  
\- \`ScheduleImport\` ;  
\- \`ScheduleImportRow\` ;  
\- \`StudentImport\` ;  
\- \`StudentImportRow\` ;  
\- \`CourseSession\` ;  
\- \`SessionClass\` ;  
\- \`Substitution\` ;  
\- \`CancellationRequest\` ;  
\- \`AttendanceCheckpoint\` ;  
\- \`AttendanceRecord\` ;  
\- \`AttendanceCorrection\` ;  
\- \`Justification\` ;  
\- \`Claim\` ;  
\- \`ClaimMessage\` ;  
\- \`Notification\` ;  
\- \`EmailDelivery\` ;  
\- \`AuditEvent\` ;  
\- \`IoTDevice\` ;  
\- \`IoTEvent\` ;  
\- \`AnomalyAlert\`.  
  
\## 42.2 Principes  
  
\- UUID pour les identifiants exposés ;  
\- contraintes d’unicité ;  
\- clés étrangères ;  
\- suppression logique ;  
\- horodatage ;  
\- auteur des modifications ;  
\- verrouillage optimiste si nécessaire.  
  
\---  
  
\# 43. Règles de gestion consolidées  
  
\## Identité  
  
\- \*\*RG-001\*\* : une adresse email correspond à un seul utilisateur.  
\- \*\*RG-002\*\* : un utilisateur peut posséder plusieurs rôles.  
\- \*\*RG-003\*\* : le compte super administrateur est distinct du compte quotidien.  
\- \*\*RG-004\*\* : un compte archivé ne peut pas se connecter.  
\- \*\*RG-005\*\* : une invitation expire après un mois.  
\- \*\*RG-006\*\* : l’historique n’est pas supprimé lors d’un changement de classe.  
  
\## Pédagogie  
  
\- \*\*RG-010\*\* : une formation possède un responsable pédagogique principal unique.  
\- \*\*RG-011\*\* : un responsable peut gérer plusieurs formations.  
\- \*\*RG-012\*\* : un apprenant appartient à une seule classe principale active.  
\- \*\*RG-013\*\* : une séance peut concerner plusieurs classes.  
\- \*\*RG-014\*\* : une séance possède un formateur principal.  
\- \*\*RG-015\*\* : une séance peut posséder un remplaçant autorisé.  
\- \*\*RG-016\*\* : une séance normale provient d’un planning publié.  
\- \*\*RG-017\*\* : une séance exceptionnelle exige un motif.  
  
\## Import  
  
\- \*\*RG-020\*\* : un import est simulé avant application.  
\- \*\*RG-021\*\* : une erreur bloquante empêche la confirmation.  
\- \*\*RG-022\*\* : un utilisateur existant est mis à jour, pas dupliqué.  
\- \*\*RG-023\*\* : un changement de classe conserve l’historique.  
\- \*\*RG-024\*\* : une opération groupée exige une confirmation.  
\- \*\*RG-025\*\* : les suggestions IA restent soumises à confirmation.  
  
\## Planning  
  
\- \*\*RG-030\*\* : le responsable pédagogique publie son planning.  
\- \*\*RG-031\*\* : le formateur ne publie pas le planning.  
\- \*\*RG-032\*\* : trois versions sont conservées.  
\- \*\*RG-033\*\* : une modification publiée génère une notification.  
\- \*\*RG-034\*\* : un conflit bloquant interdit la publication.  
\- \*\*RG-035\*\* : une salle peut être affectée après l’import.  
  
\## Émargement  
  
\- \*\*RG-040\*\* : le QR fixe est lié à une salle.  
\- \*\*RG-041\*\* : le QR fixe est utilisable jusqu’au début de la séance.  
\- \*\*RG-042\*\* : le QR fixe exige une connexion au réseau ESIC.  
\- \*\*RG-043\*\* : le QR dynamique change périodiquement.  
\- \*\*RG-044\*\* : un jeton est limité dans le temps.  
\- \*\*RG-045\*\* : une validation est unique par point de contrôle.  
\- \*\*RG-046\*\* : quatre contrôles peuvent être réalisés par journée.  
\- \*\*RG-047\*\* : deux contrôles cohérents valident une demi-journée.  
\- \*\*RG-048\*\* : quatre contrôles cohérents valident une journée.  
\- \*\*RG-049\*\* : une validation incomplète produit \`PARTIAL\` ou \`TO\_CONFIRM\`.  
\- \*\*RG-050\*\* : une correction manuelle exige un motif.  
\- \*\*RG-051\*\* : une présence exceptionnelle est auditée.  
\- \*\*RG-052\*\* : un apprenant non inscrit est enregistré provisoirement.  
  
\## Retards  
  
\- \*\*RG-060\*\* : jusqu’à 15 minutes, l’apprenant reste présent.  
\- \*\*RG-061\*\* : de 16 à 30 minutes, il est en retard.  
\- \*\*RG-062\*\* : après 30 minutes, une validation manuelle est requise.  
\- \*\*RG-063\*\* : un cas exceptionnel peut être accepté par le formateur.  
  
\## Justificatifs  
  
\- \*\*RG-070\*\* : un justificatif peut porter sur une séance ou une période.  
\- \*\*RG-071\*\* : la taille maximale est de 5 Mo.  
\- \*\*RG-072\*\* : les formats acceptés sont JPEG, PNG et PDF.  
\- \*\*RG-073\*\* : un refus exige un motif.  
\- \*\*RG-074\*\* : le délai initial est d’un mois.  
\- \*\*RG-075\*\* : un justificatif accepté produit \`EXCUSED\`.  
\- \*\*RG-076\*\* : un justificatif n’efface pas l’historique de l’absence.  
  
\## Sécurité  
  
\- \*\*RG-080\*\* : aucune donnée personnelle n’est placée dans le QR.  
\- \*\*RG-081\*\* : aucune donnée biométrique brute n’est conservée.  
\- \*\*RG-082\*\* : le MFA est obligatoire pour les comptes privilégiés.  
\- \*\*RG-083\*\* : le MFA apprenant est adaptatif.  
\- \*\*RG-084\*\* : après trois échecs, les contrôles sont renforcés.  
\- \*\*RG-085\*\* : le jeton sensible n’est pas stocké dans \`localStorage\`.  
\- \*\*RG-086\*\* : l’adresse IP n’est pas conservée dans l’audit métier.  
\- \*\*RG-087\*\* : le cache ne contourne jamais les autorisations.  
\- \*\*RG-088\*\* : une action critique exige une réauthentification.  
  
\---  
  
\# 44. Exigences fonctionnelles détaillées  
  
\## Priorités  
  
\- \`MUST\` : obligatoire pour le parcours principal ;  
\- \`SHOULD\` : important ;  
\- \`COULD\` : souhaitable ;  
\- \`FUTURE\` : évolution.  
  
| ID | Exigence | Priorité |  
|---|---|---|  
| EF-AUTH-001 | Se connecter avec email et mot de passe | MUST |  
| EF-AUTH-002 | Gérer plusieurs rôles | MUST |  
| EF-AUTH-003 | Choisir un contexte de rôle | MUST |  
| EF-AUTH-004 | Activer un compte par invitation | SHOULD |  
| EF-AUTH-005 | Réinitialiser un mot de passe | SHOULD |  
| EF-AUTH-006 | Enregistrer une passkey | SHOULD |  
| EF-AUTH-007 | Utiliser une authentification adaptative | SHOULD |  
| EF-AUTH-008 | Activer le MFA privilégié | SHOULD |  
| EF-USER-001 | Créer un utilisateur | MUST |  
| EF-USER-002 | Suspendre un utilisateur | MUST |  
| EF-USER-003 | Archiver un utilisateur | SHOULD |  
| EF-USER-004 | Réaliser une opération de masse | SHOULD |  
| EF-USER-005 | Détecter les doublons | MUST |  
| EF-ACA-001 | Gérer les formations | MUST |  
| EF-ACA-002 | Gérer les niveaux | MUST |  
| EF-ACA-003 | Gérer les promotions | MUST |  
| EF-ACA-004 | Gérer les classes | MUST |  
| EF-ACA-005 | Gérer les années scolaires | MUST |  
| EF-ACA-006 | Gérer trois rythmes d’alternance | MUST |  
| EF-IMP-001 | Simuler un import apprenant CSV | MUST |  
| EF-IMP-002 | Confirmer un import apprenant | MUST |  
| EF-IMP-003 | Importer un classeur Excel | SHOULD |  
| EF-IMP-004 | Gérer plusieurs feuilles | SHOULD |  
| EF-IMP-005 | Proposer un mapping intelligent | SHOULD |  
| EF-PLAN-001 | Importer un planning CSV | MUST |  
| EF-PLAN-002 | Prévisualiser le planning | MUST |  
| EF-PLAN-003 | Corriger les lignes | SHOULD |  
| EF-PLAN-004 | Publier le planning | MUST |  
| EF-PLAN-005 | Versionner le planning | SHOULD |  
| EF-PLAN-006 | Créer un planning dans l’interface | SHOULD |  
| EF-PLAN-007 | Conserver trois versions | SHOULD |  
| EF-ROOM-001 | Gérer les salles | MUST |  
| EF-ROOM-002 | Gérer un QR fixe par salle | SHOULD |  
| EF-SES-001 | Créer des séances depuis le planning | MUST |  
| EF-SES-002 | Ouvrir une séance | MUST |  
| EF-SES-003 | Clôturer une séance | MUST |  
| EF-SES-004 | Annuler une séance | SHOULD |  
| EF-SES-005 | Affecter un remplaçant | SHOULD |  
| EF-ATT-001 | Générer un QR dynamique | MUST |  
| EF-ATT-002 | Valider une présence | MUST |  
| EF-ATT-003 | Gérer quatre points de contrôle | MUST |  
| EF-ATT-004 | Calculer les demi-journées | MUST |  
| EF-ATT-005 | Gérer les retards | MUST |  
| EF-ATT-006 | Saisir manuellement une présence | MUST |  
| EF-ATT-007 | Ajouter un apprenant provisoire | SHOULD |  
| EF-ATT-008 | Contrôler le réseau local | SHOULD |  
| EF-JUS-001 | Déposer un justificatif | SHOULD |  
| EF-JUS-002 | Valider ou refuser | SHOULD |  
| EF-CLAIM-001 | Créer une réclamation | SHOULD |  
| EF-CLAIM-002 | Échanger sous forme conversationnelle | SHOULD |  
| EF-NOTIF-001 | Afficher des notifications internes | SHOULD |  
| EF-NOTIF-002 | Notifier les modifications | SHOULD |  
| EF-REP-001 | Produire un rapport de classe | MUST |  
| EF-REP-002 | Produire un rapport individuel | MUST |  
| EF-REP-003 | Exporter en CSV | MUST |  
| EF-REP-004 | Exporter en Excel | SHOULD |  
| EF-AUD-001 | Auditer les opérations critiques | MUST |  
| EF-IOT-001 | Recevoir un événement MQTT | SHOULD |  
| EF-IOT-002 | Gérer l’identité d’une borne | SHOULD |  
| EF-AI-001 | Suggérer un mapping de colonnes | SHOULD |  
| EF-AI-002 | Produire un score de confiance | SHOULD |  
| EF-AI-003 | Détecter une anomalie | COULD |  
  
\---  
  
\# 45. Critères d’acceptation principaux  
  
\## AC-001 — Authentification  
  
Un utilisateur actif doit pouvoir se connecter avec des identifiants  
valides.  
  
Un utilisateur suspendu doit recevoir un refus sans divulgation  
d’information sensible.  
  
\## AC-002 — Périmètre pédagogique  
  
Un responsable pédagogique ne doit pas pouvoir lire les classes d’une  
formation hors de son périmètre.  
  
L’API doit renvoyer \`403\`.  
  
\## AC-003 — Cumul des rôles  
  
Un responsable également formateur doit pouvoir accéder aux deux  
contextes sans perdre les restrictions de périmètre.  
  
\## AC-004 — Import des apprenants  
  
Un fichier de 100 apprenants valides doit produire une simulation  
contenant :  
  
\- nombre de créations ;  
\- nombre de mises à jour ;  
\- nombre de déplacements ;  
\- nombre d’erreurs ;  
\- nombre d’avertissements.  
  
\## AC-005 — Anti-doublon  
  
Un apprenant existant ne doit pas être recréé.  
  
\## AC-006 — Historique  
  
Après un changement de classe, l’ancienne inscription doit rester  
consultable.  
  
\## AC-007 — Import planning  
  
Un planning valide doit produire des séances uniquement après  
confirmation et publication.  
  
\## AC-008 — Versionnement  
  
Une modification d’un planning publié doit créer une nouvelle version.  
  
\## AC-009 — QR fixe  
  
Le QR fixe d’une salle doit être refusé :  
  
\- après le début ;  
\- hors réseau ESIC ;  
\- sans séance correspondante.  
  
\## AC-010 — QR dynamique  
  
Le QR dynamique doit changer périodiquement et être refusé après  
expiration.  
  
\## AC-011 — Retard  
  
Une validation réalisée 20 minutes après le début doit produire \`LATE\`.  
  
\## AC-012 — Demi-journée  
  
Les deux contrôles du matin doivent produire une demi-journée présente.  
  
\## AC-013 — Journée  
  
Les quatre contrôles doivent produire une journée présente.  
  
\## AC-014 — Justificatif  
  
Un justificatif accepté doit transformer \`ABSENT\` en \`EXCUSED\`.  
  
\## AC-015 — Audit  
  
Une correction doit afficher :  
  
\- l’ancienne valeur ;  
\- la nouvelle ;  
\- l’auteur ;  
\- la date ;  
\- le motif.  
  
\## AC-016 — Rapport  
  
Le rapport individuel doit afficher le calcul des demi-journées.  
  
\## AC-017 — Sécurité  
  
Un étudiant ne doit jamais consulter le rapport d’un autre étudiant.  
  
\## AC-018 — WebAuthn  
  
Le serveur ne doit recevoir aucune donnée biométrique brute.  
  
\## AC-019 — Raspberry Pi  
  
Un événement MQTT possédant un identifiant déjà traité doit être rejeté  
ou ignoré comme doublon.  
  
\## AC-020 — IA  
  
Une suggestion à faible confiance ne doit pas être appliquée sans  
confirmation.  
  
\---  
  
\# 46. Tests  
  
\## 46.1 Tests unitaires  
  
\- calcul des retards ;  
\- calcul des demi-journées ;  
\- calcul journalier ;  
\- contrôle d’inscription ;  
\- contrôle de périmètre ;  
\- détection des doublons ;  
\- validation de fichier ;  
\- expiration du jeton ;  
\- règles d’alternance ;  
\- conversion \`ABSENT\` vers \`EXCUSED\`.  
  
\## 46.2 Tests d’intégration  
  
\- authentification ;  
\- migrations ;  
\- MySQL ;  
\- Redis ;  
\- import ;  
\- publication ;  
\- émargement ;  
\- audit ;  
\- rapport ;  
\- MQTT ;  
\- service Python.  
  
\## 46.3 Tests de sécurité  
  
\- accès sans authentification ;  
\- accès hors rôle ;  
\- accès hors périmètre ;  
\- IDOR ;  
\- injection ;  
\- XSS ;  
\- CSRF ;  
\- CORS ;  
\- rejeu ;  
\- brute force ;  
\- fichier malveillant ;  
\- exposition de secrets ;  
\- expiration ;  
\- élévation de privilège.  
  
\## 46.4 Tests de performance  
  
\- lecture du planning sans cache ;  
\- lecture avec cache ;  
\- génération du QR ;  
\- validation de présence ;  
\- import de 100 apprenants ;  
\- rapport mensuel.  
  
\## 46.5 Tests d’accessibilité  
  
\- navigation clavier ;  
\- lecteur d’écran ;  
\- contrastes ;  
\- erreurs ;  
\- alternative au QR ;  
\- alternative à WebAuthn.  
  
\---  
  
\# 47. Recette  
  
\## 47.1 Acteurs  
  
\- porteur du projet ;  
\- responsable pédagogique fictif ;  
\- formateur fictif ;  
\- apprenant fictif ;  
\- administration fictive.  
  
\## 47.2 Scénario de recette principal  
  
1\. l’administrateur crée une formation ;  
2\. le responsable crée une classe ;  
3\. il importe 10 apprenants ;  
4\. il confirme l’import ;  
5\. les comptes sont créés ;  
6\. il importe un planning ;  
7\. il publie ;  
8\. le formateur consulte sa séance ;  
9\. il ouvre la séance ;  
10\. le QR est généré ;  
11\. l’apprenant émarge ;  
12\. le formateur voit la présence ;  
13\. une correction est réalisée ;  
14\. l’audit est affiché ;  
15\. le rapport est exporté.  
  
\## 47.3 Critères de validation  
  
\- aucun blocage ;  
\- données cohérentes ;  
\- autorisations respectées ;  
\- erreurs lisibles ;  
\- preuves disponibles ;  
\- documentation mise à jour.  
  
\---  
  
\# 48. Interface utilisateur  
  
\## 48.1 Principes  
  
\- Angular Material ;  
\- responsive ;  
\- interface cohérente ;  
\- actions primaires visibles ;  
\- confirmations pour les actions risquées ;  
\- états de chargement ;  
\- erreurs contextualisées ;  
\- formulaires validés ;  
\- aide concise.  
  
\## 48.2 Écrans communs  
  
\- connexion ;  
\- activation ;  
\- récupération ;  
\- profil ;  
\- choix du rôle ;  
\- notifications ;  
\- paramètres de sécurité.  
  
\## 48.3 Responsable pédagogique  
  
\- tableau de bord ;  
\- formations ;  
\- classes ;  
\- apprenants ;  
\- import des apprenants ;  
\- planning ;  
\- import du planning ;  
\- calendrier ;  
\- séances ;  
\- remplacements ;  
\- justificatifs ;  
\- réclamations ;  
\- rapports.  
  
\## 48.4 Formateur  
  
\- séances du jour ;  
\- calendrier ;  
\- ouverture ;  
\- QR ;  
\- présences en direct ;  
\- ajout manuel ;  
\- clôture ;  
\- demandes.  
  
\## 48.5 Apprenant  
  
\- prochain cours ;  
\- planning ;  
\- écran d’émargement ;  
\- historique ;  
\- justificatifs ;  
\- réclamations ;  
\- rapport.  
  
\## 48.6 Administration  
  
\- recherche globale ;  
\- rapports ;  
\- justificatifs ;  
\- comptes ;  
\- invitations ;  
\- anomalies.  
  
\---  
  
\# 49. Messages d’erreur  
  
Les messages doivent :  
  
\- être compréhensibles ;  
\- indiquer l’action possible ;  
\- éviter les informations sensibles ;  
\- inclure un identifiant de corrélation si utile.  
  
Exemples :  
  
\`\`\`text  
Le fichier ne contient pas la colonne obligatoire "email".  
Corrigez le fichier, puis relancez la simulation.  
  
Cette séance n’est pas encore ouverte.  
  
Le code d’émargement a expiré. Demandez au formateur d’afficher  
un nouveau code.  
  
Votre présence a déjà été enregistrée pour ce point de contrôle.  
  
Vous n’êtes pas autorisé à consulter cette formation.  
\`\`\`  
  
\---  
  
\# 50. Sauvegarde et restauration  
  
\## 50.1 Sauvegarde  
  
Le prototype doit prévoir :  
  
\- export MySQL ;  
\- sauvegarde des fichiers ;  
\- script documenté ;  
\- date de sauvegarde ;  
\- emplacement protégé.  
  
\## 50.2 Restauration  
  
Une procédure doit décrire :  
  
\- arrêt contrôlé ;  
\- restauration ;  
\- migrations ;  
\- vérification ;  
\- test de connexion ;  
\- contrôle d’intégrité.  
  
\## 50.3 Preuve  
  
Au moins un test de restauration doit être documenté.  
  
\---  
  
\# 51. Supervision  
  
\## 51.1 Santé  
  
\- santé de Spring Boot ;  
\- connexion MySQL ;  
\- connexion Redis ;  
\- service IA ;  
\- broker MQTT ;  
\- service email.  
  
\## 51.2 Indicateurs  
  
\- temps de réponse ;  
\- erreurs ;  
\- connexions échouées ;  
\- taux de cache ;  
\- volume d’émargements ;  
\- files d’emails ;  
\- événements IoT ;  
\- alertes.  
  
\## 51.3 Journaux  
  
\- structurés ;  
\- niveaux adaptés ;  
\- identifiant de corrélation ;  
\- aucun secret ;  
\- rotation ;  
\- durée définie.  
  
\---  
  
\# 52. Documentation technique  
  
Les documents attendus sont :  
  
\`\`\`text  
docs/01-cadrage.md  
docs/02-cahier-des-charges.md  
docs/03-architecture.md  
docs/04-modele-donnees.md  
docs/05-backlog.md  
docs/06-risques.md  
docs/07-securite-rgpd.md  
docs/08-tests-recette.md  
docs/09-matrice-rncp.md  
docs/10-journal-ia.md  
docs/11-guide-demonstration.md  
docs/CURRENT-STATE.md  
\`\`\`  
  
\## 52.1 Architecture Decision Records  
  
Les décisions importantes doivent être documentées :  
  
\- choix du monolithe ;  
\- choix de MySQL ;  
\- choix de Redis ;  
\- choix de PWA ;  
\- choix de WebAuthn ;  
\- stratégie de session ;  
\- QR fixe et dynamique ;  
\- conservation ;  
\- stratégie d’import ;  
\- usage de l’IA.  
  
\---  
  
\# 53. Utilisation de Claude Code  
  
\## 53.1 Source de vérité  
  
Claude doit lire :  
  
1\. \`CLAUDE.md\` ;  
2\. le cahier des charges ;  
3\. l’architecture ;  
4\. le modèle de données ;  
5\. le backlog ;  
6\. l’état courant.  
  
\## 53.2 Règle  
  
Claude ne doit jamais :  
  
\- inventer un test ;  
\- inventer une fonctionnalité ;  
\- déclarer une fonction terminée sans preuve ;  
\- changer une règle métier sans documenter ;  
\- placer un secret dans Git ;  
\- utiliser des données réelles ;  
\- réécrire inutilement tous les documents.  
  
\## 53.3 Statuts  
  
Chaque exigence doit être marquée :  
  
\- \`TODO\` ;  
\- \`IN\_PROGRESS\` ;  
\- \`IMPLEMENTED\` ;  
\- \`TESTED\` ;  
\- \`DEMONSTRATED\` ;  
\- \`SIMULATED\` ;  
\- \`DEFERRED\`.  
  
\## 53.4 Traçabilité  
  
\`\`\`text  
Exigence  
→ User story  
→ Code  
→ Test  
→ Capture ou preuve  
→ Bloc RNCP  
\`\`\`  
  
\---  
  
\# 54. Livrables  
  
\## 54.1 Fonctionnels  
  
\- prototype ;  
\- interface web ;  
\- API ;  
\- base ;  
\- imports ;  
\- émargement ;  
\- rapports ;  
\- audit.  
  
\## 54.2 Techniques  
  
\- code ;  
\- Docker Compose ;  
\- migrations ;  
\- tests ;  
\- OpenAPI ;  
\- scripts ;  
\- configuration d’exemple ;  
\- simulateur MQTT.  
  
\## 54.3 Soutenance  
  
\- rapport ;  
\- présentation ;  
\- captures ;  
\- vidéo ;  
\- scénario ;  
\- matrice RNCP ;  
\- journal IA.  
  
\---  
  
\# 55. Traçabilité RNCP 39394  
  
\## 55.1 Bloc 1  
  
Preuves :  
  
\- cadrage ;  
\- cahier des charges ;  
\- analyse de l’existant ;  
\- périmètre ;  
\- risques ;  
\- priorités ;  
\- gouvernance ;  
\- indicateurs ;  
\- conduite du changement ;  
\- feuille de route.  
  
\## 55.2 Bloc 2  
  
Preuves :  
  
\- Angular ;  
\- Spring Boot ;  
\- API ;  
\- MySQL ;  
\- Redis ;  
\- imports ;  
\- tableaux de bord ;  
\- PWA ;  
\- WebAuthn ;  
\- Python ;  
\- tests.  
  
\## 55.3 Bloc 3  
  
Preuves :  
  
\- Docker ;  
\- authentification ;  
\- MFA ;  
\- autorisations ;  
\- audit ;  
\- anti-bot ;  
\- cache ;  
\- sauvegarde ;  
\- supervision ;  
\- réponse aux incidents ;  
\- détection d’anomalies.  
  
\## 55.4 Bloc 4  
  
Preuves :  
  
\- Raspberry Pi 4 ;  
\- MQTT ;  
\- identité du dispositif ;  
\- protection contre le rejeu ;  
\- télémétrie ;  
\- mode dégradé ;  
\- analyse des événements.  
  
\---  
  
\# 56. Matrice de priorisation finale  
  
\## MUST  
  
\- authentification ;  
\- rôles ;  
\- formations ;  
\- classes ;  
\- inscriptions historiques ;  
\- rythmes d’alternance ;  
\- import CSV des apprenants ;  
\- simulation ;  
\- import CSV du planning ;  
\- publication ;  
\- séances ;  
\- QR dynamique ;  
\- quatre points de contrôle ;  
\- calcul des demi-journées ;  
\- présence manuelle ;  
\- rapports ;  
\- export CSV ;  
\- audit ;  
\- Redis ;  
\- Docker Compose ;  
\- tests critiques.  
  
\## SHOULD  
  
\- Excel ;  
\- multifeuille ;  
\- invitations ;  
\- mot de passe oublié ;  
\- WebAuthn ;  
\- PWA ;  
\- QR fixe ;  
\- réseau ESIC ;  
\- remplacement ;  
\- annulation ;  
\- justificatifs ;  
\- réclamations ;  
\- Excel export ;  
\- assistant intelligent ;  
\- Raspberry Pi.  
  
\## COULD  
  
\- MFA TOTP complet ;  
\- Turnstile ;  
\- notifications push ;  
\- Isolation Forest ;  
\- DLQ ;  
\- rapport PDF ;  
\- graphique avancé ;  
\- lecteur NFC.  
  
\## FUTURE  
  
\- Microsoft Graph ;  
\- Teams ;  
\- Outlook ;  
\- AWS complet ;  
\- passkeys généralisées ;  
\- borne NFC industrielle ;  
\- génération automatique d’attestation ;  
\- moteur d’accompagnement pédagogique.  
  
\---  
  
\# 57. Risques majeurs  
  
| Risque | Impact | Réponse |  
|---|---:|---|  
| Périmètre irréalisable en trois jours | Critique | Respecter les priorités MUST |  
| Authentification trop complexe | Élevé | Commencer par le flux simple, puis WebAuthn |  
| Import hétérogène | Élevé | Modèle CSV et simulation |  
| Règles d’émargement complexes | Élevé | Tests unitaires avant l’interface |  
| Quatre contrôles difficiles à démontrer | Moyen | Accélérer l’horloge en mode démo |  
| Raspberry Pi indisponible | Moyen | Simulateur MQTT |  
| Données non conformes | Élevé | Données fictives |  
| Perte de temps sur AWS | Élevé | Tout faire en local |  
| Rapport incohérent avec le code | Critique | Mise à jour après chaque fonctionnalité |  
| Code généré mal compris | Critique | Relecture et démonstration manuelle |  
  
\---  
  
\# 58. Fonctionnalités différenciantes  
  
\## 58.1 Import intelligent contrôlé  
  
Le système ne se limite pas à importer un modèle fixe. Il prépare une  
reconnaissance semi-automatique avec confirmation humaine.  
  
\## 58.2 Double QR  
  
Le système combine :  
  
\- QR fixe de salle avant la séance ;  
\- QR dynamique du formateur pendant la séance.  
  
\## 58.3 Présence par continuité  
  
Les quatre points de contrôle permettent de détecter :  
  
\- une arrivée ;  
\- un départ pendant la matinée ;  
\- une absence après midi ;  
\- un départ pendant l’après-midi.  
  
\## 58.4 Gestion réelle de l’alternance  
  
Le système ne considère pas une journée en entreprise comme une absence.  
  
\## 58.5 Journal de transparence  
  
L’apprenant peut comprendre :  
  
\- quand sa présence a été enregistrée ;  
\- comment ;  
\- qui l’a modifiée ;  
\- pourquoi.  
  
\## 58.6 Authentification adaptative  
  
L’expérience reste simple sur un appareil reconnu tout en renforçant le  
contrôle lors d’un changement à risque.  
  
\## 58.7 Parcours de régularisation  
  
Un nouvel apprenant peut être enregistré provisoirement sans bloquer son  
premier cours.  
  
\## 58.8 Simulateur de calendrier  
  
Une évolution pourra permettre au responsable de tester l’effet :  
  
\- d’un rythme d’alternance ;  
\- d’un changement de semaine ;  
\- d’un déplacement de cours ;  
\- d’une indisponibilité de formateur.  
  
\## 58.9 Détection prédictive des conflits  
  
L’assistant pourra prévenir :  
  
\- un conflit de salle ;  
\- un conflit de formateur ;  
\- une classe surchargée ;  
\- une séance sans enseignant ;  
\- un calendrier incompatible avec le rythme.  
  
\---  
  
\# 59. Définition de terminé  
  
Une fonctionnalité est terminée lorsque :  
  
\- l’exigence est identifiée ;  
\- le code compile ;  
\- les tests passent ;  
\- les erreurs sont gérées ;  
\- l’autorisation est testée ;  
\- l’API est documentée ;  
\- la documentation est à jour ;  
\- une preuve existe ;  
\- le statut est mis à jour ;  
\- la fonctionnalité peut être expliquée.  
  
\---  
  
\# 60. Validation du cahier des charges  
  
Le présent cahier des charges doit être considéré comme :  
  
\- la référence fonctionnelle initiale ;  
\- un document évolutif versionné ;  
\- une base pour le backlog ;  
\- une base pour l’architecture ;  
\- une base pour les tests ;  
\- une base pour le rapport.  
  
Toute modification majeure doit préciser :  
  
\- l’ancienne règle ;  
\- la nouvelle règle ;  
\- la raison ;  
\- l’impact ;  
\- la priorité ;  
\- la date ;  
\- l’auteur.  
  
\---  
  
\# 61. Approbation  
  
| Rôle | Nom | Décision | Date |  
|---|---|---|---|  
| Porteur du projet | Abubacar AFOLABI | À valider | |  
| Responsable pédagogique consulté | À compléter | À valider | |  
| Référent technique | À compléter | À valider | |  
| Référent sécurité/RGPD | À compléter | À valider | |  
  
\---  
  
\# 62. Conclusion  
  
ESIC Connect doit être présenté comme une transformation complète du  
processus pédagogique et administratif de suivi de l’assiduité.  
  
Le prototype ne vise pas à reproduire immédiatement l’intégralité d’une  
solution industrielle. Il doit démontrer de manière cohérente :  
  
\- le pilotage d’un système d’information ;  
\- la formalisation des besoins ;  
\- le développement d’une application web ;  
\- la sécurisation des identités ;  
\- la gestion des données ;  
\- l’utilisation pertinente de Redis ;  
\- l’intégration de WebAuthn ;  
\- la gestion intelligente des plannings ;  
\- la production de rapports ;  
\- l’exploitation d’une Raspberry Pi ;  
\- l’utilisation contrôlée de l’intelligence artificielle ;  
\- la traçabilité avec les quatre blocs du titre RNCP 39394.  
  
Le parcours prioritaire reste :  
  
\`\`\`text  
Importation des apprenants  
→ Importation du planning  
→ Publication  
→ Création des séances  
→ Consultation par le formateur  
→ Ouverture de l’émargement  
→ Validation des présences  
→ Calcul de l’assiduité  
→ Production du rapport  
\`\`\`  
\`\`\`  
