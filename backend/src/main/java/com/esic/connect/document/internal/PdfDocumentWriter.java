package com.esic.connect.document.internal;

import com.esic.connect.document.DocumentIdentity;
import com.esic.connect.document.TabularDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Production d'un PDF paginé portant l'identité visuelle de l'ESIC
 * (EF-REP-005, EF-REP-006 ; docs/02 §22.4).
 *
 * <p><strong>Identité visuelle.</strong> Le dépôt ne contient
 * <em>aucun fichier de logo</em> : l'en-tête est donc une signature
 * typographique — bandeau, nom de l'établissement, nom du produit — et
 * non une image. Dessiner un logo inventé serait pire que de ne pas en
 * mettre. L'insertion d'un logo fourni par l'établissement se réduit à
 * un {@code PDImageXObject} dans {@link #header}.
 *
 * <p><strong>Encodage.</strong> Les polices Standard 14 utilisent
 * WinAnsi (CP1252) : un caractère hors de ce jeu — une flèche, un
 * idéogramme collé dans un nom — ferait échouer l'écriture en cours de
 * page et perdrait tout le document. Chaque chaîne est donc réduite au
 * jeu représentable ({@link #encodable}), les caractères typographiques
 * courants étant d'abord repliés sur leur équivalent ASCII.
 *
 * <p>Chaque page porte l'identifiant du document, l'émetteur et la
 * mention de document électronique (AC-033) : une page détachée du reste
 * reste rattachable.
 */
final class PdfDocumentWriter {

    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private static final float MARGIN = 36f;
    private static final float BAND_HEIGHT = 46f;
    private static final float TITLE_SIZE = 15f;
    private static final float BODY_SIZE = 8.5f;
    private static final float FOOT_SIZE = 7f;
    private static final float LINE = 12f;
    /** Au-delà de six colonnes, le portrait tasse les libellés jusqu'à l'illisible. */
    private static final int LANDSCAPE_THRESHOLD = 6;

    private static final float[] BRAND = {0.07f, 0.20f, 0.38f};
    private static final float[] BAND_TEXT = {1f, 1f, 1f};
    private static final float[] HEADER_FILL = {0.90f, 0.92f, 0.95f};
    private static final float[] RULE = {0.72f, 0.75f, 0.79f};

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    private PdfDocumentWriter() {
    }

    static byte[] write(TabularDocument document, DocumentIdentity identity, ZoneId zone) {
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDRectangle size = document.headers().size() > LANDSCAPE_THRESHOLD
                    ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                    : PDRectangle.A4;

            Layout layout = new Layout(pdf, size, document, identity, zone);
            layout.open();
            layout.intro();
            layout.table();
            layout.notes();
            layout.close();

            pdf.save(out);
            return out.toByteArray();
        } catch (IOException cannotWrite) {
            // Écriture en mémoire : une IOException signale un défaut de la
            // bibliothèque, pas une condition métier récupérable.
            throw new UncheckedIOException(cannotWrite);
        }
    }

    /** État de mise en page : la position courante et la page ouverte. */
    private static final class Layout {

        private final PDDocument pdf;
        private final PDRectangle size;
        private final TabularDocument document;
        private final DocumentIdentity identity;
        private final ZoneId zone;
        private final float[] columnWidths;

        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        Layout(PDDocument pdf, PDRectangle size, TabularDocument document,
               DocumentIdentity identity, ZoneId zone) {
            this.pdf = pdf;
            this.size = size;
            this.document = document;
            this.identity = identity;
            this.zone = zone;
            this.columnWidths = widths(size, document);
        }

        void open() throws IOException {
            newPage();
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(size);
            pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            pageNumber++;
            header();
            footer();
            y = size.getHeight() - BAND_HEIGHT - 24f;
        }

        /** Bandeau d'identité visuelle et titre du document. */
        private void header() throws IOException {
            float top = size.getHeight() - BAND_HEIGHT;
            stream.setNonStrokingColor(BRAND[0], BRAND[1], BRAND[2]);
            stream.addRect(0, top, size.getWidth(), BAND_HEIGHT);
            stream.fill();

            stream.setNonStrokingColor(BAND_TEXT[0], BAND_TEXT[1], BAND_TEXT[2]);
            text(BOLD, 16f, MARGIN, top + 25f, "ESIC");
            text(REGULAR, 9f, MARGIN + 40f, top + 26f, "CONNECT");
            text(REGULAR, 8f, MARGIN, top + 11f, identity.issuer());

            String right = "Document " + identity.documentId();
            float width = width(REGULAR, 8f, right);
            text(REGULAR, 8f, size.getWidth() - MARGIN - width, top + 26f, right);
            String when = "Généré le " + ZonedDateTime.ofInstant(identity.generatedAt(), zone).format(STAMP);
            float whenWidth = width(REGULAR, 8f, when);
            text(REGULAR, 8f, size.getWidth() - MARGIN - whenWidth, top + 11f, when);
            stream.setNonStrokingColor(0f, 0f, 0f);
        }

        /** Pied de page : mention de document électronique, auteur, pagination. */
        private void footer() throws IOException {
            stream.setStrokingColor(RULE[0], RULE[1], RULE[2]);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, MARGIN + 22f);
            stream.lineTo(size.getWidth() - MARGIN, MARGIN + 22f);
            stream.stroke();
            stream.setNonStrokingColor(0.35f, 0.35f, 0.35f);
            text(ITALIC, FOOT_SIZE, MARGIN, MARGIN + 12f, DocumentIdentity.ELECTRONIC_NOTICE);
            text(REGULAR, FOOT_SIZE, MARGIN, MARGIN + 2f,
                    "Émis par " + identity.issuer() + " — établi par " + identity.authorDisplay()
                            + " — identifiant " + identity.documentId());
            String page = "Page " + pageNumber;
            text(REGULAR, FOOT_SIZE, size.getWidth() - MARGIN - width(REGULAR, FOOT_SIZE, page),
                    MARGIN + 2f, page);
            stream.setNonStrokingColor(0f, 0f, 0f);
        }

        /** Titre, période et faits de synthèse. */
        void intro() throws IOException {
            ensure(3 * LINE);
            text(BOLD, TITLE_SIZE, MARGIN, y, document.title());
            y -= LINE + 4f;
            if (document.period() != null && !document.period().isBlank()) {
                text(REGULAR, 9.5f, MARGIN, y, document.period());
                y -= LINE;
            }
            y -= 4f;
            for (TabularDocument.Fact fact : document.facts()) {
                ensure(LINE);
                text(BOLD, 9f, MARGIN, y, fact.label() + " : ");
                text(REGULAR, 9f, MARGIN + width(BOLD, 9f, fact.label() + " : "), y, fact.value());
                y -= LINE;
            }
            if (!document.facts().isEmpty()) {
                y -= 6f;
            }
        }

        void table() throws IOException {
            if (document.headers().isEmpty()) {
                return;
            }
            headerRow();
            for (List<String> row : document.rows()) {
                if (y < MARGIN + 40f) {
                    newPage();
                    headerRow();
                }
                float x = MARGIN;
                for (int c = 0; c < document.headers().size(); c++) {
                    String cell = c < row.size() ? row.get(c) : "";
                    text(REGULAR, BODY_SIZE, x + 3f, y, clip(REGULAR, BODY_SIZE, cell, columnWidths[c] - 6f));
                    x += columnWidths[c];
                }
                y -= LINE;
            }
        }

        private void headerRow() throws IOException {
            ensure(2 * LINE);
            stream.setNonStrokingColor(HEADER_FILL[0], HEADER_FILL[1], HEADER_FILL[2]);
            stream.addRect(MARGIN, y - 3f, size.getWidth() - 2 * MARGIN, LINE + 1f);
            stream.fill();
            stream.setNonStrokingColor(0f, 0f, 0f);
            float x = MARGIN;
            for (int c = 0; c < document.headers().size(); c++) {
                text(BOLD, BODY_SIZE, x + 3f, y,
                        clip(BOLD, BODY_SIZE, document.headers().get(c), columnWidths[c] - 6f));
                x += columnWidths[c];
            }
            y -= LINE + 3f;
        }

        void notes() throws IOException {
            if (document.notes().isEmpty()) {
                return;
            }
            y -= 8f;
            ensure(2 * LINE);
            text(BOLD, 8.5f, MARGIN, y, "Mentions");
            y -= LINE;
            float usable = size.getWidth() - 2 * MARGIN;
            for (String note : document.notes()) {
                for (String line : wrap(REGULAR, 8f, note, usable)) {
                    ensure(LINE);
                    text(REGULAR, 8f, MARGIN, y, line);
                    y -= LINE;
                }
            }
        }

        private void ensure(float needed) throws IOException {
            if (y - needed < MARGIN + 34f) {
                newPage();
            }
        }

        private void text(PDFont font, float fontSize, float x, float baseline, String value)
                throws IOException {
            String safe = encodable(value);
            if (safe.isEmpty()) {
                return;
            }
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, baseline);
            stream.showText(safe);
            stream.endText();
        }
    }

    // ------------------------------------------------------------------

    /**
     * Largeur des colonnes, proportionnelle au contenu observé et bornée
     * pour qu'aucune colonne n'écrase les autres.
     */
    private static float[] widths(PDRectangle size, TabularDocument document) {
        int columns = document.headers().size();
        float[] widths = new float[columns];
        if (columns == 0) {
            return widths;
        }
        float usable = size.getWidth() - 2 * MARGIN;
        float[] weights = new float[columns];
        float total = 0f;
        for (int c = 0; c < columns; c++) {
            int longest = document.headers().get(c).length();
            int sampled = 0;
            for (List<String> row : document.rows()) {
                if (sampled++ >= 200) {
                    break;
                }
                if (c < row.size() && row.get(c) != null) {
                    longest = Math.max(longest, row.get(c).length());
                }
            }
            weights[c] = Math.min(34, Math.max(6, longest));
            total += weights[c];
        }
        for (int c = 0; c < columns; c++) {
            widths[c] = usable * weights[c] / total;
        }
        return widths;
    }

    private static float width(PDFont font, float fontSize, String value) {
        try {
            return font.getStringWidth(encodable(value)) / 1000f * fontSize;
        } catch (IOException unmeasurable) {
            return value == null ? 0f : value.length() * fontSize * 0.5f;
        }
    }

    /** Tronque avec une ellipse pour tenir dans {@code maxWidth}. */
    private static String clip(PDFont font, float fontSize, String value, float maxWidth) {
        String safe = encodable(value);
        if (safe.isEmpty() || width(font, fontSize, safe) <= maxWidth) {
            return safe;
        }
        String candidate = safe;
        while (!candidate.isEmpty() && width(font, fontSize, candidate + "...") > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? "" : candidate + "...";
    }

    private static List<String> wrap(PDFont font, float fontSize, String value, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String safe = encodable(value);
        StringBuilder current = new StringBuilder();
        for (String word : safe.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(font, fontSize, candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /**
     * Réduit une chaîne au jeu représentable par les polices Standard 14
     * (WinAnsi / CP1252). Les caractères typographiques usuels sont
     * repliés sur un équivalent ; le reste est remplacé par {@code ?}
     * plutôt que de faire échouer la génération du document entier.
     */
    static String encodable(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String folded = value
                .replace(' ', ' ')
                .replace('‑', '-')
                .replace('−', '-')
                .replace("…", "...")
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace('“', '"')
                .replace('”', '"');
        StringBuilder sb = new StringBuilder(folded.length());
        for (int i = 0; i < folded.length(); i++) {
            char ch = folded.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ');
            } else if (WIN_ANSI.newEncoder().canEncode(ch)) {
                sb.append(ch);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}
