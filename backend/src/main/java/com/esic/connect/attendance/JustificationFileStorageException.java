package com.esic.connect.attendance;

/**
 * Échec technique du port {@link JustificationFileStorage} (bloc G1-E).
 * Le message ne contient jamais de chemin de fichier, de nom client ni de
 * donnée personnelle — seulement une cause générique.
 */
public class JustificationFileStorageException extends RuntimeException {

    /** Nature de l'échec, pour la traduction en réponse HTTP par le module. */
    public enum Kind {
        /** Contenu vide. */
        EMPTY,
        /** La taille a dépassé la limite pendant la lecture du flux. */
        TOO_LARGE,
        /** Clé de stockage inconnue. */
        NOT_FOUND,
        /** Écriture / lecture / déplacement impossible (E/S). */
        IO_ERROR
    }

    private final transient Kind kind;

    public JustificationFileStorageException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public JustificationFileStorageException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
