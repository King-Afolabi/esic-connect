# Diagramme d’activité — Importation des apprenants

```mermaid
flowchart TD
    A([Début]) --> B[Choisir la formation et la classe]
    B --> C[Téléverser CSV ou XLSX]
    C --> D{Type et taille valides ?}

    D -->|Non| E[Afficher l’erreur technique]
    E --> C

    D -->|Oui| F[Créer un import en brouillon]
    F --> G[Lire le fichier et les feuilles]
    G --> H[Détecter les colonnes]
    H --> I[Normaliser les valeurs]
    I --> J[Contrôler les champs obligatoires]
    J --> K[Détecter les doublons]
    K --> L[Rechercher les comptes existants]
    L --> M[Calculer les créations et mises à jour]
    M --> N[Afficher la simulation]

    N --> O{Erreurs bloquantes ?}
    O -->|Oui| P[Corriger le fichier ou le mapping]
    P --> C

    O -->|Non| Q{Confirmation ?}
    Q -->|Non| R[Conserver le brouillon ou annuler]
    R --> Z([Fin])

    Q -->|Oui| S[Ouvrir une transaction]
    S --> T[Créer ou mettre à jour les comptes]
    T --> U[Clôturer les anciennes inscriptions]
    U --> V[Créer les nouvelles inscriptions]
    V --> W[Créer les invitations]
    W --> X[Enregistrer l’audit]
    X --> Y[Afficher le bilan]
    Y --> Z
```

## Cas d’échec

- colonne obligatoire absente ;
- email invalide ;
- classe inconnue ;
- formation hors périmètre ;
- doublon dans le fichier ;
- conflit avec un compte existant ;
- classe cible identique ;
- import déjà confirmé ;
- erreur transactionnelle.

## Règle d’intégrité

La simulation ne modifie pas les données métier définitives.