# Journal d’utilisation de l’intelligence artificielle

## Règles

- ne pas transmettre de données réelles ;
- ne pas transmettre de secrets ;
- vérifier chaque résultat ;
- conserver les prompts importants ;
- distinguer aide, décision et validation ;
- ne pas présenter une production IA comme preuve sans contrôle.

## Registre

| Date | Outil | Tâche | Données | Résultat | Vérification | Décision |
|---|---|---|---|---|---|---|
| 28/08/2026 | ChatGPT | Cadrage | Besoins exprimés | Document MD | Relecture humaine | Retenu avec corrections |
| 28/08/2026 | ChatGPT | Cahier des charges | Besoins fictifs | Exigences | Relecture humaine | Retenu |
| 28/08/2026 | ChatGPT | Architecture | Cahier des charges | Architecture | Relecture humaine | Retenu |
| 28/08/2026 | ChatGPT | Modèle de données | Architecture | MCD/MLD | Relecture humaine | Retenu |
| 30/08/2026 | Claude Code | Correction : idempotence du provisionnement des comptes de démonstration sur base MySQL persistante (`DefaultDemoAccountProvisioner`) | Code back-end, aucun secret | Resynchronisation du mot de passe et du statut `ACTIVE` des 4 comptes fictifs à chaque amorçage `demo`, hachage réécrit seulement si nécessaire | `./mvnw clean test` → 502 tests, 0 échec ; 3 tests ajoutés (resync, idempotence fonctionnelle, garde `@Profile("demo")`) | Retenu |

## Modèle d’entrée

```markdown
## [DATE] — [OUTIL]

### Objectif

[Description]

### Données envoyées

- [Données]
- Classification : fictive/anonymisée/publique

### Prompt résumé

[Résumé]

### Résultat

[Résultat]

### Vérifications

- [Test]
- [Source]
- [Relecture]

### Décision

- Accepté
- Corrigé
- Rejeté

### Limites

[Limites]
```

## Responsabilité

Les assistants IA :

- proposent ;
- accélèrent ;
- expliquent ;
- génèrent des brouillons.

Le candidat :

- choisit ;
- vérifie ;
- teste ;
- assume les décisions ;
- explique le résultat au jury.