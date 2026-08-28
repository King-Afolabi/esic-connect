\`\`\`markdown  
\# CLAUDE.md — ESIC Connect  
  
\## Références  
  
Lire avant toute tâche métier ou technique :  
  
\- @docs/01-cadrage.md  
\- @docs/02-cahier-des-charges.md  
\- @docs/CURRENT-STATE.md  
  
Le cahier des charges définit les exigences.    
Le code et les tests définissent ce qui est réellement réalisé.  
  
\## Objectif prioritaire  
  
\`\`\`text  
Import apprenants  
→ Import planning  
→ Publication  
→ Création des séances  
→ Ouverture par le formateur  
→ Émargement  
→ Rapport  
\`\`\`  
  
\## Stack  
  
\- Java 21, Spring Boot, Maven  
\- Angular, Angular Material, PWA  
\- MySQL 8  
\- Redis 7  
\- Python, FastAPI  
\- Raspberry Pi 4, MQTT  
\- Docker Compose  
  
\## Règles  
  
\- Utiliser uniquement des données fictives.  
\- Ne jamais enregistrer de secret dans Git.  
\- Ne jamais inventer une fonctionnalité, un test ou un résultat.  
\- Respecter strictement le cahier des charges.  
\- Demander confirmation avant de modifier une règle métier.  
\- Commencer par les exigences \`MUST\`.  
\- Écrire et exécuter les tests.  
\- Contrôler les autorisations côté Spring Boot.  
\- Ne pas supprimer les historiques.  
\- Ne pas utiliser \`localStorage\` pour les jetons sensibles.  
\- Ne pas créer de microservices Java, de MongoDB ou de Kubernetes.  
\- Ne pas commencer par AWS.  
\- Ne pas réécrire entièrement un document pour une modification mineure.  
  
\## Méthode  
  
Pour chaque tâche :  
  
1\. Lire uniquement les fichiers utiles.  
2\. Examiner le code existant.  
3\. Proposer un plan court.  
4\. Implémenter une seule fonctionnalité.  
5\. Écrire et exécuter les tests.  
6\. Mettre à jour \`docs/CURRENT-STATE.md\`.  
7\. Indiquer les fichiers modifiés, les tests et les limites.  
  
\## Statuts  
  
\- \`TODO\`  
\- \`IN\_PROGRESS\`  
\- \`IMPLEMENTED\`  
\- \`TESTED\`  
\- \`DEMONSTRATED\`  
\- \`SIMULATED\`  
\- \`DEFERRED\`  
  
Ne jamais confondre \`IMPLEMENTED\`, \`TESTED\` et \`DEMONSTRATED\`.  
  
\## Définition de terminé  
  
Une tâche est terminée lorsque :  
  
\- le code compile ;  
\- les tests passent ;  
\- la sécurité est contrôlée ;  
\- la documentation reflète la réalité ;  
\- les commandes de vérification sont fournies.  
\`\`\`