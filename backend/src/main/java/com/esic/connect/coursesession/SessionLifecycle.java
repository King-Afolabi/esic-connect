package com.esic.connect.coursesession;

/**
 * Cycle de vie d'une séance (docs/02-cahier-des-charges.md §15.2, réduit à
 * cette tranche).
 *
 * <pre>{@code PLANNED  →  OPEN  →  CLOSED}</pre>
 *
 * <ul>
 *   <li>{@link #PLANNED} : séance créée, émargement indisponible ;</li>
 *   <li>{@link #OPEN} : point de contrôle ouvert, émargement possible ;</li>
 *   <li>{@link #CLOSED} : terminal — plus aucun émargement, jetons Redis
 *       invalidés. Pas de réouverture.</li>
 * </ul>
 */
public enum SessionLifecycle {
    PLANNED,
    OPEN,
    CLOSED
}
