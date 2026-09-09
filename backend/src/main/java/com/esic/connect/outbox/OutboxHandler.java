package com.esic.connect.outbox;

import java.util.Map;

/**
 * Point d'extension du diffuseur : le module qui sait produire un effet
 * de bord publie un bean implémentant cette interface (docs/02 §25.1).
 *
 * <p>Le module {@code outbox} route sur {@link #messageType()} et ne
 * connaît rien d'autre du gestionnaire — c'est ce qui lui permet de ne
 * dépendre d'aucun module métier.
 *
 * <p><strong>Contrat de rejouabilité</strong> : {@link #handle} peut être
 * appelée plusieurs fois pour la même ligne — reprise après un échec
 * partiel, rejeu manuel depuis la file d'échec (EF-OPS-005), arrêt de la
 * JVM entre l'effet et l'enregistrement du succès. Le gestionnaire doit
 * donc être <strong>idempotent</strong> : produire deux fois le même
 * effet ne doit pas produire deux résultats.
 *
 * <p><strong>Contrat d'erreur</strong> : une exception signale un échec
 * <em>replanifiable</em> — le diffuseur réessaiera avec une attente
 * croissante, puis placera la ligne en file d'échec. Un gestionnaire qui
 * constate qu'un effet n'a plus lieu d'être (ressource supprimée,
 * destinataire archivé) doit rendre la main <strong>sans</strong>
 * exception : réessayer indéfiniment un effet devenu sans objet ne le
 * rendrait pas possible.
 */
public interface OutboxHandler {

    /**
     * @return le type de message traité par ce gestionnaire ; doit être
     *         unique dans l'application (un démarrage avec deux
     *         gestionnaires du même type échoue).
     */
    String messageType();

    /**
     * Produit l'effet de bord décrit par le message.
     *
     * @param messageKey clé d'idempotence stable de la ligne (SHA-256
     *                   hexadécimal). Identique à chaque tentative de la
     *                   même ligne, différente d'une ligne à l'autre.
     * @param payload contenu enregistré par
     *                {@link OutboxPublisher#enqueue}, relu depuis le JSON.
     *                Les nombres reviennent typés par Jackson (entier ->
     *                {@code Integer}/{@code Long}) : le gestionnaire doit
     *                les lire sans supposer la classe exacte.
     * @throws RuntimeException pour signaler un échec replanifiable
     */
    void handle(String messageKey, Map<String, Object> payload);

    /**
     * Le diffuseur doit-il ouvrir une transaction neuve autour de
     * {@link #handle} ? {@code true} par défaut, ce qui convient à tout
     * gestionnaire dont l'effet est une écriture en base.
     *
     * <p><strong>Pourquoi ce réglage existe.</strong> Le drain immédiat a
     * lieu dans le {@code afterCommit} de la transaction métier. À cet
     * instant, ses ressources sont encore liées au fil d'exécution alors
     * que la transaction sous-jacente est déjà committée : un
     * {@code @Transactional} ordinaire y « participerait » à une
     * transaction morte et échouerait sur
     * <em>« no transaction is in progress »</em>. Seule une transaction
     * <em>neuve</em>, qui suspend la précédente, fonctionne — c'est ce que
     * le diffuseur fait pour vous.
     *
     * <p>Renvoyer {@code false} n'a de sens que pour un gestionnaire dont
     * l'effet principal est un appel <strong>externe</strong> — courriel,
     * poussée, publication MQTT. Tenir une connexion de base ouverte
     * pendant l'attente d'un serveur distant épuiserait le pool sur un
     * incident réseau. Un tel gestionnaire doit alors porter lui-même des
     * transactions {@code REQUIRES_NEW} courtes autour de ses écritures.
     */
    default boolean transactional() {
        return true;
    }
}
