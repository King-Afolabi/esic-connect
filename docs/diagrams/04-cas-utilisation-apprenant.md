# Cas d’utilisation — Apprenant

```mermaid
flowchart LR
    E[Apprenant]

    subgraph EC[ESIC Connect]
        A((Activer le compte))
        B((S’authentifier))
        C((Enregistrer une passkey))
        D((Consulter le planning))
        F((Recevoir une notification))
        G((Ouvrir une séance))
        H((Scanner le QR))
        I((Saisir le code court))
        J((Confirmer avec WebAuthn))
        K((Consulter l’historique))
        L((Consulter le taux d’assiduité))
        M((Déposer un justificatif))
        N((Créer une réclamation))
        O((Échanger dans une réclamation))
        P((Consulter les modifications))
        Q((Télécharger son rapport))
    end

    E --> A
    E --> B
    E --> C
    E --> D
    E --> F
    E --> G
    G --> H
    G --> I
    H --> J
    I --> J
    E --> K
    E --> L
    E --> M
    E --> N
    E --> O
    E --> P
    E --> Q
```

## Restrictions

- L’apprenant ne consulte que ses propres données.
- Il ne modifie pas directement une présence.
- Le téléchargement du rapport peut être désactivé par le responsable.
- Un justificatif accepté produit `EXCUSED`, pas `PRESENT`.
- Une alternative est disponible si le smartphone ou WebAuthn est
  indisponible.