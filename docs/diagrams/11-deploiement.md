# Diagrammes de déploiement

## Déploiement local

```mermaid
flowchart TB
    DEV[Poste de développement]

    subgraph HOST[Docker / poste local]
        FE[Angular]
        BE[Spring Boot]
        DB[(MySQL)]
        RE[(Redis)]
        AI[FastAPI]
        MQ[Mosquitto]
        MP[Mailpit]
        FS[(Volume fichiers)]
    end

    PI[Raspberry Pi 4]

    DEV --> FE
    FE --> BE
    BE --> DB
    BE --> RE
    BE --> AI
    BE --> MP
    BE --> FS
    PI --> MQ
    MQ --> BE
```

## Déploiement staging

```mermaid
flowchart TB
    U[Utilisateurs de recette] -->|HTTPS| RP[Reverse Proxy]

    subgraph VM[VM cloud staging]
        RP --> FE[Angular/Nginx]
        RP --> BE[Spring Boot]
        BE --> DB[(MySQL)]
        BE --> RE[(Redis)]
        BE --> AI[FastAPI]
        BE --> MQ[Mosquitto]
        BE --> FS[(Volume sécurisé)]
    end
```

## Production AWS cible

```mermaid
flowchart TB
    USER[Utilisateurs] --> CF[CloudFront]
    CF --> S3WEB[S3 Front-end]
    CF --> ALB[Load Balancer]
    ALB --> APP[Spring Boot sur ECS/App Runner]
    APP --> RDS[(RDS MySQL)]
    APP --> CACHE[(ElastiCache/Valkey)]
    APP --> S3FILE[(S3 fichiers)]
    APP --> SQS[SQS]
    SQS --> SES[SES]
    IOT[AWS IoT Core] --> APP
    APP --> CW[CloudWatch]
    APP --> SM[Secrets Manager]
```

## Environnements

```text
local → test → staging → production
```

- Local : développement.
- Test : tests automatisés.
- Staging : recette et démonstration.
- Production : exploitation réelle.