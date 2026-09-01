package com.esic.connect.coursesession;

/**
 * Cycle de vie d'une séance (docs/02-cahier-des-charges.md §15.2).
 *
 * <pre>{@code PLANNED  →  OPEN  →  CLOSED}
 * {@code PLANNED / OPEN  →  CANCELLED}   (bloc G1-C)}</pre>
 *
 * <ul>
 *   <li>{@link #PLANNED} : séance créée, émargement indisponible ;</li>
 *   <li>{@link #OPEN} : point de contrôle ouvert, émargement possible ;</li>
 *   <li>{@link #CLOSED} : terminal — plus aucun émargement, jetons Redis
 *       invalidés. Pas de réouverture ;</li>
 *   <li>{@link #CANCELLED} : terminal (G1-C) — annulation avec motif d'une
 *       séance {@code PLANNED} ou {@code OPEN}. Jetons Redis purgés,
 *       points de contrôle annulés, aucune présence ni absence dérivée.
 *       Une séance {@code CLOSED} n'est jamais annulée.</li>
 * </ul>
 */
public enum SessionLifecycle {
    PLANNED,
    OPEN,
    CLOSED,
    CANCELLED
}
