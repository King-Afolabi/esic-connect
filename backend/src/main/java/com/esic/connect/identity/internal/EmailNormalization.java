package com.esic.connect.identity.internal;

import java.util.Locale;

/** Normalisation minimale d'une adresse électronique avant recherche. */
final class EmailNormalization {

    private EmailNormalization() {
    }

    static String normalize(String rawEmail) {
        return rawEmail == null ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
