package com.esic.connect.attendance;

import java.io.InputStream;

/**
 * Port de stockage du <strong>contenu</strong> des pièces jointes de
 * justificatifs (bloc G1-E ; DEC-G1-008). Le métier ne dépend jamais de
 * {@code java.nio.file} : il ne connaît que ce port. L'implémentation G1
 * ({@code LocalFilesystemJustificationFileStorage}) écrit hors webroot ;
 * un déploiement futur peut substituer un adaptateur objet
 * (S3-compatible) sans toucher au métier.
 *
 * <p>Le port n'a aucune notion de justificatif, d'apprenant ni de
 * périmètre : il déplace / lit / supprime des octets sous une
 * <strong>clé opaque</strong> qu'il génère lui-même, jamais dérivée d'un
 * nom fourni par le client.
 */
public interface JustificationFileStorage {

    /**
     * Génère une nouvelle clé opaque de stockage — aléatoire, dispersée,
     * <strong>jamais</strong> dérivée d'un nom fourni par le client. La
     * clé est destinée à être <em>persistée en base au statut
     * {@code PENDING_STORAGE}</em> (DEC-G1-009 étape 3) <strong>avant</strong>
     * l'appel à {@link #store(String, PendingUpload)} (étape 5 :
     * « stocker avec la storage_key persistée »).
     */
    String newStorageKey();

    /**
     * Persiste un fichier <strong>déjà validé</strong> (extension, type,
     * magic bytes, taille) <strong>sous la clé fournie</strong> et renvoie
     * la taille et l'empreinte réellement écrites. L'écriture passe par un
     * fichier temporaire puis un déplacement atomique ; l'empreinte
     * SHA-256 et la taille sont calculées pendant l'écriture. Une cible
     * déjà présente n'est <strong>jamais écrasée</strong> (échec
     * {@code IO_ERROR}). En cas d'échec, aucun fichier partiel ne
     * subsiste.
     *
     * @param storageKey clé opaque obtenue via {@link #newStorageKey()} et
     *                   déjà persistée (statut {@code PENDING_STORAGE})
     * @param upload     contenu à stocker (le flux est entièrement consommé)
     * @return la clé (écho), la taille et l'empreinte réellement écrites
     * @throws JustificationFileStorageException si l'écriture échoue, si la
     *         taille dépasse la limite pendant le flux, si le contenu est
     *         vide, ou si la clé est déjà utilisée
     */
    StoredRef store(String storageKey, PendingUpload upload);

    /**
     * Ouvre le contenu d'une pièce pour téléchargement.
     *
     * @param storageKey clé opaque renvoyée par {@link #store(PendingUpload)}
     * @return un flux à fermer par l'appelant
     * @throws JustificationFileStorageException si la clé est inconnue ou illisible
     */
    InputStream open(String storageKey);

    /**
     * Supprime définitivement le contenu d'une pièce (compensation d'un
     * rollback, purge, suppression logique). Idempotent : une clé déjà
     * absente n'est pas une erreur.
     */
    void delete(String storageKey);

    /**
     * Énumère les clés effectivement présentes dans le stockage
     * (EF-JUS-002 — balayage des fichiers orphelins, docs/02 §19.5 :
     * « Un balayage périodique détecte et supprime les fichiers
     * orphelins »).
     *
     * <p>Un orphelin est un contenu qu'aucune ligne ne référence plus :
     * il survit à une suppression dont la partie fichier a échoué. Sans
     * balayage, il resterait indéfiniment — une donnée personnelle
     * conservée sans base légale ni moyen de la retrouver.
     *
     * <p>Le résultat est <strong>borné</strong> par {@code limit} : un
     * stockage de production peut contenir des centaines de milliers
     * d'objets, et une énumération intégrale en mémoire serait un défaut
     * en soi. L'ordre n'est pas garanti.
     *
     * @param limit nombre maximal de clés renvoyées, strictement positif
     * @return des clés existantes, au plus {@code limit}
     */
    java.util.List<String> listKeys(int limit);

    /**
     * Fichier candidat au stockage — déjà validé par l'appelant.
     *
     * @param content      flux du contenu (consommé une seule fois)
     * @param maxSizeBytes taille maximale tolérée pendant la lecture du flux
     */
    record PendingUpload(InputStream content, long maxSizeBytes) {
    }

    /**
     * Référence d'un fichier effectivement écrit.
     *
     * @param storageKey clé opaque de relecture / suppression
     * @param sizeBytes  taille réellement écrite (octets)
     * @param sha256     empreinte hexadécimale minuscule du contenu écrit
     */
    record StoredRef(String storageKey, long sizeBytes, String sha256) {
    }
}
