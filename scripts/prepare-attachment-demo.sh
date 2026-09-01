#!/usr/bin/env bash
# Génère des fichiers de pièce jointe de justificatif DÉMONSTRABLES et
# FICTIFS (bloc G1-E) dans un répertoire de sortie NON VERSIONNÉ
# (par défaut `build/demo-data/`). Aucun contenu réel, aucun secret,
# aucun fichier suivi par Git n'est créé. Idempotent.
#
# Usage :
#   ./scripts/prepare-attachment-demo.sh
#   OUT_DIR=/tmp/x ./scripts/prepare-attachment-demo.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/build/demo-data}"
mkdir -p "$OUT_DIR"

# PDF minimal valide (magic bytes %PDF-, un objet, trailer).
cat > "$OUT_DIR/justificatif-demo.pdf" <<'PDF'
%PDF-1.4
1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj
2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj
3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>endobj
xref
0 4
0000000000 65535 f
trailer<< /Root 1 0 R /Size 4 >>
startxref
0
%%EOF
PDF

# PNG 1x1 transparent (magic bytes 89 50 4E 47 ...), en base64.
base64 --decode > "$OUT_DIR/justificatif-demo.png" <<'PNG'
iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==
PNG

# JPEG minimal (magic bytes FF D8 FF), en base64.
base64 --decode > "$OUT_DIR/justificatif-demo.jpg" <<'JPG'
/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=
JPG

echo "Fichiers de démonstration G1-E générés (fictifs) dans : $OUT_DIR"
ls -l "$OUT_DIR"/justificatif-demo.* 2>/dev/null || true
