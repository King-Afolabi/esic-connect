/**
 * Module « identité et accès » (docs/03-architecture.md §7.1).
 * Gère les comptes utilisateurs, les rôles et leurs affectations.
 * Les types d'implémentation résident dans {@code identity.internal} et ne
 * sont pas accessibles depuis les autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.esic.connect.identity;
