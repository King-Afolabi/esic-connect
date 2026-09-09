#!/usr/bin/env python3
"""Amorçage de démonstration complet — ESIC Connect (lot complémentaire, Lot §4).

Construit, PAR LES API REST RÉELLES et avec le compte ADMIN de
démonstration (second facteur TOTP franchi, jamais contourné) :

  * référentiels : année 2026-2027, 5 formations (BTS SIO, BTS CIEL,
    Bachelor CDA, Master ESIS, Master CPDIA), leurs niveaux, promotions
    et 9 classes ;
  * organisation physique : sites Malakoff (étages 1, 2, 5) et Paris
    (rez-de-chaussée), bâtiments, salles, plages réseau ;
  * 4 familles de rythmes d'alternance + affectation aux 9 classes ;
  * affectation du responsable pédagogique à chaque formation ;
  * 18 comptes formateurs fictifs, ACTIFS (invitation + activation via
    Mailpit — parcours réel EF-AUTH-004) ;
  * apprenants fictifs par classe via l'import CSV réel (simulation +
    confirmation) — restent PENDING_ACTIVATION (nourrit EF-REP-010) ;
  * ~3 mois de planning par classe via l'import CSV de planning réel
    (simulation + publication versionnée), déterministe et SANS CONFLIT
    (salle et binôme de formateurs dédiés par classe, une séance par
    demi-journée, rythmes respectés) ;
  * scénarios d'assiduité : ouverture de séances passées, points de
    contrôle nommés, émargement manuel (présent / retard / absent),
    clôture ;
  * jeux de fichiers d'import (valide / avertissement / bloquant /
    multi-anomalies / doublons) écrits sous docs/demo-data/ et
    RÉELLEMENT importés pour vérifier leur comportement.

Toutes les données sont fictives (domaine @example.test, entreprises
imaginaires). Aucun secret n'est écrit : le mot de passe de démo et le
secret TOTP viennent de l'environnement.

Prérequis : back-end démarré en profil `demo` sur $API_BASE, Mailpit
joignable sur $MAILPIT_BASE, variables ESIC_DEMO_PASSWORD et
ESIC_DEMO_TOTP_SECRET identiques à celles du back-end.

    API_BASE=http://localhost:8080 \
    ESIC_DEMO_PASSWORD=... ESIC_DEMO_TOTP_SECRET=... \
    python3 scripts/seed-demo-full.py [phase ...]

phases : ref sites alt mgr teachers students planning attendance fixtures
         (aucune = toutes, dans cet ordre)
"""

from __future__ import annotations

import base64
import datetime as dt
import hashlib
import hmac
import io
import json
import os
import re
import struct
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request


def ascii_fold(text: str) -> str:
    """Retire les accents pour un usage sûr en partie locale d'e-mail."""
    return (unicodedata.normalize("NFKD", text)
            .encode("ascii", "ignore").decode("ascii"))

API_BASE = os.environ.get("API_BASE", "http://localhost:8080").rstrip("/")
API = API_BASE + "/api/v1"
MAILPIT = os.environ.get("MAILPIT_BASE", "http://localhost:8025").rstrip("/")
ADMIN_EMAIL = "admin@example.test"
PASSWORD = os.environ.get("ESIC_DEMO_PASSWORD") or sys.exit("ESIC_DEMO_PASSWORD requis")
TOTP_SECRET = os.environ.get("ESIC_DEMO_TOTP_SECRET") or sys.exit("ESIC_DEMO_TOTP_SECRET requis")
TZ = "Europe/Paris"
YEAR_CODE = "AY-2026"

# Fenêtre de planning : ~14 semaines, à cheval sur « aujourd'hui » pour
# nourrir à la fois l'historique d'assiduité et les séances à venir.
PLAN_START = dt.date(2026, 8, 24)          # lundi
PLAN_END = dt.date(2026, 11, 30)
CYCLE_ANCHOR = dt.date(2026, 8, 31)        # lundi — ancre des cycles semaine/4
# L'import CSV inscrit « à la date du jour ». La phase `backdate` recule
# ensuite chaque inscription à ENROLL_START pour que l'émargement de
# démonstration porte sur des séances RÉELLEMENT passées (antérieures à
# l'horloge du serveur) — sans quoi les tableaux de bord, qui mesurent
# jusqu'à « maintenant », afficheraient 0 %.
ENROLL_START = dt.date(2026, 8, 24)
ATT_FROM = dt.date(2026, 8, 25)
ATT_TO = dt.date(2026, 9, 6)

_HERE = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd()
DEMO_DATA_DIR = os.path.join(_HERE, "..", "docs", "demo-data")

# --------------------------------------------------------------------------
# HTTP + auth
# --------------------------------------------------------------------------

_token = None


def _totp(secret: str) -> str:
    key = base64.b32decode(secret.strip().upper() + "=" * ((8 - len(secret) % 8) % 8))
    counter = struct.pack(">Q", int(time.time()) // 30)
    mac = hmac.new(key, counter, hashlib.sha1).digest()
    off = mac[-1] & 0x0F
    binary = ((mac[off] & 0x7F) << 24) | ((mac[off + 1] & 0xFF) << 16) \
        | ((mac[off + 2] & 0xFF) << 8) | (mac[off + 3] & 0xFF)
    return str(binary % 1_000_000).zfill(6)


def _raw(method, url, data=None, headers=None):
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read()
            return resp.status, (json.loads(body) if body else None)
    except urllib.error.HTTPError as exc:
        body = exc.read()
        try:
            return exc.code, json.loads(body) if body else None
        except json.JSONDecodeError:
            return exc.code, {"raw": body.decode("utf-8", "replace")}


def api(method, path, body=None, auth=True, params=None):
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    headers = {"Content-Type": "application/json"}
    if auth and _token:
        headers["Authorization"] = "Bearer " + _token
    data = json.dumps(body).encode() if body is not None else None
    return _raw(method, url, data, headers)


def api_multipart(path, filename, content: bytes, params=None, content_type="text/csv"):
    boundary = "----esicseed" + hashlib.md5(filename.encode()).hexdigest()[:16]
    buf = io.BytesIO()
    buf.write(f"--{boundary}\r\n".encode())
    buf.write(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode())
    buf.write(f"Content-Type: {content_type}\r\n\r\n".encode())
    buf.write(content)
    buf.write(f"\r\n--{boundary}--\r\n".encode())
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Authorization": "Bearer " + _token,
    }
    return _raw("POST", url, buf.getvalue(), headers)


