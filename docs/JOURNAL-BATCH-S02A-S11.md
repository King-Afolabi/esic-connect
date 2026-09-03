# Journal d'exécution — lot S2A → S11

> **Nature de ce document.** Un relevé, sprint par sprint, de ce qui a été
> réellement livré, testé et documenté pendant l'exécution du lot
> `batch/S02A-S11`. Il ne remplace pas `docs/CURRENT-STATE.md`, qui reste
> la seule source de vérité sur l'état du dépôt : il en donne l'historique.
>
> Aucun chiffre de test n'est reporté ici sans avoir été produit par une
> commande exécutée. Aucun jalon n'est posé sans que l'incrément
> correspondant soit présent, testé et documenté.

## Référence d'entrée

| Élément | Valeur |
|---|---|
| Branche de coordination | `batch/S02A-S11` |
| Base | `f0d02d4` sur `feature/produit-complet-v2` |
| État initial mesuré | back-end **858 tests / 0 échec**, front-end **617 tests / 0 échec** |
| Commande de mesure | `cd backend && ./mvnw clean test` ; `cd frontend && npm test -- --watch=false` |

---

## S2 — Sécurité forte (`sprint/S02-securite`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

`EF-AUTH-005` mot de passe oublié, `EF-AUTH-012` limitation de débit,
`EF-AUTH-014` déconnexion et révocation, migration `V17`, décision
`DEC-S2-003`. Rien de tout cela n'a été réécrit.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-AUTH-006` | passkeys WebAuthn : options d'enregistrement, vérification d'attestation, liste, révocation individuelle |
| `EF-AUTH-007` | connexion sans mot de passe par passkey, défi à usage unique, compteur de signature contrôlé |
| `EF-AUTH-008` | second facteur TOTP (RFC 6238) : enrôlement, confirmation, vérification, anti-rejeu du pas de temps |
| `EF-AUTH-009` | dix codes de récupération à usage unique, régénération invalidant les anciens |
| `EF-AUTH-010` | authentification adaptative : appareil inconnu → second facteur ; appareil reconnu → reconnexion allégée, jamais pour un rôle privilégié |
| `EF-AUTH-011` | anti-robot Turnstile derrière un port, vérification **serveur**, adaptateur local documenté, politique de repli `DEC-S2-004` |
| `EF-AUTH-013` | appareils de confiance : empreinte seule, durée bornée, liste et révocation, isolation entre comptes |
| `EF-AUTH-015` | réauthentification forte exigée avant un changement de rôle (claim `amr`) |

### Critères d'acceptation couverts

| Critère | Test |
|---|---|
| `AC-020` — aucune donnée biométrique reçue | `WebAuthnIntegrationTests` |
| `AC-021` — second facteur exigé pour `SUPER_ADMIN` / `ADMIN` | `MfaIntegrationTests` |
| `AC-022` — contrôle renforcé après trois échecs | `CaptchaIntegrationTests` |
| `AC-023` — réponse neutre à la demande de réinitialisation | déjà couvert par `PasswordResetIntegrationTests` (S2 partie A) |

### Migration

`V18__create_strong_authentication_tables.sql` — `mfa_credential`,
`mfa_recovery_code`, `webauthn_credential`, `trusted_device`.

### Décisions

`DEC-S2-004` repli anti-robot, `DEC-S2-005` enrôlement forcé pendant la
connexion, `DEC-S2-006` portée d'un appareil de confiance. Consignées
dans `docs/03-architecture.md`.

### Effet de bord assumé sur la suite de tests

Rendre le second facteur réellement obligatoire change le contrat de
`POST /auth/login` : un compte privilégié n'obtient plus de jeton contre
son seul mot de passe. Trente-quatre classes de test obtenaient leur
jeton ainsi. Elles passent désormais par `AuthTestSupport`, qui franchit
le défi comme le ferait un utilisateur — **aucun raccourci, aucun profil
dérogatoire, aucune porte dérobée de test**.

`AuthRateLimitIntegrationTests` a été rendu autonome : il remet à zéro
les compteurs indexés sur l'origine (`127.0.0.1`), partagés par toute la
suite. Ce couplage préexistait ; il devenait visible dès que le nombre de
connexions augmentait.

### Limites restantes, explicitement assumées

- La **cérémonie WebAuthn complète** n'est pas rejouée en test : elle
  exige un authentificateur qui signe réellement. Les tests couvrent le
  contrat, le cycle de vie du défi, l'isolation et l'absence de donnée
  biométrique ; la vérification cryptographique est celle de la
  bibliothèque. Une signature « simulée » ne prouverait rien.
- Turnstile n'est **pas vérifié contre le service réel** : aucune clé
  secrète n'est configurée dans le dépôt. Sans clé, le produit déclare
  franchement, via `GET /api/v1/auth/captcha`, qu'aucun contrôle n'est
  actif — il n'en simule pas un.
- Les passkeys exigent un contexte sûr : elles fonctionnent sur
  `localhost`, et exigeront **un domaine et HTTPS** hors du poste de
  développement.
