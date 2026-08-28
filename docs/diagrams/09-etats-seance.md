# Diagramme d’états — Séance

```mermaid
stateDiagram-v2
    [*] --> PLANNED

    PLANNED --> OPEN: Ouverture autorisée
    PLANNED --> CANCELLED: Annulation validée
    PLANNED --> POSTPONED: Report décidé

    OPEN --> CLOSED: Clôture par le formateur
    OPEN --> CANCELLED: Incident exceptionnel

    POSTPONED --> PLANNED: Nouvelle date publiée
    CANCELLED --> [*]
    CLOSED --> [*]

    note right of PLANNED
      Issue d’un planning publié
    end note

    note right of OPEN
      Points de contrôle disponibles
    end note

    note right of CLOSED
      Corrections limitées et auditées
    end note
```

## Transitions autorisées

| État initial | État cible | Acteur |
|---|---|---|
| `PLANNED` | `OPEN` | Formateur affecté ou remplaçant |
| `PLANNED` | `CANCELLED` | Responsable pédagogique |
| `PLANNED` | `POSTPONED` | Responsable pédagogique |
| `OPEN` | `CLOSED` | Formateur |
| `POSTPONED` | `PLANNED` | Responsable pédagogique |

Toute transition doit être auditée.