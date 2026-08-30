# Stratégie de tests et cahier de recette — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Version | 1.0 |
| Date | 28 août 2026 |
| Statut | À exécuter progressivement |
| Responsable | Abubacar AFOLABI |

---

# 1. Objectifs

La stratégie doit vérifier :

- la conformité fonctionnelle ;
- l’intégrité des données ;
- les autorisations ;
- la sécurité ;
- les performances ;
- l’accessibilité ;
- la résilience ;
- l’IoT ;
- l’IA ;
- la démonstrabilité.

---

# 2. Niveaux de tests

## Tests unitaires

Ciblent :

- une règle ;
- une fonction ;
- un service isolé ;
- un calcul.

## Tests d’intégration

Ciblent :

- Spring Boot et MySQL ;
- Spring Boot et Redis ;
- migrations ;
- sécurité ;
- repository ;
- FastAPI ;
- MQTT.

## Tests d’API

Ciblent :

- routes ;
- validation ;
- statuts HTTP ;
- autorisations ;
- erreurs.

## Tests end-to-end

Ciblent les parcours complets Angular → Spring Boot → données.

## Tests de sécurité

Ciblent :

- authentification ;
- autorisations ;
- injections ;
- fichiers ;
- sessions ;
- QR ;
- IoT.

## Tests de performance

Ciblent :

- latence ;
- cache ;
- import ;
- rapports ;
- concurrence.

## Recette utilisateur

Vérifie que la solution répond aux besoins du Product Owner.

---

# 3. Environnements de tests

| Environnement | Utilisation |
|---|---|
| Local | Tests manuels |
| Test | Tests automatisés |
| Staging | Recette et démonstration |
| Production | Tests limités non destructifs |

Aucune donnée réelle ne doit être utilisée en test ou staging.

---

# 4. Données de test

Créer au minimum :

- 1 super administrateur ;
- 1 administrateur ;
- 1 administration scolaire ;
- 2 responsables pédagogiques ;
- 3 formateurs ;
- 3 formations ;
- 4 classes ;
- 30 apprenants ;
- 3 rythmes d’alternance ;
- 10 matières ;
- 20 séances ;
- présences normales et anormales ;
- justificatifs fictifs ;
- Raspberry Pi fictive.

---

# 5. Tests unitaires prioritaires

| ID | Cas | Résultat attendu |
|---|---|---|
| TU-001 | Retard de 10 min | `PRESENT` |
| TU-002 | Retard de 20 min | `LATE` |
| TU-003 | Retard de 40 min | Validation manuelle |
| TU-004 | Deux contrôles du matin | Demi-journée validée |
| TU-005 | Quatre contrôles | Journée validée |
| TU-006 | Un seul contrôle | `PARTIAL` ou `TO_CONFIRM` |
| TU-007 | Aucun contrôle | `ABSENT` |
| TU-008 | Justificatif accepté | `EXCUSED` |
| TU-009 | Journée entreprise | Pas d’absence |
| TU-010 | Exception école | Présence attendue |
| TU-011 | Jeton expiré | Refus |
| TU-012 | Double validation | Résultat idempotent |
| TU-013 | Utilisateur hors classe | Refus |
| TU-014 | Distanciel non autorisé | Refus |
| TU-015 | Changement de classe | Historique conservé |

---

# 6. Tests du modèle de données

| ID | Action | Résultat |
|---|---|---|
| TD-001 | Supprimer un apprenant avec présence | Refus FK |
| TD-002 | Archiver l’apprenant | Succès |
| TD-003 | Supprimer une classe utilisée | Refus FK |
| TD-004 | Clôturer une inscription | Succès |
| TD-005 | Deux présences au même checkpoint | Contrainte unique |
| TD-006 | Deux événements IoT identiques | Refus doublon |
| TD-007 | Deux versions identiques | Refus |
| TD-008 | Changement concurrent de classe | Une seule inscription active |
| TD-009 | Correction d’une présence | Historique ajouté |
| TD-010 | Suppression du fichier expiré | Métadonnées conservées |

---

# 7. Tests d’authentification

| ID | Scénario | Résultat |
|---|---|---|
| TA-001 | Identifiants valides | Connexion |
| TA-002 | Mot de passe invalide | `401` neutre |
| TA-003 | Compte suspendu | Refus |
| TA-004 | Trois échecs | Contrôle renforcé |
| TA-005 | Déconnexion | Session révoquée |
| TA-006 | Mot de passe changé | Ancienne session révoquée |
| TA-007 | Nouvel appareil | MFA adaptatif |
| TA-008 | Passkey valide | Connexion |
| TA-009 | Credential révoqué | Refus |
| TA-010 | Cookie absent | `401` |

---

# 8. Tests d’autorisation

| ID | Scénario | Résultat |
|---|---|---|
| TZ-001 | Étudiant lit un autre étudiant | `403` |
| TZ-002 | Responsable lit une autre formation | `403` |
| TZ-003 | Formateur ouvre une séance non affectée | `403` |
| TZ-004 | Remplaçant ouvre sa séance | Autorisé |
| TZ-005 | Formateur publie le planning | `403` |
| TZ-006 | Admin consulte logs techniques critiques | Selon rôle |
| TZ-007 | Étudiant modifie une présence | `403` |
| TZ-008 | Administration exporte un rapport autorisé | Autorisé |
| TZ-009 | Responsable cumule rôle formateur | Contextes corrects |
| TZ-010 | Délégation expirée | `403` |

