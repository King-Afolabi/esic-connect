package com.esic.connect.document.internal;

import com.esic.connect.document.DocumentIdentity;
import com.esic.connect.document.DocumentRenderer;
import com.esic.connect.document.TabularDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Adaptateur unique du port {@link DocumentRenderer} : CSV artisanal,
 * classeur via Apache POI, PDF via Apache PDFBox.
 *
 * <p>Le fuseau d'affichage des horodatages est celui de l'horloge
 * applicative : un document daté « 5 septembre 23:40 UTC » alors qu'il a
 * été produit le 6 septembre à Paris est une source de litige inutile.
 */
@Component
class DefaultDocumentRenderer implements DocumentRenderer {

    private final Clock clock;

    DefaultDocumentRenderer(Clock clock) {
        this.clock = clock;
    }

    @Override
    public byte[] toCsv(TabularDocument document) {
        return CsvDocumentWriter.write(document).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] toSpreadsheet(TabularDocument document) {
        return SpreadsheetDocumentWriter.write(document);
    }

    @Override
    public byte[] toPdf(TabularDocument document, DocumentIdentity identity) {
        return PdfDocumentWriter.write(document, identity, clock.getZone());
    }
}
