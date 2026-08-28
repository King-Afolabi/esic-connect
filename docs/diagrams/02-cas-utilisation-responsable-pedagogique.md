# Cas d’utilisation — Responsable pédagogique

```mermaid
flowchart LR
    RP[Responsable pédagogique]

    subgraph EC[ESIC Connect]
        A((Sélectionner son contexte))
        B((Gérer ses formations))
        C((Créer une classe))
        D((Importer les apprenants))
        E((Simuler l’import))
        F((Confirmer l’import))
        G((Importer le planning))
        H((Corriger le brouillon))
        I((Détecter les conflits))
        J((Publier le planning))
        K((Affecter les formateurs))
        L((Valider un remplacement))
        M((Annuler une séance))
        N((Créer une séance exceptionnelle))
        O((Traiter un justificatif))
        P((Traiter une réclamation))
        Q((Corriger une présence ancienne))
        R((Consulter les tableaux de bord))
        S((Exporter un rapport))
        T((Déléguer temporairement une formation))
    end

    RP --> A
    RP --> B
    RP --> C
    RP --> D
    D --> E
    E --> F
    RP --> G
    G --> H
    H --> I
    I --> J
    RP --> K
    RP --> L
    RP --> M
    RP --> N
    RP --> O
    RP --> P
    RP --> Q
    RP --> R
    RP --> S
    RP --> T
```

## Préconditions générales

- Le compte est actif.
- L’utilisateur possède le rôle `PEDAGOGICAL_MANAGER`.
- Il est affecté à la formation concernée.
- Une authentification renforcée peut être demandée pour une action
  sensible.

## Postconditions importantes

- Les imports confirmés sont audités.
- Les séances proviennent d’un planning publié.
- Les anciennes inscriptions sont conservées.
- Les changements publiés peuvent générer des notifications.
- Toute correction ancienne exige un motif.