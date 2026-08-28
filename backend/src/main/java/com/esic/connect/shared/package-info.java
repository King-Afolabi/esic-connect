/**
 * Module « shared » (docs/03-architecture.md §7.16) : noyau technique
 * transverse — entité de base ({@code BaseEntity}), format d'erreur et
 * gestion commune des exceptions ({@code shared.web}), configuration de
 * sécurité et d'audit JPA.
 *
 * Déclaré {@link org.springframework.modulith.ApplicationModule.Type#OPEN}
 * car ses types sont, par nature, consommés directement par les autres
 * modules (ex. {@code ApiError} pour des réponses d'erreur homogènes).
 * Il ne doit contenir aucune règle métier (voir §7.16).
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared")
package com.esic.connect.shared;
