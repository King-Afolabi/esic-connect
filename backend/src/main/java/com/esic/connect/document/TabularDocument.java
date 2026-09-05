package com.esic.connect.document;

import java.util.List;

/**
 * Contenu d'un document de restitution, déjà rendu en texte par le
 * module métier appelant.
 *
 * <p>Le rendu textuel est délibérément fait <em>avant</em> d'arriver
 * ici : formater un taux, une date ou un statut relève du métier, et le
 * module {@code document} ne doit pas avoir à connaître
 * {@code AttendanceStatus} pour savoir l'écrire.
 *
 * @param title    titre du document (« Rapport de classe », …)
 * @param period   période couverte, déjà libellée ({@code null} si sans objet)
 * @param facts    couples libellé/valeur affichés en tête (synthèse)
 * @param headers  en-têtes de colonnes
 * @param rows     lignes ; chaque ligne doit avoir la taille de {@code headers}
 * @param notes    mentions honnêtes (limites, périmètre, absence de donnée)
 */
public record TabularDocument(
        String title,
        String period,
        List<Fact> facts,
        List<String> headers,
        List<List<String>> rows,
        List<String> notes) {

    public TabularDocument {
        title = title == null ? "" : title;
        facts = facts == null ? List.of() : List.copyOf(facts);
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** Document tabulaire simple, sans synthèse ni mention. */
    public static TabularDocument of(String title, String period,
                                     List<String> headers, List<List<String>> rows) {
        return new TabularDocument(title, period, List.of(), headers, rows, List.of());
    }

    /** Couple libellé / valeur d'une synthèse. */
    public record Fact(String label, String value) {
    }
}