def login_admin():
    global _token
    status, body = api("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": PASSWORD}, auth=False)
    if status == 200 and body.get("accessToken"):
        _token = body["accessToken"]
        return
    challenge = (body or {}).get("mfa", {})
    cid = challenge.get("challengeId")
    if not cid:
        sys.exit(f"connexion ADMIN impossible (HTTP {status}) : {body}")
    if challenge.get("purpose") == "ENROLL":
        s, b = api("POST", "/auth/mfa/enroll", {"challengeId": cid}, auth=False)
        secret = (b or {}).get("secret")
        s, b = api("POST", "/auth/mfa/enroll/confirm",
                   {"challengeId": cid, "code": _totp(secret)}, auth=False)
        _token = (b or {}).get("session", {}).get("accessToken")
    else:
        for attempt in range(2):
            s, b = api("POST", "/auth/mfa/verify", {"challengeId": cid, "code": _totp(TOTP_SECRET)}, auth=False)
            if s == 200:
                _token = b["accessToken"]
                break
            if (b or {}).get("code") == "CODE_ALREADY_USED":
                time.sleep(31 - int(time.time()) % 30)
                continue
            sys.exit(f"second facteur ADMIN refusé (HTTP {s}) : {b}")
    if not _token:
        sys.exit("aucun jeton ADMIN obtenu")


def fail(msg, status=None, body=None):
    sys.exit(f"ÉCHEC : {msg}" + (f" (HTTP {status})" if status else "") + (f"\n  {body}" if body else ""))


# --------------------------------------------------------------------------
# helpers idempotents
# --------------------------------------------------------------------------

def ensure(list_path, code, create_body, code_field="code"):
    """POST create ; sur 409 retrouve par `code` via le filtre `q`. -> publicId"""
    status, body = api("POST", list_path, create_body)
    if status in (200, 201):
        return body["publicId"]
    if status == 409:
        s, b = api("GET", list_path, params={"q": code, "size": 100})
        for item in (b or {}).get("content", []):
            if item.get(code_field) == code:
                return item["publicId"]
    fail(f"création {list_path} ({code})", status, body)


def get_all(path, page_size=100, **params):
    """Pagine une route de liste et renvoie toutes les entrées."""
    out, page = [], 0
    while True:
        s, b = api("GET", path, params={"page": page, "size": page_size, **params})
        content = (b or {}).get("content", [])
        out.extend(content)
        total_pages = (b or {}).get("totalPages")
        if not content or (total_pages is not None and page + 1 >= total_pages) \
                or len(content) < page_size:
            return out
        page += 1


def find_user(email):
    s, b = api("GET", "/users", params={"q": email, "size": 50})
    for u in (b or {}).get("content", []):
        if u.get("email", "").lower() == email.lower():
            return u["publicId"], u.get("status")
    return None, None


def mailpit_token(email, timeout=20):
    q = urllib.parse.quote(f"to:{email} subject:Activation")
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            d = json.load(urllib.request.urlopen(f"{MAILPIT}/api/v1/search?query={q}&limit=5"))
        except Exception:
            d = {"messages": []}
        for m in d.get("messages", []):
            full = json.load(urllib.request.urlopen(f"{MAILPIT}/api/v1/message/{m['ID']}"))
            text = (full.get("Text") or "") + " " + (full.get("HTML") or "")
            hit = re.search(r"[?&]token=([A-Za-z0-9._~-]+)", text)
            if hit:
                return hit.group(1)
        time.sleep(1)
    return None


def ensure_active_account(email, first, last, role, password):
    pid, status = find_user(email)
    if pid is None:
        s, b = api("POST", "/users", {"email": email, "firstName": first, "lastName": last,
                                      "role": role, "sendInvitation": True})
        if s not in (200, 201):
            fail(f"création compte {email}", s, b)
        pid, status = b["publicId"], b.get("status")
    if status == "ACTIVE":
        return pid
    tok = mailpit_token(email)
    if not tok:
        fail(f"jeton d'activation introuvable dans Mailpit pour {email}")
    s, b = api("POST", "/account-invitations/activate", {"token": tok, "password": password}, auth=False)
    if s not in (200, 204):
        # déjà activé entre-temps ? on revérifie
        _, st = find_user(email)
        if st != "ACTIVE":
            fail(f"activation {email}", s, b)
    return pid


# --------------------------------------------------------------------------
# modèle de l'établissement
# --------------------------------------------------------------------------

PROGRAMS = [
    # (code, nom, type, [(level_code, level_name, seq)], [ (class_code, class_name, rhythm_code) ])
    ("BTS-SIO", "BTS SIO — Services informatiques aux organisations", "BTS",
     [("SIO1", "BTS SIO 1re année", 1), ("SIO2", "BTS SIO 2e année", 2)],
     [("BTS-SIO-1", "BTS SIO 1", "SIO1", "RY-BTS1"),
      ("BTS-SIO-2", "BTS SIO 2", "SIO2", "RY-BTS2")]),
    ("BTS-CIEL", "BTS CIEL — Cybersécurité, informatique et réseaux, électronique", "BTS",
     [("CIEL1", "BTS CIEL 1re année", 1), ("CIEL2", "BTS CIEL 2e année", 2)],
     [("BTS-CIEL-1", "BTS CIEL 1", "CIEL1", "RY-BTS1"),
      ("BTS-CIEL-2", "BTS CIEL 2", "CIEL2", "RY-BTS2")]),
    ("BACH-CDA", "Bachelor CDA — Concepteur développeur d'applications", "BACHELOR",
     [("CDA3", "Bachelor 3 — CDA", 3)],
     [("BACH-CDA", "Bachelor CDA", "CDA3", "RY-CDA")]),
    ("MAST-ESIS", "Mastère ESIS — Expert en systèmes d'information et sécurité", "MASTER",
     [("ESIS1", "Mastère ESIS 1", 1), ("ESIS2", "Mastère ESIS 2", 2)],
     [("MAST-ESIS-1", "Mastère ESIS 1", "ESIS1", "RY-MASTER"),
      ("MAST-ESIS-2", "Mastère ESIS 2", "ESIS2", "RY-MASTER")]),
    ("MAST-CPDIA", "Mastère CPDIA — Chef de projet digital, IA & data", "MASTER",
     [("CPDIA1", "Mastère CPDIA 1", 1), ("CPDIA2", "Mastère CPDIA 2", 2)],
     [("MAST-CPDIA-1", "Mastère CPDIA 1", "CPDIA1", "RY-MASTER"),
      ("MAST-CPDIA-2", "Mastère CPDIA 2", "CPDIA2", "RY-MASTER")]),
]

