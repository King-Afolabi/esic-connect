/**
 * Module « search » — recherche globale dans le périmètre de l'appelant
 * (EF-USER-009 ; docs/02 §22.7).
 *
 * <p>Ce module <strong>ne détient aucune donnée</strong> et n'a ni table
 * ni migration. Il interroge les ports publics des modules qui
 * possèdent l'information — {@code enrollment}, {@code identity},
 * {@code academic}, {@code organization}, {@code coursesession} — et
 * assemble leurs réponses en une liste unique. C'est chaque module qui
 * écrit sa propre requête : lui seul sait ce qu'est un code de classe ou
 * un numéro étudiant.
 *
 * <p><strong>Le périmètre est résolu ici, une fois, côté serveur</strong>
 * ({@code AcademicScopeDirectory}) et transmis à chaque port : un
 * responsable pédagogique ne trouve que ses classes, ses apprenants et
 * ses séances. Aucun filtre ne vient d'un paramètre d'appel.
 *
 * <p>Aucune adresse électronique n'est cherchée ni renvoyée : la
 * rechercher permettrait de confirmer l'existence d'un compte à partir
 * d'une adresse devinée — une énumération déguisée en recherche.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Recherche globale")
package com.esic.connect.search;
