/**
 * Module « outbox » — file transactionnelle des effets de bord
 * (docs/02 §25 ; EF-AUD-003, EF-OPS-005 ; RG-096, RG-097).
 *
 * <p>Un effet de bord externe — trace d'audit, notification, courriel,
 * poussée — ne doit exister <strong>que si</strong> la transaction métier
 * qui l'a motivé a effectivement committé, et ne doit
 * <strong>jamais</strong> être perdu parce que le diffuseur a échoué.
 * Ces deux garanties sont contradictoires tant que l'effet est produit
 * directement : soit on l'écrit trop tôt (et une annulation le laisse
 * derrière elle), soit on l'écrit trop tard (et une panne le perd).
 *
 * <p>Ce module résout la contradiction en écrivant l'<em>intention</em>
 * dans la même transaction que le métier ({@link
 * com.esic.connect.outbox.OutboxPublisher}), puis en la traitant après
 * commit — immédiatement, et à défaut lors d'une reprise planifiée.
 *
 * <p><strong>Ce module ne connaît aucun métier.</strong> Il route un
 * {@code messageType} vers le {@link com.esic.connect.outbox.OutboxHandler}
 * que le module concerné publie comme bean. Il ne dépend donc d'aucun
 * autre module ; ce sont les autres qui dépendent de lui.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Outbox")
package com.esic.connect.outbox;
