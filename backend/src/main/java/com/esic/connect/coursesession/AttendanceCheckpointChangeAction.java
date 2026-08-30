package com.esic.connect.coursesession;

/**
 * Action du cycle de vie d'un point de contrôle d'émargement (V10),
 * tracée par l'audit (cahier §30.1) et écoutée par le module
 * {@code attendance} ({@link #CLOSED} / {@link #CANCELLED} déclenchent la
 * purge du jeton Redis du point de contrôle).
 */
public enum AttendanceCheckpointChangeAction {
    CREATED,
    OPENED,
    CLOSED,
    CANCELLED
}
