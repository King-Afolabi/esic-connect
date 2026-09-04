package com.esic.connect.claim.internal;

import java.util.List;

/**
 * Réclamation complète : en-tête, fil de messages et historique des
 * décisions. Les deux listes sont séparées parce qu'une décision n'est
 * pas de la conversation (RG-088).
 */
record ClaimThreadResponse(
        ClaimResponse claim,
        List<ClaimResponses.MessageView> messages,
        List<ClaimResponses.EventView> events) {
}
