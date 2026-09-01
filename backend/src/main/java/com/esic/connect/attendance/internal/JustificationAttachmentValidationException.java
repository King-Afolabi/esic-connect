package com.esic.connect.attendance.internal;

/**
 * Rejet d'un fichier de pièce jointe <em>avant</em> toute écriture (bloc
 * G1-E ; DEC-G1-009 étape 2). Aucun message ne contient de nom de fichier
 * client, de chemin ni de donnée personnelle.
 */
class JustificationAttachmentValidationException extends RuntimeException {

    enum Kind {
        /** Fichier vide. */
        EMPTY,
        /** Taille au-delà de la limite. */
        TOO_LARGE,
        /** Extension hors liste blanche ({@code .pdf} / {@code .jpg} / {@code .jpeg} / {@code .png}). */
        EXTENSION_NOT_ALLOWED,
        /** {@code Content-Type} déclaré hors liste tolérée. */
        DECLARED_TYPE_NOT_ALLOWED,
        /** Signature binaire (magic bytes) non reconnue comme PDF / JPEG / PNG. */
        CONTENT_NOT_RECOGNISED,
        /** Extension et contenu réel incohérents (ex. {@code .png} contenant un PDF). */
        EXTENSION_CONTENT_MISMATCH,
        /** Archive / conteneur OLE2 détecté (polyglotte potentiel). */
        ARCHIVE_REJECTED
    }

    private final Kind kind;

    JustificationAttachmentValidationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
