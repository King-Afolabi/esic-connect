/**
 * Module « integration » — intégrations externes encapsulées derrière des
 * ports (docs/02 §28 ; EF-INT-001, EF-INT-002, EF-INT-003).
 *
 * <p>Deux capacités y vivent, pour la même raison : elles exposent le
 * planning de l'établissement <em>hors</em> d'ESIC Connect.
 *
 * <ul>
 *   <li><strong>Flux iCalendar</strong> (EF-INT-001, AC-034) — un
 *       abonnement par personne, signé, révocable, consultable depuis
 *       n'importe quel agenda. Réellement livré : il ne dépend d'aucun
 *       service tiers.</li>
 *   <li><strong>Microsoft Graph</strong> (EF-INT-002, EF-INT-003) — port
 *       de création de réunion Teams et d'écriture de calendrier. Sans
 *       identifiants de client configurés, l'adaptateur inactif répond
 *       et le produit <strong>déclare</strong> qu'aucune intégration
 *       n'est active. Il n'en simule pas une.</li>
 * </ul>
 *
 * <p>« Le produit fonctionne intégralement sans elle, avec un adaptateur
 * local » (§28.1) : aucun module métier ne dépend de ce module pour
 * fonctionner ; c'est lui qui lit leurs ports publics.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Intégrations externes")
package com.esic.connect.integration;
