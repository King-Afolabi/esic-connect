package com.esic.connect.outbox;

import java.util.Map;

/**
 * Port d'écriture de la file transactionnelle des effets de bord
 * (docs/02 §25.1 ; RG-096, RG-097).
 *
 * <p><strong>Contrat d'appel</strong> : cette méthode doit être appelée
 * <em>à l'intérieur</em> de la transaction métier. La ligne d'outbox
 * commite alors avec le métier — ou disparaît avec lui si la transaction
 * est annulée (AC-027). Appelée hors transaction, elle écrit dans sa
 * propre transaction : l'effet est alors « au mieux », et l'appelant doit
 * savoir qu'il perd la garantie d'atomicité.
 *
 * <p>Le module appelant ne produit <strong>aucun</strong> effet de bord
 * lui-même : il décrit ce qu'il faut faire, et un
 * {@link OutboxHandler} du module compétent le fera après commit.
 */
public interface OutboxPublisher {

    /**
     * Enregistre un effet de bord à produire.
     *
     * @param messageType  type de message routant vers le
     *                     {@link OutboxHandler} compétent ; doit
     *                     correspondre à {@link OutboxHandler#messageType()}
     * @param dedupKey     clé stable d'<strong>occurrence</strong> — deux
     *                     livraisons du même événement métier doivent
     *                     produire la même clé, afin qu'une seule ligne
     *                     existe. Hachée en SHA-256 par l'implémentation :
     *                     l'appelant fournit une chaîne lisible, jamais un
     *                     secret.
     * @param payload      description de l'effet. <strong>Jamais</strong>
     *                     de jeton, de mot de passe, d'adresse en clair ni
     *                     d'adresse IP (RG-094) : uniquement des
     *                     identifiants publics et des libellés neutres.
     *                     Les valeurs doivent être sérialisables en JSON.
     * @return {@code true} si une ligne a été créée, {@code false} si la
     *         même occurrence était déjà enregistrée (idempotence)
     */
    boolean enqueue(String messageType, String dedupKey, Map<String, Object> payload);
}
