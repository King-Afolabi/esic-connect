package com.esic.connect.academic;

/** Action du cycle de vie d'une ressource académique, tracée par l'audit (cahier §30.1). */
public enum AcademicChangeAction {
    CREATED,
    UPDATED,
    ARCHIVED,
    RESTORED,
    CLOSED
}
