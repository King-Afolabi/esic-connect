/**
 * Module « notification » (docs/03-architecture.md §7.11).
 *
 * Réagit aux événements métier d'autres modules pour produire des
 * notifications — ici l'email d'activation de compte via un serveur SMTP
 * (Mailpit en développement). Ne porte aucune règle métier et ne
 * référence aucune classe interne d'un autre module : il ne consomme que
 * les événements publiés dans l'API du module {@code identity}.
 *
 * Dette technique assumée : l'envoi est synchrone après commit, sans file
 * persistante ni reprise garantie (docs/03-architecture.md §18.2/§18.3,
 * cahier §23.3).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package com.esic.connect.notification;
