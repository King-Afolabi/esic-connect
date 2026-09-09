# Courriel réel (Brevo) — configuration et changement d'adresse d'expédition

Ce document explique **comment ESIC Connect envoie des courriels réels**,
pourquoi un envoi peut échouer silencieusement, et la procédure exacte
pour **changer l'adresse d'expédition** (`APP_MAIL_FROM`) sur le
déploiement Raspberry Pi.

Aucun secret ne figure ici. Les identifiants SMTP vivent uniquement dans
le fichier `.env` de la Pi (`chmod 600`, jamais commité — `SECRETS.md`).

---

## 1. Comment le produit envoie un courriel

Toute la chaîne est pilotée par variables d'environnement — **aucune
adresse n'est codée en dur** dans le back-end :

```
.env (Pi)                     application.yml                  code
─────────                     ──────────────                   ────
MAIL_HOST ───────────────────▶ spring.mail.host
MAIL_PORT ───────────────────▶ spring.mail.port
MAIL_USERNAME ───────────────▶ spring.mail.username
MAIL_PASSWORD ───────────────▶ spring.mail.password            JavaMailSender
MAIL_SMTP_AUTH ──────────────▶ spring.mail.properties.mail.smtp.auth
MAIL_SMTP_STARTTLS ──────────▶ ...starttls.enable
APP_MAIL_FROM ───────────────▶ app.mail.from ─────────────────▶ message.setFrom(...)
```

`app.mail.from` est injecté dans les trois émetteurs
(`JavaMailSenderInvitationMailer`, `JavaMailSenderPasswordResetMailer`,
`JavaMailSenderNotificationMailer`) via `@Value("${app.mail.from}")` et
appliqué par `SimpleMailMessage.setFrom(...)`.

Un changement d'adresse d'expédition est donc **uniquement** un
changement de `.env` + un redémarrage du back-end. Pas de rebuild, pas de
migration, pas de modification de code.

---

## 2. Pourquoi « les mails ne partent pas » après un changement d'adresse

Brevo **refuse d'expédier** un message dont l'adresse `From` n'est pas,
au choix :

1. un **expéditeur validé** (page Brevo *Senders, Domains & Dedicated
   IPs → Senders*) — Brevo envoie un lien de confirmation à l'adresse,
   qu'il faut ouvrir ; **ou**
2. une adresse d'un **domaine authentifié** chez Brevo (enregistrements
   DNS SPF + DKIM posés sur ce domaine).

Tant que l'une des deux conditions n'est pas remplie pour la **nouvelle**
adresse, le relais SMTP Brevo renvoie une erreur `550` et le message
n'est jamais remis. Côté ESIC Connect, `mailSender.send(...)` lève alors
une `MailException` : l'effet de bord part en file d'échec de l'outbox
(`FAILED` puis `DEAD`), visible dans **Exploitation → Effets de bord**
(`/exploitation/effets-de-bord`, `ADMIN` / `SUPER_ADMIN`).

> **`abubacar@etudiant-esci.fr`** : le domaine `etudiant-esci.fr`
> appartient à l'école — poser des enregistrements DNS SPF/DKIM y est
> hors de portée. Il faut donc passer par l'**option 1 : expéditeur
> unique validé**.

### Action côté compte Brevo (à faire une seule fois, hors du dépôt)

1. se connecter à `app.brevo.com` ;
2. *Senders, Domains & Dedicated IPs* → onglet **Senders** → **Add a
   sender** ;
3. renseigner un nom (« ESIC Connect ») et l'adresse
   `abubacar@etudiant-esci.fr` ;
4. ouvrir le courriel de confirmation reçu sur cette adresse et cliquer
   le lien de validation ;
5. vérifier que le sender apparaît **Verified** dans Brevo.

Sans cette étape, aucune modification de `.env` ne fera partir les
courriels.

---

## 3. Mettre à jour l'adresse d'expédition sur la Pi

> Nécessite un shell sur la Pi (`ssh king_a@192.168.1.83`, **réseau local
> uniquement** — le tunnel Cloudflare n'expose que l'application, pas
> SSH). À faire lors du prochain accès au même réseau que la Pi.

```bash
ssh king_a@192.168.1.83
cd ~/esic-connect

# 0. Sauvegarde préalable (règle de production) : ROLLBACK.md § Sauvegarde.
#    Un changement de .env ne touche pas la base, mais la règle est la règle.

# 1. Sauvegarder l'ancien .env
cp .env ".env.bak.$(date +%s)"

# 2. Éditer les variables de courriel. Valeurs cibles pour Brevo :
#      MAIL_HOST=smtp-relay.brevo.com
#      MAIL_PORT=587
#      MAIL_USERNAME=<login SMTP Brevo — visible dans Brevo > SMTP & API>
#      MAIL_PASSWORD=<clé SMTP Brevo — PAS le mot de passe du compte>
#      MAIL_SMTP_AUTH=true
#      MAIL_SMTP_STARTTLS=true
#      APP_MAIL_FROM=abubacar@etudiant-esci.fr
nano .env

# 3. Vérifier (sans révéler les secrets) que les 7 clés sont cohérentes
grep -E '^(MAIL_HOST|MAIL_PORT|MAIL_SMTP_AUTH|MAIL_SMTP_STARTTLS|APP_MAIL_FROM)=' .env
grep -cE '^(MAIL_USERNAME|MAIL_PASSWORD)=.+' .env   # doit afficher 2

# 4. Recréer le seul back-end (env-only : aucun rebuild, aucune migration)
docker compose -f compose.prod.yaml up -d backend
docker compose -f compose.prod.yaml ps            # backend "healthy"
```

`compose.prod.yaml` passe déjà tout le bloc `MAIL_*` / `APP_MAIL_FROM` au
service `backend` : aucune modification de la composition n'est
nécessaire.

---

## 4. Vérifier que l'envoi fonctionne

```bash
# a) Déclencher un envoi réel : demande de réinitialisation de mot de passe
#    (réponse volontairement neutre — c'est normal). Remplacer <URL> par
#    l'URL publique du tunnel.
curl -s -o /dev/null -w '%{http_code}\n' -X POST '<URL>/api/v1/auth/forgot-password' \
  -H 'Content-Type: application/json' \
  -d '{"email":"abubacar@etudiant-esci.fr"}'      # 200 attendu

# b) Journaux du back-end : aucune trace SMTP en erreur
docker compose -f compose.prod.yaml logs --since 3m backend | grep -iE 'mail|smtp|55[0-9]|MailException' || echo "aucune erreur mail"

# c) File d'échec de l'outbox : rien de neuf en DEAD/FAILED pour un email
#    → écran Exploitation → Effets de bord, ou :
docker compose -f compose.prod.yaml exec -T mysql sh -c \
 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e \
  "SELECT status, COUNT(*) FROM outbox_message WHERE message_type LIKE \"%MAIL%\" OR message_type LIKE \"%EMAIL%\" GROUP BY status"'

# d) Brevo > Logs (dans l'interface web) : le message apparaît "delivered".
```

Si le message part mais **atterrit en indésirable** : c'est SPF/DKIM
absents pour `etudiant-esci.fr` (non corrigeable sans accès DNS au
domaine de l'école). Ce n'est pas un défaut du produit — cf.
`docs/12-prerequis-externes.md` §5.

---

## 5. Retour au mode local (Mailpit / pas d'envoi réel)

Pour désactiver l'envoi réel sans casser le démarrage :

```
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
```

puis `docker compose -f compose.prod.yaml up -d backend`. Le back-end
démarre ; les envois échouent en file d'échec de l'outbox sans bloquer
les parcours.