---

# 9. Tests des imports

| ID | Fichier | Résultat |
|---|---|---|
| TI-001 | CSV apprenants valide | Simulation valide |
| TI-002 | Colonne email absente | Erreur bloquante |
| TI-003 | Email invalide | Ligne en erreur |
| TI-004 | Doublon dans fichier | Avertissement/erreur |
| TI-005 | Apprenant existant | Mise à jour proposée |
| TI-006 | Nouvelle classe | Changement proposé |
| TI-007 | Formation hors périmètre | `403` |
| TI-008 | 100 apprenants | Traitement réussi |
| TI-009 | XLSX multifeuille | Mapping par feuille |
| TI-010 | Fichier trop grand | Refus |
| TI-011 | Macro ou format interdit | Refus |
| TI-012 | Confirmation deux fois | Idempotence |
| TI-013 | Planning valide | Brouillon créé |
| TI-014 | Horaire inversé | Erreur |
| TI-015 | Conflit de salle | Alerte |
| TI-016 | Formateur inconnu | À corriger |
| TI-017 | Mapping IA faible | Confirmation obligatoire |

---

# 10. Tests d’émargement

| ID | Scénario | Résultat |
|---|---|---|
| TE-001 | QR valide | Présence |
| TE-002 | QR expiré | Refus |
| TE-003 | QR d’une autre séance | Refus |
| TE-004 | QR fixe hors réseau | Refus |
| TE-005 | QR fixe après début | Refus |
| TE-006 | Code court valide | Présence |
| TE-007 | Deux scans simultanés | Une présence |
| TE-008 | WebAuthn échoue | Parcours alternatif |
| TE-009 | Apprenant sans smartphone | Saisie manuelle |
| TE-010 | Apprenant provisoire | Entrée provisoire |
| TE-011 | Distanciel collectif | Canal distant |
| TE-012 | Distanciel individuel autorisé | Accepté |
| TE-013 | Distanciel individuel non autorisé | Refus |
| TE-014 | Correction | Motif et audit |
| TE-015 | SSE déconnecté | Reconnexion |

---

# 11. Tests de fichiers

| ID | Scénario | Résultat |
|---|---|---|
| TF-001 | PDF 2 Mo | Accepté |
| TF-002 | PDF 6 Mo | Refus |
| TF-003 | Extension JPG avec exécutable | Refus |
| TF-004 | Utilisateur non autorisé | `403` |
| TF-005 | Nom avec traversée de chemin | Neutralisé |
| TF-006 | Fichier purgé | Métadonnée présente, contenu absent |

---

# 12. Tests IA

| ID | Scénario | Résultat |
|---|---|---|
| TIA-001 | Colonne « Courriel » | Mapping vers email |
| TIA-002 | Score faible | `TO_REVIEW` |
| TIA-003 | FastAPI indisponible | Mode manuel |
| TIA-004 | Suggestion rejetée | Aucune application |
| TIA-005 | Donnée ambiguë | Avertissement |
| TIA-006 | Résultat validé | Décision humaine tracée |

---

# 13. Tests IoT

| ID | Scénario | Résultat |
|---|---|---|
| TO-001 | Heartbeat valide | Dispositif en ligne |
| TO-002 | Device inconnu | Refus |
| TO-003 | Event ID rejoué | Ignoré/refusé |
| TO-004 | Séquence ancienne | Refus |
| TO-005 | Session inexistante | Refus |
| TO-006 | Broker coupé | Mise en file locale |
| TO-007 | Reconnexion | Republie sans doublon |
| TO-008 | Credential révoqué | Refus |

---

# 14. Tests de performance

## Scénarios

| ID | Mesure | Objectif initial |
|---|---|---|
| TP-001 | Planning en cache | Viser < 100 ms local |
| TP-002 | Génération du jeton | Viser < 100 ms local |
| TP-003 | Validation présence | Mesurer p50/p95 |
| TP-004 | Import de 100 élèves | Temps documenté |
| TP-005 | Rapport mensuel | Temps documenté |
| TP-006 | 20 scans simultanés | Aucun doublon |

Les objectifs sont des cibles mesurées, pas des garanties non vérifiées.

---

# 15. Tests de résilience

| ID | Panne | Résultat |
|---|---|---|
| TR-001 | Redis indisponible | QR indisponible proprement |
| TR-002 | FastAPI indisponible | Import manuel disponible |
| TR-003 | SMTP indisponible | Email en attente |
| TR-004 | MQTT indisponible | File locale |
| TR-005 | SSE interrompu | Reconnexion |
| TR-006 | Redémarrage Spring | Données durables conservées |
| TR-007 | Restauration MySQL | Application fonctionnelle |

---

# 16. Tests d’accessibilité

