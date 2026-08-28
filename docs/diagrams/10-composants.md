# Diagramme de composants

```mermaid
flowchart TB
    subgraph CLIENTS[Clients]
        WEB[Navigateur]
        PWA[Angular PWA]
        RPI[Raspberry Pi 4]
    end

    subgraph PRESENTATION[Présentation]
        ANG[Angular / Angular Material]
        SW[Service Worker PWA]
    end

    subgraph BACKEND[Spring Boot — monolithe modulaire]
        AUTH[Identity & Security]
        ACA[Academic & Enrollment]
        PLAN[Planning]
        SES[Course Session]
        ATT[Attendance]
        REP[Reporting]
        AUD[Audit]
        NOT[Notification]
        IOT[IoT Adapter]
        AIC[AI Adapter]
    end

    subgraph DATA[Données]
        MYSQL[(MySQL)]
        REDIS[(Redis)]
        FILES[(Stockage fichiers)]
    end

    subgraph SERVICES[Services internes]
        FAST[FastAPI / IA]
        MQTT[Mosquitto]
        MAIL[Mailpit]
    end

    WEB --> ANG
    PWA --> ANG
    ANG --> AUTH
    ANG --> ACA
    ANG --> PLAN
    ANG --> SES
    ANG --> ATT
    ANG --> REP

    AUTH --> MYSQL
    AUTH --> REDIS
    ACA --> MYSQL
    PLAN --> MYSQL
    PLAN --> REDIS
    SES --> MYSQL
    ATT --> MYSQL
    ATT --> REDIS
    REP --> MYSQL
    REP --> REDIS
    AUD --> MYSQL
    NOT --> MYSQL
    NOT --> MAIL
    AIC --> FAST
    IOT --> MQTT
    RPI --> MQTT
    ATT --> FILES
```

## Principe

Spring Boot reste :

- l’autorité de sécurité ;
- l’autorité métier ;
- le point d’entrée du navigateur ;
- le validateur des événements IA et IoT.

FastAPI et MQTT ne sont pas directement exposés au navigateur.