RHYTHMS = [
    ("RY-BTS1", "BTS 1re année — 3 jours école / 2 jours entreprise",
     "THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY", None,
     {"schoolDays": ["MONDAY", "TUESDAY", "WEDNESDAY"], "companyDays": ["THURSDAY", "FRIDAY"]}),
    ("RY-BTS2", "BTS 2e année — 2 semaines école sur 4, vendredi libre",
     "TWO_WEEKS_SCHOOL_OUT_OF_FOUR", 4,
     {"schoolWeeks": [1, 2], "companyWeeks": [3, 4],
      "schoolDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"]}),
    ("RY-MASTER", "Mastère (ESIS / CPDIA) — 1 semaine école sur 4",
     "ONE_WEEK_SCHOOL_OUT_OF_FOUR", 4,
     {"schoolWeeks": [1], "companyWeeks": [2, 3, 4]}),
    ("RY-CDA", "Bachelor CDA — 1 semaine école sur 4 (hypothèse provisoire, cursus 1 an)",
     "ONE_WEEK_SCHOOL_OUT_OF_FOUR", 4,
     {"schoolWeeks": [1], "companyWeeks": [2, 3, 4]}),
]

SITES = [
    ("MALAKOFF", "ESIC Malakoff", "Malakoff", [
        ("MLK-A", "Bâtiment A", [
            ("1er étage", ["MLK-101", "MLK-102", "MLK-103", "MLK-104"]),
            ("2e étage", ["MLK-201", "MLK-202", "MLK-203", "MLK-204"]),
            ("5e étage", ["MLK-501", "MLK-502", "MLK-503", "MLK-504"]),
        ]),
    ], "10.20.0.0/16"),
    ("PARIS", "ESIC Paris", "Paris", [
        ("PAR-R", "Bâtiment principal", [
            ("Rez-de-chaussée", ["PAR-001", "PAR-002", "PAR-003", "PAR-004"]),
        ]),
    ], "10.30.0.0/16"),
]

# Salle dédiée par classe (aucun partage -> aucun conflit de salle).
CLASS_ROOM = {
    "BTS-SIO-1": "MLK-101", "BTS-SIO-2": "MLK-102",
    "BTS-CIEL-1": "MLK-201", "BTS-CIEL-2": "MLK-202",
    "BACH-CDA": "MLK-501",
    "MAST-ESIS-1": "MLK-103", "MAST-ESIS-2": "MLK-203",
    "MAST-CPDIA-1": "MLK-104", "MAST-CPDIA-2": "MLK-204",
}

# Matières par classe (rotation des titres de séance).
SUBJECTS = {
    "BTS-SIO-1": ["Support et mise à disposition de services", "Bloc 1 — Cybersécurité",
                  "Mathématiques pour l'informatique", "Anglais technique", "Culture économique et juridique"],
    "BTS-SIO-2": ["Administration des systèmes et réseaux", "Développement d'applications",
                  "Cybersécurité des services informatiques", "Anglais", "Atelier de professionnalisation"],
    "BTS-CIEL-1": ["Réseaux et infrastructures", "Électronique numérique",
                   "Programmation embarquée", "Mathématiques", "Anglais technique"],
    "BTS-CIEL-2": ["Cybersécurité des systèmes", "Systèmes embarqués avancés",
                   "Supervision réseau", "Projet technique", "Anglais"],
    "BACH-CDA": ["Conception orientée objet", "Développement web full-stack",
                 "Bases de données avancées", "Qualité et tests", "Gestion de projet agile"],
    "MAST-ESIS-1": ["Architecture des SI", "Sécurité offensive et défensive",
                    "Gouvernance et conformité", "Cloud et virtualisation", "Anglais professionnel"],
    "MAST-ESIS-2": ["Audit de sécurité", "Réponse à incident", "Cryptographie appliquée",
                    "Management d'équipe SI", "Mémoire de fin d'études"],
    "MAST-CPDIA-1": ["Fondamentaux de la data", "Apprentissage automatique",
                     "Gestion de projet digital", "Éthique et IA", "Anglais professionnel"],
    "MAST-CPDIA-2": ["Ingénierie des données", "Deep learning appliqué", "MLOps",
                     "Pilotage de la transformation digitale", "Mémoire de fin d'études"],
}

FIRST_NAMES = ["Camille", "Lucas", "Sarah", "Yanis", "Léa", "Nathan", "Inès", "Adam", "Chloé",
               "Rayan", "Manon", "Enzo", "Jade", "Noah", "Louna", "Gabriel", "Emma", "Aaron",
               "Lina", "Ethan", "Nora", "Sacha", "Maya", "Isaac", "Ambre", "Elias", "Rose", "Nael"]
LAST_NAMES = ["Bernard", "Petit", "Robert", "Richard", "Durand", "Dubois", "Moreau", "Laurent",
              "Simon", "Michel", "Lefebvre", "Leroy", "Roux", "David", "Bertrand", "Morel",
              "Fournier", "Girard", "Bonnet", "Dupont", "Lambert", "Fontaine", "Rousseau",
              "Vincent", "Muller", "Faure", "Andre", "Mercier", "Blanc", "Guerin"]
COMPANIES = ["Atos", "Capgemini", "Sopra Steria", "Orange Business", "Thales", "Devoteam",
             "Inetum", "CGI", "Worldline", "OVHcloud"]


def stable_hash(text: str) -> int:
    """Hachage DÉTERMINISTE (contrairement à hash() qui varie par process).
    Garantit des noms / numéros d'apprenants identiques d'un run à l'autre,
    donc un import idempotent (l'apprenant existe déjà -> pas de doublon)."""
    return int(hashlib.md5(text.encode("utf-8")).hexdigest(), 16)


