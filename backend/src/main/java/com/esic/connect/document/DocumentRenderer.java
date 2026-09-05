package com.esic.connect.document;

/**
 * Port de production des documents de restitution.
 *
 * <p>Les trois formats partagent le même contenu ({@link TabularDocument})
 * afin qu'un export CSV, un classeur et un PDF d'un même rapport ne
 * puissent pas diverger. Seul le PDF porte une
 * {@link DocumentIdentity} : c'est le seul des trois qui est un
 * <em>document</em> destiné à être imprimé, transmis et opposé.
 */
public interface DocumentRenderer {

    /** CSV UTF-8 avec BOM, séparateur {@code ;}, injection de formule neutralisée. */
    byte[] toCsv(TabularDocument document);

    /** Classeur {@code .xlsx} d'une feuille, injection de formule neutralisée. */
    byte[] toSpreadsheet(TabularDocument document);

    /** PDF paginé portant l'identité visuelle et l'identité du document. */
    byte[] toPdf(TabularDocument document, DocumentIdentity identity);

    /** Type MIME du classeur Excel produit par {@link #toSpreadsheet}. */
    String SPREADSHEET_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
}
