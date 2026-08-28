# Diagramme d’activité — Importation du planning

```mermaid
flowchart TD
    A([Début]) --> B[Sélectionner la classe]
    B --> C[Téléverser le fichier]
    C --> D[Créer un brouillon d’import]
    D --> E[Détecter les feuilles et colonnes]
    E --> F[Proposer le mapping]
    F --> G[Normaliser dates, heures et valeurs]
    G --> H[Rechercher matières et formateurs]
    H --> I[Détecter les conflits]

    I --> J{Valeurs ambiguës ?}
    J -->|Oui| K[Afficher les suggestions et scores]
    K --> L[Correction humaine]
    L --> M[Revalider]

    J -->|Non| M
    M --> N{Erreurs bloquantes ?}

    N -->|Oui| O[Revenir au brouillon]
    O --> L

    N -->|Non| P[Prévisualiser le calendrier]
    P --> Q{Publier ?}

    Q -->|Non| R[Enregistrer le brouillon]
    R --> Z([Fin])

    Q -->|Oui| S[Créer une version du planning]
    S --> T[Créer ou mettre à jour les séances]
    T --> U[Invalider les caches]
    U --> V[Préparer les notifications]
    V --> W[Auditer la publication]
    W --> X[Afficher le planning publié]
    X --> Z
```

## Conflits contrôlés

- formateur affecté à deux séances ;
- salle utilisée simultanément ;
- classe affectée à deux séances ;
- horaire invalide ;
- formateur inconnu ;
- salle inactive ;
- incohérence avec l’alternance ;
- lien distant manquant si nécessaire.

## Position de l’IA

L’IA fournit une suggestion. Le responsable pédagogique reste le seul
décideur de la publication.