def cycle_week(date: dt.date) -> int:
    return ((date - CYCLE_ANCHOR).days // 7) % 4 + 1


def is_school_day(rhythm_code: str, date: dt.date) -> bool:
    wd = date.weekday()  # 0=Mon
    if rhythm_code == "RY-BTS1":
        return wd in (0, 1, 2)
    if rhythm_code == "RY-BTS2":
        return wd in (0, 1, 2, 3) and cycle_week(date) in (1, 2)
    if rhythm_code in ("RY-MASTER", "RY-CDA"):
        return wd in (0, 1, 2, 3, 4) and cycle_week(date) == 1
    return False


def daterange(start: dt.date, end: dt.date):
    d = start
    while d <= end:
        yield d
        d += dt.timedelta(days=1)


# --------------------------------------------------------------------------
# phases
# --------------------------------------------------------------------------

STATE = {}  # code -> publicId, plus 'classes', 'teachers'...


def load_state():
    """Recharge depuis l'API ce que les phases précédentes ont pu créer,
    pour qu'une phase puisse tourner seule (ex. `planning` sans `ref`)."""
    s, b = api("GET", "/academic-years", params={"q": YEAR_CODE, "size": 50})
    for y in (b or {}).get("content", []):
        if y.get("code") == YEAR_CODE:
            STATE["year"] = y["publicId"]
    STATE.setdefault("classes", {})
    prog_by_id = {}
    s, b = api("GET", "/programs", params={"size": 200})
    for p in (b or {}).get("content", []):
        STATE[p["code"]] = p["publicId"]
        prog_by_id[p["publicId"]] = p["code"]
    class_rhythm = {c[0]: c[3] for _, _, _, _, cl in PROGRAMS for c in cl}
    class_prog = {c[0]: pc for pc, _, _, _, cl in PROGRAMS for c in cl}
    s, b = api("GET", "/class-groups", params={"size": 200})
    for c in (b or {}).get("content", []):
        code = c.get("code")
        if code in class_rhythm:
            STATE["classes"][code] = {"id": c["publicId"], "program": class_prog[code],
                                      "rhythm": class_rhythm[code]}
    STATE.setdefault("rooms", {})
    for scode in ("MALAKOFF", "PARIS"):
        s, b = api("GET", "/sites", params={"q": scode, "size": 50})
        sid = next((x["publicId"] for x in (b or {}).get("content", []) if x.get("code") == scode), None)
        if sid:
            STATE[f"site:{scode}"] = sid
            s, b = api("GET", f"/sites/{sid}/rooms", params={"size": 200})
            for r in (b or {}).get("content", []):
                STATE["rooms"][r["code"]] = r["publicId"]
    STATE.setdefault("rhythms", {})
    s, b = api("GET", "/alternation/patterns", params={"size": 100})
    for r in (b or {}).get("content", []):
        STATE["rhythms"][r["code"]] = r["publicId"]
    STATE.setdefault("teachers", {})
    s, b = api("GET", "/users", params={"role": "TEACHER", "size": 500})
    by_email = {u.get("email", "").lower(): u["publicId"] for u in (b or {}).get("content", [])}
    for ccode in STATE["classes"]:
        pair = {}
        for slot in ("am", "pm"):
            email = f"prof.{ccode.lower().replace('-', '')}.{slot}@example.test"
            if email in by_email:
                pair[slot] = by_email[email]
        if len(pair) == 2:
            STATE["teachers"][ccode] = pair


def phase_ref():
    print("== référentiels ==")
    year = ensure("/academic-years", YEAR_CODE,
                  {"code": YEAR_CODE, "name": "2026-2027",
                   "startDate": "2026-09-01", "endDate": "2027-08-31"})
    STATE["year"] = year
    STATE["classes"] = {}
    for pcode, pname, ptype, levels, classes in PROGRAMS:
        prog = ensure("/programs", pcode, {"code": pcode, "name": pname, "programType": ptype})
        STATE[pcode] = prog
        lvl_ids = {}
        for lcode, lname, seq in levels:
            lvl_ids[lcode] = ensure(f"/programs/{prog}/levels", lcode,
                                    {"code": lcode, "name": lname, "sequenceNumber": seq})
        promo_code = f"PROMO-{pcode}-2026"
        promo = ensure("/promotions", promo_code,
                       {"programPublicId": prog, "academicYearPublicId": year,
                        "code": promo_code, "name": f"Promotion {pname} — 2026"})
        for ccode, cname, lcode, rhythm in classes:
            cid = ensure("/class-groups", ccode, {
                "promotionPublicId": promo, "programLevelPublicId": lvl_ids[lcode],
                "sitePublicId": STATE["site:MALAKOFF"], "code": ccode, "name": cname,
                "capacity": 30})
            STATE["classes"][ccode] = {"id": cid, "program": pcode, "rhythm": rhythm}
        print(f"  {pcode}: formation + {len(levels)} niveaux + promo + {len(classes)} classe(s)")


def phase_sites():
    print("== sites, bâtiments, salles ==")
    STATE["rooms"] = {}
    for scode, sname, city, buildings, cidr in SITES:
        sid = ensure("/sites", scode, {"code": scode, "name": sname, "city": city,
                                       "countryCode": "FR", "timeZoneId": TZ})
        STATE[f"site:{scode}"] = sid
        for bcode, bname, floors in buildings:
            s, b = api("POST", f"/sites/{sid}/buildings", {"code": bcode, "name": bname})
            bid = b["publicId"] if s in (200, 201) else None
            if bid is None:
                s2, b2 = api("GET", f"/sites/{sid}/buildings", params={"q": bcode, "size": 50})
                bid = next((x["publicId"] for x in (b2 or {}).get("content", []) if x.get("code") == bcode), None)
            for floor_label, rooms in floors:
                for rcode in rooms:
                    s, b = api("POST", f"/sites/{sid}/rooms", {
                        "code": rcode, "name": f"Salle {rcode}", "buildingPublicId": bid,
                        "capacity": 30, "floorLabel": floor_label})
                    if s in (200, 201):
                        STATE["rooms"][rcode] = b["publicId"]
                    elif s == 409:
                        s2, b2 = api("GET", f"/sites/{sid}/rooms", params={"q": rcode, "size": 50})
                        STATE["rooms"][rcode] = next(
                            (x["publicId"] for x in (b2 or {}).get("content", []) if x.get("code") == rcode), None)
                    else:
                        fail(f"salle {rcode}", s, b)
        # plage réseau (best effort — exige SUPER_ADMIN ; on ignore un refus)
        s, b = api("POST", f"/sites/{sid}/network-ranges", {"cidr": cidr, "label": f"Réseau {sname}"})
        print(f"  {scode}: {sum(len(r) for _, _, fl in buildings for _, r in fl)} salles"
              + ("" if s in (200, 201) else f" (plage réseau non créée: HTTP {s})"))


def phase_alt():
    print("== rythmes d'alternance ==")
    STATE["rhythms"] = {}
    for code, name, rtype, cycle, config in RHYTHMS:
        body = {"code": code, "name": name, "type": rtype, "configuration": config}
        if cycle:
            body["cycleLengthWeeks"] = cycle
        STATE["rhythms"][code] = ensure("/alternation/patterns", code, body)
    for ccode, meta in STATE["classes"].items():
        s, b = api("POST", "/alternation/class-assignments", {
            "classGroupPublicId": meta["id"],
            "workStudyPatternPublicId": STATE["rhythms"][meta["rhythm"]],
            "cycleStartDate": CYCLE_ANCHOR.isoformat(),
            "validFrom": CYCLE_ANCHOR.isoformat()})
        if s not in (200, 201) and s != 409:
            fail(f"affectation rythme {ccode}", s, b)
    print(f"  {len(RHYTHMS)} rythmes, {len(STATE['classes'])} classes affectées")


def phase_mgr():
    print("== responsable pédagogique ==")
    rid, _ = find_user("responsable@example.test")
    if not rid:
        fail("compte responsable@example.test introuvable")
    n = 0
    for pcode, *_ in PROGRAMS:
        s, b = api("POST", "/pedagogical-assignments", {
            "programPublicId": STATE[pcode], "userPublicId": rid,
            "type": "PRIMARY_MANAGER", "reason": "Démonstration — lot complémentaire"})
        if s in (200, 201):
            n += 1
        elif s != 409:
            fail(f"affectation responsable {pcode}", s, b)
    print(f"  responsable affecté à {n} formation(s) (409 = déjà fait pour les autres)")


def phase_teachers():
    print("== formateurs (18, actifs via invitation + activation Mailpit) ==")
    STATE["teachers"] = {}  # class_code -> {"am": pid, "pm": pid}
    tp = os.environ.get("ESIC_DEMO_TEACHER_PASSWORD", PASSWORD)
    for ccode in STATE["classes"]:
        pair = {}
        for slot in ("am", "pm"):
            email = f"prof.{ccode.lower().replace('-', '')}.{slot}@example.test"
            first = {"am": "Formateur", "pm": "Formatrice"}[slot]
            last = ccode.replace("-", " ").title() + (" Matin" if slot == "am" else " Après-midi")
            pair[slot] = ensure_active_account(email, first, last, "TEACHER", tp)
        STATE["teachers"][ccode] = pair
        print(f"  {ccode}: {pair['am'][:8]} / {pair['pm'][:8]}")


def _student_csv(ccode, pcode, n, work_study_ratio=0.7):
    rows = ["last_name,first_name,email,formation_code,class_code,academic_year,"
            "student_number,birth_date,work_study,company_name"]
    for i in range(n):
        ln = LAST_NAMES[(i * 7 + stable_hash(ccode)) % len(LAST_NAMES)]
        fn = FIRST_NAMES[(i * 5 + stable_hash(ccode) // 7) % len(FIRST_NAMES)]
        num = f"ESIC-2026-{stable_hash(ccode) % 900 + 100}{i:02d}"
        email = (ascii_fold(f"{fn}.{ln}").lower().replace(" ", "")
                 + f".{ccode.lower().replace('-', '')}{i:02d}@example.test")
        ws = "true" if (i / max(n, 1)) < work_study_ratio else "false"
        comp = COMPANIES[i % len(COMPANIES)] if ws == "true" else ""
        birth = f"{2005 - (0 if 'BTS' in pcode else 2)}-{(i % 12) + 1:02d}-{(i % 27) + 1:02d}"
        rows.append(f"{ln},{fn},{email},{pcode},{ccode},{YEAR_CODE},{num},{birth},{ws},{comp}")
    return ("\n".join(rows) + "\n").encode()


def _run_student_import(filename, content, expect_confirmable=True, confirm=True):
    s, job = api_multipart("/student-imports", filename, content)
    if s not in (200, 201):
        fail(f"simulation import {filename}", s, job)
    jid = job["publicId"]
    s, job = api("GET", f"/student-imports/{jid}")
    sm = job.get("summary") or {}
    globals_ = [i.get("code") for i in (job.get("issues") or [])]
    summary = {"status": job.get("status"), "confirmable": job.get("confirmable"),
               "total": sm.get("total"), "valid": sm.get("valid"), "warning": sm.get("warning"),
               "error": sm.get("error"), "blocking": sm.get("blocking"),
               "plannedCreate": sm.get("plannedCreate"), "globalIssues": globals_}
    if confirm and expect_confirmable:
        if not job.get("confirmable"):
            fail(f"import {filename} non confirmable alors qu'attendu confirmable", body=summary)
        s, res = api("POST", f"/student-imports/{jid}/confirm")
        if s not in (200, 201):
            fail(f"confirmation import {filename}", s, res)
        return jid, summary, res
    return jid, summary, None


def phase_students():
    print("== apprenants (import CSV réel, restent PENDING) ==")
    counts = {"BTS-SIO-1": 26, "BTS-SIO-2": 22, "BTS-CIEL-1": 24, "BTS-CIEL-2": 20,
              "BACH-CDA": 28, "MAST-ESIS-1": 18, "MAST-ESIS-2": 16,
              "MAST-CPDIA-1": 20, "MAST-CPDIA-2": 15}
    total = 0
    for ccode, meta in STATE["classes"].items():
        n = counts[ccode]
        csv = _student_csv(ccode, meta["program"], n)
        jid, summary, res = _run_student_import(f"apprenants-{ccode}.csv", csv)
        applied = (res or {}).get("appliedSummary") or {}
        created = applied.get("created", summary.get("plannedCreate"))
        total += n
        print(f"  {ccode}: {n} lignes -> {summary['status']} "
              f"(valides {summary['valid']}, créés {created})")
    print(f"  total apprenants importés : {total}")


def _planning_csv(ccode, meta):
    tz = TZ
    am = STATE["teachers"][ccode]["am"]
    pm = STATE["teachers"][ccode]["pm"]
    room = CLASS_ROOM[ccode]
    subs = SUBJECTS[ccode]
    rows = ["slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code"]
    idx = 0
    for d in daterange(PLAN_START, PLAN_END):
        if not is_school_day(meta["rhythm"], d):
            continue
        friday = d.weekday() == 4
        # matin
        rows.append(f"{ccode}-{d.isoformat()}-AM,{d.isoformat()},09:00,12:30,{tz},"
                    f"{subs[idx % len(subs)]},{am},{room}")
        idx += 1
        # après-midi (vendredi : fin 16:00)
        end_pm = "16:00" if friday else "17:00"
        rows.append(f"{ccode}-{d.isoformat()}-PM,{d.isoformat()},13:30,{end_pm},{tz},"
                    f"{subs[idx % len(subs)]},{pm},{room}")
        idx += 1
    return ("\n".join(rows) + "\n").encode(), (len(rows) - 1)


def phase_planning():
    print("== planning (import CSV + publication, sans conflit) ==")
    STATE["sessions"] = {}
    for ccode, meta in STATE["classes"].items():
        csv, nrows = _planning_csv(ccode, meta)
        s, job = api_multipart("/planning-imports", f"planning-{ccode}.csv", csv,
                               params={"classGroupPublicId": meta["id"]})
        if s not in (200, 201):
            fail(f"simulation planning {ccode}", s, job)
        jid = job["publicId"]
        s, job = api("GET", f"/planning-imports/{jid}")
        blocking = job.get("blockingRows", job.get("blockingCount", 0))
        conflicts = job.get("conflictRows", 0)
        if blocking:
            fail(f"planning {ccode} : {blocking} ligne(s) bloquante(s)", body=job)
        s, pub = api("POST", f"/planning-imports/{jid}/publish")
        if s not in (200, 201):
            fail(f"publication planning {ccode}", s, pub)
        created = pub.get("createdSessions", pub.get("sessionCount", "?"))
        STATE["sessions"][ccode] = pub
        print(f"  {ccode}: {nrows} créneaux, bloquants={blocking}, conflits={conflicts} -> "
              f"publié v{pub.get('version', '?')} ({created} séances)")


def phase_backdate():
    print("== recul des inscriptions à " + ENROLL_START.isoformat() + " ==")
    lot = set(STATE["classes"])
    enr = get_all("/enrollments", status="ACTIVE")
    moved = 0
    for e in enr:
        if e.get("classGroupCode") not in lot:
            continue
        if (e.get("startDate") or "9999") <= ENROLL_START.isoformat():
            continue
        eid = e["publicId"]
        cg = e["classGroupPublicId"]
        prof = e["studentProfilePublicId"]
        # clôture puis recréation avec une date de début reculée
        s, _ = api("POST", f"/enrollments/{eid}/close",
                   {"status": "WITHDRAWN", "reason": "Recalage démo — date d'entrée réelle"})
        if s not in (200, 204):
            continue
        s, _ = api("POST", "/enrollments", {"studentProfilePublicId": prof,
                                            "classGroupPublicId": cg,
                                            "startDate": ENROLL_START.isoformat()})
        if s in (200, 201):
            moved += 1
    print(f"  {moved} inscription(s) reculée(s)")


def phase_attendance():
    print("== scénarios d'assiduité (séances passées) ==")
    all_sessions = get_all("/sessions", sort="startsAt,asc")
    all_enr = get_all("/enrollments", status="ACTIVE")
    by_class_sessions = {}
    for x in all_sessions:
        for c in x.get("classes", []):
            by_class_sessions.setdefault(c["code"], []).append(x)
    by_class_enr = {}
    for e in all_enr:
        by_class_enr.setdefault(e.get("classGroupCode"), []).append(e["publicId"])
    lo, hi = ATT_FROM.isoformat(), ATT_TO.isoformat()
    # Ménage : des exécutions antérieures ont pu laisser des séances
    # ouvertes hors fenêtre — on les referme pour un état propre.
    stale = [x for x in all_sessions
             if x.get("status") == "OPEN" and x.get("startsAt", "")[:10] < lo]
    for x in stale:
        api("POST", f"/sessions/{x['publicId']}/close")
    if stale:
        print(f"  ({len(stale)} séance(s) résiduelle(s) hors fenêtre refermées)")
    grand = 0
    for ccode in STATE["classes"]:
        past = sorted([x for x in by_class_sessions.get(ccode, [])
                       if lo <= x.get("startsAt", "")[:10] < hi],
                      key=lambda x: x["startsAt"])[:8]
        enrolments = by_class_enr.get(ccode, [])
        done = records = 0
        first_err = None
        for sess in past:
            sid = sess["publicId"]
            st, ob = api("POST", f"/sessions/{sid}/open")
            if st not in (200, 201, 204, 409):
                first_err = first_err or f"open {st} {ob}"
                continue
            morning = sess.get("startsAt", "T09").split("T")[1][:2] < "12"
            types = (["MORNING_ARRIVAL", "MORNING_BREAK_RETURN"] if morning
                     else ["AFTERNOON_ARRIVAL", "AFTERNOON_BREAK_RETURN"])
            cps = []
            for t in types:
                sc, cb = api("POST", f"/sessions/{sid}/checkpoints",
                             {"label": t.replace("_", " ").title(), "type": t})
                if sc in (200, 201):
                    cpid = cb["publicId"]
                    api("POST", f"/sessions/{sid}/checkpoints/{cpid}/open")
                    cps.append(cpid)
                elif sc == 409:  # déjà créé lors d'un run précédent
                    lc, lb = api("GET", f"/sessions/{sid}/checkpoints")
                    for c in (lb or []):
                        if c.get("type") == t and c.get("status") != "CANCELLED":
                            cps.append(c["publicId"])
                            if c.get("status") == "PLANNED":
                                api("POST", f"/sessions/{sid}/checkpoints/{c['publicId']}/open")
            for j, enr in enumerate(enrolments):
                r = (j * 7 + (stable_hash(sid) % 97)) % 100
                status = "PRESENT" if r < 80 else ("LATE" if r < 92 else "ABSENT")
                for cpid in cps:
                    body = {"enrollmentPublicId": enr, "checkpointPublicId": cpid,
                            "status": status, "comment": "Saisie de démonstration"}
                    if status == "LATE":
                        body["lateMinutes"] = 20
                    sc, mb = api("POST", f"/sessions/{sid}/attendance/manual", body)
                    if sc in (200, 201):
                        records += 1
                    elif first_err is None:
                        first_err = f"manual {sc} {mb.get('code') if isinstance(mb, dict) else mb}"
            api("POST", f"/sessions/{sid}/close")
            done += 1
        grand += records
        note = f" [{first_err}]" if first_err and records == 0 else ""
        print(f"  {ccode}: {done} séances émargées, {records} présences saisies "
              f"({len(enrolments)} inscrits){note}")
    print(f"  total présences saisies : {grand}")
    # État propre : aucune séance ne doit rester OUVERTE dans l'instantané.
    left = [x["publicId"] for x in get_all("/sessions", status="OPEN")]
    for sid in left:
        api("POST", f"/sessions/{sid}/close")
    if left:
        print(f"  {len(left)} séance(s) encore ouverte(s) refermée(s)")


def phase_fixtures():
    print("== jeux de fichiers d'import (écrits + importés pour vérification) ==")
    os.makedirs(DEMO_DATA_DIR, exist_ok=True)
    pcode, ccode = "BTS-SIO", "BTS-SIO-1"
    hdr = ("last_name,first_name,email,formation_code,class_code,academic_year,"
           "student_number,birth_date,work_study,company_name")

    valid = (hdr + "\n"
             + f"Test,Alice,fx.alice@example.test,{pcode},{ccode},{YEAR_CODE},FX-001,2005-03-04,true,Atos\n"
             + f"Test,Bruno,fx.bruno@example.test,{pcode},{ccode},{YEAR_CODE},FX-002,2005-06-11,false,\n")
    warning = (hdr + ",level_code\n"
               + f"Test,Chloé,fx.chloe@example.test,{pcode},{ccode},{YEAR_CODE},FX-003,2005-01-02,true,Capgemini,SIO1\n")
    blocking = (hdr + "\n"
                + f"Test,Dora,fx.dora@example.test,{pcode},{ccode},{YEAR_CODE},FX-004,2005-01-02,true,Atos\n"
                + f"Test,Elias,fx.elias@example.test,INCONNU,{ccode},{YEAR_CODE},FX-005,2005-01-02,false,\n")
    multi = (hdr + "\n"
             + f"Test,,fx.frank@example.test,{pcode},{ccode},{YEAR_CODE},FX-006,not-a-date,maybe,\n"
             + f",Gina,pas-un-email,{pcode},ZZZ,{YEAR_CODE},FX-007,2005-01-02,true,\n")
    dup = (hdr + "\n"
           + f"Test,Hugo,fx.hugo@example.test,{pcode},{ccode},{YEAR_CODE},FX-008,2005-01-02,true,Atos\n"
           + f"Test,Hugo,fx.hugo@example.test,{pcode},{ccode},{YEAR_CODE},FX-008,2005-01-02,true,Atos\n")

    # (contenu, confirmer après simulation ?) — on ne confirme que le
    # fichier valide, pour ne pas polluer la base ; les autres sont
    # simulés et leur verdict est consigné.
    files = {"import-apprenants-valide.csv": (valid, True),
             "import-apprenants-avertissement.csv": (warning, False),
             "import-apprenants-bloquant.csv": (blocking, False),
             "import-apprenants-multi-anomalies.csv": (multi, False),
             "import-apprenants-doublons.csv": (dup, False)}
    report = ["# Jeux de fichiers d'import apprenants — vérification réelle\n",
              f"Générés par `scripts/seed-demo-full.py` (phase `fixtures`) et **réellement "
              f"importés** (simulation) le {dt.date.today().isoformat()} sur la base "
              f"`esic_connect_demo`. Seul le fichier valide est confirmé ; les autres sont "
              f"laissés en simulation.\n",
              "Colonnes du modèle : `last_name, first_name, email, formation_code, class_code, "
              "academic_year` (obligatoires) + `phone, student_number, birth_date, work_study, "
              "company_name` (optionnelles). `level_code`, `promotion_code`, `work_study_pattern` "
              "sont **ignorées avec un avertissement** (`IMP_COLUMN_IGNORED`).\n",
              "| Fichier | Attendu | `status` / confirmable | total/valides/erreurs/bloquantes/avert. | anomalies globales |",
              "|---|---|---|---|---|"]
    for name, (content, do_confirm) in files.items():
        path = os.path.join(DEMO_DATA_DIR, name)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(content)
        jid, summary, res = _run_student_import(name, content.encode(),
                                                expect_confirmable=do_confirm, confirm=do_confirm)
        exp = {"import-apprenants-valide.csv": "confirmable, 2 créations",
               "import-apprenants-avertissement.csv": "confirmable + avert. colonne ignorée",
               "import-apprenants-bloquant.csv": "1 ligne en erreur (formation inconnue) → non confirmable",
               "import-apprenants-multi-anomalies.csv": "plusieurs anomalies (nom, e-mail, date, classe)",
               "import-apprenants-doublons.csv": "doublon intra-fichier signalé (avert.)"}[name]
        verdict = "confirmable" if summary["confirmable"] else "NON confirmable"
        gi = ", ".join(sorted(set(summary["globalIssues"]))) or "—"
        row = (f"| `{name}` | {exp} | {summary['status']} / {verdict} | "
               f"{summary['total']}/{summary['valid']}/{summary['error']}/"
               f"{summary['blocking']}/{summary['warning']} | {gi} |")
        report.append(row)
        print(f"  {name}: {summary['status']} {verdict} "
              f"(t={summary['total']} ok={summary['valid']} err={summary['error']} "
              f"block={summary['blocking']} warn={summary['warning']}) globals=[{gi}]")
    with open(os.path.join(DEMO_DATA_DIR, "IMPORT-FIXTURES-REPORT.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(report) + "\n")
    print("  rapport -> docs/demo-data/IMPORT-FIXTURES-REPORT.md")


def _total(path, **params):
    s, b = api("GET", path, params={"size": 1, **params})
    b = b or {}
    for k in ("totalElements", "totalCount", "total"):
        if k in b:
            return b[k]
    return len(b.get("content", []))


def phase_check():
    print("== contrôle final ==")
    from collections import Counter
    s, b = api("GET", "/users", params={"role": "TEACHER", "size": 500})
    tstat = Counter(u["status"] for u in (b or {}).get("content", [])
                    if u.get("email", "").startswith("prof."))
    lot_classes = {cc for _, _, _, _, cl in PROGRAMS for cc, *_ in cl}
    s, b = api("GET", "/class-groups", params={"size": 200})
    classes = [c for c in (b or {}).get("content", []) if c.get("code") in lot_classes]
    print(f"  formations               : {_total('/programs')}")
    print(f"  classes du lot           : {len(classes)} / 9")
    print(f"  rythmes d'alternance     : {_total('/alternation/patterns')}")
    mlk = STATE.get("site:MALAKOFF")
    par = STATE.get("site:PARIS")
    print(f"  salles                   : "
          f"{_total('/sites/' + mlk + '/rooms') if mlk else '?'} Malakoff + "
          f"{_total('/sites/' + par + '/rooms') if par else '?'} Paris")
    print(f"  formateurs prof.*        : {dict(tstat)}")
    print(f"  apprenants (rôle STUDENT): {_total('/users', role='STUDENT')}")
    print(f"  inscriptions ACTIVE      : {_total('/enrollments', status='ACTIVE')}")
    print(f"  séances totales          : {_total('/sessions')}")
    for st in ("PLANNED", "OPEN", "CLOSED", "CANCELLED"):
        print(f"      {st:10}: {_total('/sessions', status=st)}")
    s, b = api("GET", "/planning/versions", params={"size": 300})
    vers = (b or {}).get("content", [])
    per_class = Counter(v.get("classCode") or v.get("classGroupCode") for v in vers)
    print(f"  versions de planning     : {len(vers)} ({len([c for c in per_class if c])} classes)")


def phase_pfixtures():
    print("== jeux de fichiers d'import PLANNING (écrits + simulés) ==")
    os.makedirs(DEMO_DATA_DIR, exist_ok=True)
    ccode = "BTS-SIO-1"
    meta = STATE["classes"].get(ccode)
    if not meta or ccode not in STATE.get("teachers", {}):
        fail("phase pfixtures : lancer d'abord ref/teachers")
    am = STATE["teachers"][ccode]["am"]
    pm = STATE["teachers"][ccode]["pm"]
    room = CLASS_ROOM[ccode]
    hdr = "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code"

    valide = (hdr + "\n"
              + f"FXP-1,2027-01-11,09:00,12:30,{TZ},Atelier démo 1,{am},{room}\n"
              + f"FXP-2,2027-01-11,13:30,17:00,{TZ},Atelier démo 2,{pm},{room}\n"
              + f"FXP-3,2027-01-12,09:00,12:30,{TZ},Atelier démo 3,{am},{room}\n")
    # 07:00 -> hors plage habituelle 08:00-19:00 (AVERTISSEMENT non bloquant)
    avert = (hdr + "\n"
             + f"FXP-A1,2027-01-13,07:00,12:30,{TZ},Créneau très matinal,{am},{room}\n"
             + f"FXP-A2,2027-01-13,13:30,22:30,{TZ},Créneau très long,{pm},{room}\n")
    # teacher_public_id inconnu -> PLAN_TEACHER_NOT_ELIGIBLE (bloquant) ; date invalide
    bloquant = (hdr + "\n"
                + f"FXP-B1,2027-01-14,09:00,12:30,{TZ},Formateur inconnu,00000000-0000-0000-0000-000000000000,{room}\n"
                + f"FXP-B2,pas-une-date,09:00,12:30,{TZ},Date illisible,{am},{room}\n")
    # conflit intra-fichier : même salle, même créneau + slot_key dupliqué
    conflits = (hdr + "\n"
                + f"FXP-C1,2027-01-15,09:00,12:30,{TZ},Cours A,{am},{room}\n"
                + f"FXP-C1,2027-01-15,09:00,12:30,{TZ},Cours B (slot_key dupliqué),{pm},{room}\n"
                + f"FXP-C3,2027-01-15,09:30,12:30,{TZ},Cours C (chevauchement salle),{pm},{room}\n")

    files = {"planning-valide.csv": (valide, "3 créneaux valides, publiable"),
             "planning-avertissement.csv": (avert, "hors plage horaire + durée inhabituelle (avert.)"),
             "planning-bloquant.csv": (bloquant, "formateur inéligible + date illisible → non publiable"),
             "planning-conflits.csv": (conflits, "slot_key dupliqué + chevauchement de salle")}
    rep = ["# Jeux de fichiers d'import PLANNING — vérification réelle\n",
           f"Générés par `scripts/seed-demo-full.py` (phase `pfixtures`) et **réellement simulés** "
           f"le {dt.date.today().isoformat()} sur `esic_connect_demo`, classe cible `BTS-SIO-1`. "
           f"Aucun n'est publié.\n",
           "Colonnes : `slot_key, session_date, start_time, end_time, time_zone_id, title, "
           "teacher_public_id` (obligatoires) + `room_code`. Une seule classe et une seule année "
           "par import (portées par la requête, pas les lignes).\n",
           "| Fichier | Attendu | total/valides/avert./erreurs / confirmable | anomalies (codes) |",
           "|---|---|---|---|"]
    for name, (content, exp) in files.items():
        with open(os.path.join(DEMO_DATA_DIR, name), "w", encoding="utf-8") as fh:
            fh.write(content)
        s, job = api_multipart("/planning-imports", name, content.encode(),
                               params={"classGroupPublicId": meta["id"]})
        if s not in (200, 201):
            fail(f"simulation planning {name}", s, job)
        jid = job["publicId"]
        s, rows = api("GET", f"/planning-imports/{jid}/rows", params={"size": 50})
        codes = sorted({i.get("errorCode") for r in (rows or {}).get("content", [])
                        for i in r.get("issues", []) if i.get("errorCode")})
        s, j = api("GET", f"/planning-imports/{jid}")
        conf = "confirmable" if j.get("confirmable") else "NON confirmable"
        counts = f"{j.get('totalRows')}/{j.get('validRows')}/{j.get('warningRows')}/{j.get('errorRows')}"
        allc = ", ".join(codes) or "—"
        rep.append(f"| `{name}` | {exp} | {counts} / {conf} | {allc} |")
        print(f"  {name}: {counts} {conf} anomalies=[{allc}]")
    with open(os.path.join(DEMO_DATA_DIR, "PLANNING-FIXTURES-REPORT.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(rep) + "\n")
    print("  rapport -> docs/demo-data/PLANNING-FIXTURES-REPORT.md")


PHASES = [("ref", phase_ref), ("sites", phase_sites), ("alt", phase_alt), ("mgr", phase_mgr),
          ("teachers", phase_teachers), ("students", phase_students),
          ("backdate", phase_backdate),
          ("planning", phase_planning), ("attendance", phase_attendance),
          ("fixtures", phase_fixtures), ("pfixtures", phase_pfixtures), ("check", phase_check)]


def main():
    wanted = sys.argv[1:] or [name for name, _ in PHASES]
    login_admin()
    print(f"ADMIN authentifié sur {API}")
    load_state()
    # sites d'abord si ref est demandé (classes ont besoin du site)
    order = [p for p in PHASES if p[0] in wanted]
    if any(n == "ref" for n, _ in order) and not any(n == "sites" for n, _ in order):
        order = [("sites", phase_sites)] + order
    if any(n == "ref" for n, _ in order) and any(n == "sites" for n, _ in order):
        order = ([("sites", phase_sites)]
                 + [p for p in order if p[0] != "sites"])
    for name, fn in order:
        fn()
    print("\nAmorçage complémentaire terminé.")


if __name__ == "__main__":
    main()
