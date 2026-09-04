/**
 * Module « réclamation » (EF-CLAIM-001 à 004 ; docs/02 §20).
 *
 * <p>Une réclamation est un <strong>échange conversationnel encadré</strong>
 * entre un apprenant et un acteur compétent — formateur, responsable
 * pédagogique, administration scolaire. Le cahier est explicite sur ce
 * qu'elle n'est pas : « ce n'est pas une messagerie instantanée
 * générale » (§20.1). D'où un fil rattaché à un objet précis, une
 * audience déterminée par le destinataire fonctionnel, et un cycle de vie
 * fermé.
 *
 * <p>Le module ne référence aucune classe interne d'un autre module : les
 * séances, inscriptions et comptes sont résolus par les ports publics
 * ({@code coursesession}, {@code enrollment}, {@code identity}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Réclamation")
package com.esic.connect.claim;
