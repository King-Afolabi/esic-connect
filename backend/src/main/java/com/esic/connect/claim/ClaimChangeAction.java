package com.esic.connect.claim;

/** Actions d'une réclamation portées à la piste d'audit (docs/02 §23.1). */
public enum ClaimChangeAction {
    CREATED,
    MESSAGE_POSTED,
    TRANSFERRED,
    STATUS_CHANGED,
    REOPENED
}
