package com.esic.connect.organization;

/** Action du cycle de vie d'une ressource organisationnelle, tracée par l'audit (cahier §30.1). */
public enum OrganizationChangeAction {
    CREATED,
    UPDATED,
    ARCHIVED,
    RESTORED,
    ACTIVATED,
    DEACTIVATED
}
