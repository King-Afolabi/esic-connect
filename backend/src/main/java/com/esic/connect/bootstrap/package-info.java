/**
 * Module « bootstrap » — amorçage de démonstration, actif
 * <strong>uniquement sous le profil {@code demo}</strong>.
 *
 * <p>{@link com.esic.connect.bootstrap.DemoDataInitializer} crée, de façon
 * idempotente, les comptes fictifs nécessaires à la démonstration locale
 * (1 {@code ADMIN}, 1 {@code TEACHER}, 2 {@code STUDENT}) via le port
 * public {@link com.esic.connect.identity.DemoAccountProvisioner}. Le
 * reste du jeu de données (référentiel académique, profils, inscriptions,
 * séance PLANNED) est produit par {@code scripts/seed-demo.sh} en
 * appelant les API REST réelles avec ce compte {@code ADMIN}.
 *
 * <p>Aucune donnée de démonstration n'est insérée par une migration
 * Flyway. Aucun secret durable : le mot de passe de démonstration
 * provient de la variable d'environnement {@code ESIC_DEMO_PASSWORD}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Bootstrap")
package com.esic.connect.bootstrap;
