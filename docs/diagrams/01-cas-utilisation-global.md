# Diagramme de cas d’utilisation global

## Objectif

Présenter les principales interactions entre les acteurs et ESIC Connect.

```mermaid
flowchart LR
    SA[Super administrateur]
    AD[Administrateur]
    SC[Administration scolaire]
    RP[Responsable pédagogique]
    FO[Formateur]
    ET[Apprenant]
    PI[Raspberry Pi]

    subgraph ESIC[ESIC Connect]
        UC01((S’authentifier))
        UC02((Gérer les comptes))
        UC03((Gérer les référentiels))
        UC04((Importer les apprenants))
        UC05((Gérer le planning))
        UC06((Publier le planning))
        UC07((Gérer les séances))
        UC08((Gérer les remplacements))
        UC09((Ouvrir l’émargement))
        UC10((Émarger))
        UC11((Corriger une présence))
        UC12((Déposer un justificatif))
        UC13((Traiter un justificatif))
        UC14((Créer une réclamation))
        UC15((Produire les rapports))
        UC16((Consulter les audits))
        UC17((Gérer les dispositifs))
        UC18((Analyser les anomalies))
    end

    SA --> UC01
    SA --> UC02
    SA --> UC16
    SA --> UC17

    AD --> UC01
    AD --> UC02
    AD --> UC03
    AD --> UC04
    AD --> UC16

    SC --> UC01
    SC --> UC04
    SC --> UC13
    SC --> UC15

    RP --> UC01
    RP --> UC03
    RP --> UC04
    RP --> UC05
    RP --> UC06
    RP --> UC07
    RP --> UC08
    RP --> UC11
    RP --> UC13
    RP --> UC15

    FO --> UC01
    FO --> UC07
    FO --> UC08
    FO --> UC09
    FO --> UC11
    FO --> UC13

    ET --> UC01
    ET --> UC10
    ET --> UC12
    ET --> UC14
    ET --> UC15

    PI --> UC10
    PI --> UC17
    PI --> UC18
```

## Acteurs

| Acteur | Responsabilité |
|---|---|
| Super administrateur | Administration technique et sécurité |
| Administrateur | Administration fonctionnelle globale |
| Administration scolaire | Gestion administrative et rapports |
| Responsable pédagogique | Pilotage d’un périmètre de formation |
| Formateur | Gestion opérationnelle de ses séances |
| Apprenant | Planning, émargement et suivi personnel |
| Raspberry Pi | Borne IoT d’émargement et télémétrie |

## Règles importantes

- Un utilisateur peut cumuler plusieurs rôles.
- Le responsable pédagogique reste limité à son périmètre.
- Le formateur ne publie pas le planning.
- L’apprenant accède uniquement à ses données.
- La Raspberry Pi ne crée jamais directement une présence définitive :
  Spring Boot valide toujours l’événement.