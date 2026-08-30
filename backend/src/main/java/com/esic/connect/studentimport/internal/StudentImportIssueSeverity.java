package com.esic.connect.studentimport.internal;

/**
 * Gravité d'une anomalie d'import, globale ({@link StudentImportJobIssue})
 * ou portée par une ligne ({@link StudentImportRowIssue}) — niveaux de
 * docs/01-cadrage.md §8.2 / docs/02-cahier-des-charges.md §10.8.
 *
 * <p>{@code BLOCKING} interdit la simulation d'aboutir à un job
 * confirmable ; {@code ERROR} bloque la confirmation ligne par ligne ;
 * {@code WARNING} / {@code INFO} sont informatifs.
 */
enum StudentImportIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}
