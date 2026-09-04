package com.esic.connect.attendance;

/**
 * Avis du formateur sur un départ anticipé transmis au responsable
 * pédagogique (EF-ATT-013 ; docs/02 §16.13 — « recommande favorablement
 * ou transmet »).
 *
 * <p>Un avis n'est pas une décision : il éclaire celle du responsable et
 * n'engage rien. L'absence d'avis ({@code null} en base) est un cas
 * légitime — le formateur transmet sans se prononcer.
 */
public enum EarlyDepartureOpinion {
    FAVOURABLE,
    UNFAVOURABLE
}
