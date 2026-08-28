# Diagramme de séquence — Authentification adaptative

```mermaid
sequenceDiagram
    actor U as Utilisateur
    participant F as Angular/PWA
    participant A as Spring Security
    participant DB as MySQL
    participant R as Redis
    participant W as WebAuthn/MFA
    participant AU as Audit

    U->>F: Saisit email et mot de passe
    F->>A: POST /api/v1/auth/login
    A->>R: Vérifie la limitation des tentatives
    A->>DB: Recherche le compte
    DB-->>A: Compte, rôles et statut
    A->>A: Vérifie le mot de passe

    alt Identifiants invalides
        A->>R: Incrémente les échecs
        A->>AU: Enregistre l’échec
        A-->>F: Réponse neutre 401
    else Compte suspendu ou archivé
        A->>AU: Enregistre le refus
        A-->>F: Accès refusé
    else Connexion à risque
        A-->>F: Demande une preuve supplémentaire
        F->>W: WebAuthn ou TOTP
        W-->>F: Preuve
        F->>A: Transmet la preuve
        A->>A: Vérifie la preuve
        A->>R: Crée la session
        A->>AU: Enregistre la connexion
        A-->>F: Cookies sécurisés
    else Appareil reconnu et risque faible
        A->>R: Crée ou renouvelle la session
        A->>AU: Enregistre la connexion
        A-->>F: Cookies sécurisés
    end
```

## Décisions

- Cookie `HttpOnly`.
- Cookie `Secure` sous HTTPS.
- Jeton sensible absent de `localStorage`.
- MFA renforcé selon le rôle ou le risque.
- Réponse de connexion neutre.
- Audit des succès et échecs.