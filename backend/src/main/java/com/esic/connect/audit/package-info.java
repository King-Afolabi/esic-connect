/**
 * Module « audit » (docs/03-architecture.md §7.13).
 * Conserve la piste d'audit des opérations sensibles. Ne référence aucune
 * classe interne d'un autre module (voir {@code AuditEvent.actorUserId}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit")
package com.esic.connect.audit;
