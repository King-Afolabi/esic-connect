package com.esic.connect.coursesession;

/**
 * Action du cycle de vie d'une séance, tracée par l'audit (cahier §30.1)
 * et écoutée par le module {@code attendance} ({@link #CLOSED} déclenche
 * la purge des jetons Redis de la séance).
 */
public enum CourseSessionChangeAction {
    CREATED,
    OPENED,
    CLOSED
}