- navigation au clavier ;
- focus visible ;
- labels ;
- contraste ;
- messages d’erreur ;
- tableaux alternatifs ;
- solution sans caméra ;
- solution sans WebAuthn ;
- responsive ;
- zoom.

---

# 17. Cahier de recette

## REC-001 — Parcours responsable pédagogique

### Préconditions

- compte responsable actif ;
- formation affectée ;
- classe inexistante ou vide ;
- fichier CSV disponible.

### Étapes

1. Se connecter.
2. Sélectionner le contexte responsable.
3. Créer ou sélectionner la classe.
4. Importer les apprenants.
5. Lire les erreurs.
6. Confirmer.
7. Importer le planning.
8. Corriger les conflits.
9. Publier.

### Résultat attendu

- comptes créés ou mis à jour ;
- historique conservé ;
- séances créées ;
- audit disponible.

---

## REC-002 — Parcours formateur

1. Se connecter.
2. Consulter les séances.
3. Ouvrir la séance.
4. Ouvrir le point de contrôle.
5. Afficher le QR.
6. Suivre les présences.
7. Corriger un retard.
8. Clôturer.

### Résultat attendu

La liste est à jour et les actions sont auditées.

---

## REC-003 — Parcours apprenant

1. Se connecter.
2. Consulter le prochain cours.
3. Ouvrir l’émargement.
4. Scanner ou saisir le code.
5. Confirmer localement.
6. Consulter l’historique.
7. Déposer un justificatif fictif.

### Résultat attendu

La présence personnelle est visible sans exposition des données des
autres étudiants.

---

## REC-004 — Rapport

1. Sélectionner une classe.
2. Choisir une date.
3. Générer le rapport.
4. Exporter en CSV.
5. Rechercher un apprenant.
6. Générer son rapport.

### Résultat attendu

Les statuts et demi-journées sont cohérents.

---

## REC-005 — IoT

1. Lancer Mosquitto.
2. Lancer le client Raspberry Pi ou simulateur.
3. Publier un heartbeat.
4. Publier un événement.
5. Republier le même événement.

### Résultat attendu

Le premier est traité, le second est identifié comme doublon.

---

# 18. Fiche d’anomalie

| Champ | Contenu |
|---|---|
| ID | Identifiant |
| Titre | Résumé |
| Environnement | Local/test/staging |
| Version | Commit |
| Gravité | Mineure/majeure/bloquante |
| Étapes | Reproduction |
| Attendu | Résultat attendu |
| Obtenu | Résultat constaté |
| Preuve | Capture/log |
| Responsable | Affectation |
| Statut | Ouverte/corrigée/vérifiée |

---

# 18b. Scénario de recette — gestion de l'assiduité (tranche V10)

Branche `feature/attendance-management-and-reporting`. Exécuté en local
(profil `demo`), statuts HTTP relevés — voir
`docs/11-guide-demonstration.md` §10.

1. Ouvrir une séance ; constater le point de contrôle `START` ouvert.
2. Créer un second point de contrôle (`CUSTOM`), l'ouvrir, émettre son
   jeton (`POST .../checkpoints/{cpId}/attendance-token`), un apprenant
   émarge → présence rattachée à **ce** point de contrôle.
3. Émarger 20 min après le début planifié → statut `LATE`,
   `lateMinutes > 0`.
4. Saisir manuellement une présence `ABSENT` (motif obligatoire),
   corriger en `PRESENT` (motif), annuler (`CANCELLED`) ; l'historique
   `GET .../attendance/{aid}/history` liste les 3 entrées ordonnées.
5. Fermer la séance ; un apprenant dépose un justificatif métier sur une
   absence dérivée (`POST /api/v1/me/attendance/justifications`) ; un
   second dépôt actif → `409`.
6. Un `TEACHER` sur `.../justifications/{id}/review` → `403` ;
   l'administration accepte → présence `ABSENT → EXCUSED_ABSENCE` ; un
   refus sans motif → `400`.
7. Consulter `GET /api/v1/attendance/reports/summary` puis
   `.../students` ; exporter le CSV (`.../students/export`) — vérifier
   BOM UTF-8, séparateur `;`, et qu'une cellule débutant par `=` est
   préfixée d'une apostrophe.
8. Vérifier les rôles : un `STUDENT` sur `/me/attendance` → `200`, un
   non-`STUDENT` → `403` ; un `TEACHER` sur `/attendance/reports/**` →
   `403`.

**Critères de validation** : aucun `500` pour un conflit métier attendu ;
motif obligatoire respecté ; historique conservé ; aucune donnée
personnelle ni identifiant SQL dans l'audit ou le CSV ; JWT et contexte
de rôle en mémoire seule côté front (aucun accès `localStorage` /
`sessionStorage`).

---

# 19. Critères de sortie

Une version est candidate à la soutenance lorsque :

- le parcours principal fonctionne ;
- aucune anomalie bloquante n’est ouverte ;
- les tests critiques passent ;
- les données sont fictives ;
- les rôles sont vérifiés ;
- les rapports fonctionnent ;
- la documentation est cohérente ;
- une sauvegarde est disponible ;
- la vidéo de secours existe.