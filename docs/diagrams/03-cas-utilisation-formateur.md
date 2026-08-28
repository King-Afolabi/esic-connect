# Cas d’utilisation — Formateur

```mermaid
flowchart LR
    F[Formateur]

    subgraph EC[ESIC Connect]
        A((Consulter son planning))
        B((Consulter une séance))
        C((Demander une annulation))
        D((Proposer un remplaçant))
        E((Ouvrir une séance))
        G((Ouvrir un point de contrôle))
        H((Afficher le QR dynamique))
        I((Afficher le code court))
        J((Suivre les présences))
        K((Enregistrer une présence manuelle))
        L((Ajouter un apprenant provisoire))
        M((Déclarer un retard))
        N((Corriger une présence))
        O((Enregistrer un départ anticipé))
        P((Transmettre un justificatif))
        Q((Répondre à une réclamation))
        R((Clôturer la séance))
    end

    F --> A
    F --> B
    F --> C
    F --> D
    F --> E
    E --> G
    G --> H
    G --> I
    F --> J
    F --> K
    F --> L
    F --> M
    F --> N
    F --> O
    F --> P
    F --> Q
    F --> R
```

## Restrictions

- Le formateur ne publie pas le planning.
- Il ne valide pas lui-même son remplacement.
- Il ne supprime pas une présence.
- Une correction exige un motif.
- Une correction ancienne peut nécessiter un responsable pédagogique.
- L’ajout d’un nouvel apprenant crée une entrée provisoire, pas une
  inscription officielle.