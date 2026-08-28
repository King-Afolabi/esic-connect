# Diagramme de séquence — Émargement dynamique

```mermaid
sequenceDiagram
    actor F as Formateur
    actor E as Apprenant
    participant UI as Angular/PWA
    participant API as Spring Boot
    participant R as Redis
    participant DB as MySQL
    participant WA as WebAuthn
    participant SSE as Flux SSE
    participant AU as Audit

    F->>API: Ouvre la séance
    API->>DB: Vérifie le formateur et la séance
    API->>DB: Passe la séance à OPEN
    API->>AU: Audit SESSION_OPENED

    F->>API: Ouvre un point de contrôle
    API->>R: Crée un jeton temporaire
    API-->>F: QR dynamique + code court

    E->>UI: Scanne le QR ou saisit le code
    UI->>WA: Demande une confirmation locale
    WA-->>UI: Preuve WebAuthn
    UI->>API: Jeton + preuve + session

    API->>R: Vérifie expiration et rejeu
    API->>DB: Vérifie inscription et autorisation
    API->>DB: Vérifie l’unicité du point
    API->>API: Calcule le retard

    alt Jeton invalide ou expiré
        API->>AU: Audit ATTENDANCE_REJECTED
        API-->>UI: Refus explicite
    else Présence déjà enregistrée
        API-->>UI: Résultat idempotent
    else Validation acceptée
        API->>DB: Insère AttendanceRecord
        API->>AU: Audit ATTENDANCE_RECORDED
        API-->>UI: Présence confirmée
        API->>SSE: Publie attendance-recorded
        SSE-->>F: Actualise le tableau
    end
```

## Contrôles

- séance ouverte ;
- utilisateur authentifié ;
- inscription active ;
- point de contrôle actif ;
- jeton Redis valide ;
- unicité ;
- autorisation de distanciel ;
- WebAuthn lorsqu’il est disponible ou requis.