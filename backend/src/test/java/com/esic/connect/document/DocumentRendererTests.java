package com.esic.connect.document;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module {@code document} — CSV, classeur {@code .xlsx} et PDF
 * (EF-REP-003, EF-REP-004, EF-REP-005 ; AC-032, AC-033).
 *
 * <p>Le point vérifié en priorité est la <strong>neutralisation de
 * l'injection de formule dans les deux formats tabulaires</strong> : le
 * vecteur est le tableur qui ouvre le fichier, pas son extension. Un
 * export Excel qui laisserait passer {@code =cmd|…} serait exactement
 * aussi dangereux qu'un CSV qui le laisserait passer.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentRendererTests {

    @Autowired
    private DocumentRenderer renderer;

    private static final List<String> HEADERS = List.of("Nom", "Valeur");

    private static TabularDocument documentWith(List<List<String>> rows) {
        return new TabularDocument("Rapport de contrôle", "Du 01/09/2026 au 30/09/2026",
                List.of(new TabularDocument.Fact("Lignes", Integer.toString(rows.size()))),
                HEADERS, rows, List.of("Une mention honnête."));
    }

    // --- CSV (AC-032) --------------------------------------------------

    @Test
    void csvStartsWithABomAndUsesSemicolonsWithTheHeaderOnLineOne() {
        String csv = new String(renderer.toCsv(documentWith(List.of(List.of("Dupont", "12")))),
                StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        // Aucun préambule : tout ce qui précède l'en-tête casse les
        // analyseurs qui supposent que la ligne 1 décrit les colonnes.
        assertThat(csv.split("\r\n", 2)[0]).isEqualTo("﻿Nom;Valeur");
        assertThat(csv).contains("Dupont;12\r\n");
        assertThat(csv).doesNotContain("Rapport de contrôle");
        assertThat(csv).doesNotContain("Une mention honnête.");
    }

    @Test
    void csvNeutralisesEveryFormulaPrefix() {
        List<List<String>> rows = new ArrayList<>();
        for (String dangerous : List.of("=cmd|'/c calc'!A1", "+1+1", "-1+1", "@SUM(A1)")) {
            rows.add(List.of(dangerous, "x"));
        }
        String csv = new String(renderer.toCsv(documentWith(rows)), StandardCharsets.UTF_8);
        // Chaque cellule dangereuse est préfixée d'une apostrophe ; aucune
        // ne commence encore par un caractère de formule.
        for (String line : csv.split("\r\n")) {
            assertThat(line).doesNotStartWith("=").doesNotStartWith("+")
                    .doesNotStartWith("-").doesNotStartWith("@");
        }
        assertThat(csv).contains("'=cmd|'/c calc'!A1");
    }

    @Test
    void csvQuotesCellsContainingTheSeparatorOrQuotes() {
        String csv = new String(
                renderer.toCsv(documentWith(List.of(List.of("Dupont; Jean", "il a dit \"non\"")))),
                StandardCharsets.UTF_8);
        assertThat(csv).contains("\"Dupont; Jean\"");
        assertThat(csv).contains("\"il a dit \"\"non\"\"\"");
    }

    // --- Classeur (EF-REP-004, AC-032) ---------------------------------

    @Test
    void spreadsheetIsAReadableXlsxWithHeaderAndRows() throws Exception {
        byte[] xlsx = renderer.toSpreadsheet(documentWith(List.of(
                List.of("Dupont", "12"), List.of("Martin", "8"))));
        // Signature ZIP : un `.xlsx` est une archive, pas un OLE2.
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> flattened = flatten(sheet);
            assertThat(flattened).contains("Rapport de contrôle", "Nom", "Valeur", "Dupont", "12", "Martin");
        }
    }

    @Test
    void spreadsheetNeutralisesFormulaInjectionToo() throws Exception {
        byte[] xlsx = renderer.toSpreadsheet(documentWith(List.of(List.of("=1+1", "@SUM(A1)"))));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> flattened = flatten(sheet);
            assertThat(flattened).contains("'=1+1", "'@SUM(A1)");
            assertThat(flattened).doesNotContain("=1+1", "@SUM(A1)");
            // Aucune cellule n'est de type FORMULA : tout est écrit en texte.
            for (Row row : sheet) {
                for (Cell cell : row) {
                    assertThat(cell.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);
                }
            }
        }
    }

    @Test
    void spreadsheetSheetNameStaysWithinExcelLimits() throws Exception {
        String longTitle = "Rapport d'assiduité mensuel des classes de première année section A";
        byte[] xlsx = renderer.toSpreadsheet(TabularDocument.of(longTitle, null, HEADERS,
                List.of(List.of("a", "b"))));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getSheetName(0).length()).isLessThanOrEqualTo(31);
        }
    }

    // --- PDF (EF-REP-005, AC-033) --------------------------------------

    @Test
    void pdfCarriesTheDocumentIdentityOnEveryPage() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        // Assez de lignes pour forcer plusieurs pages.
        for (int i = 0; i < 120; i++) {
            rows.add(List.of("Apprenant " + i, Integer.toString(i)));
        }
        DocumentIdentity identity = new DocumentIdentity("ESIC-ATT-2026-ABCDEFGHJK",
                "ESIC — établissement de test", "Mme Dupont", Instant.parse("2026-09-05T10:15:30Z"));
        byte[] pdf = renderer.toPdf(documentWith(rows), identity);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                assertThat(text)
                        .as("identité du document présente page " + page)
                        .contains("ESIC-ATT-2026-ABCDEFGHJK");
                assertThat(text).as("mention électronique page " + page)
                        .contains("Document électronique");
            }
            String whole = new PDFTextStripper().getText(document);
            assertThat(whole).contains("Rapport de contrôle").contains("Mme Dupont");
        }
    }

    @Test
    void pdfRendersFrenchAccentsAndSurvivesUnsupportedCharacters() throws Exception {
        DocumentIdentity identity = new DocumentIdentity("DOC-1", "ESIC", "Système", Instant.now());
        // Un idéogramme n'existe pas dans WinAnsi : il ne doit pas faire
        // échouer la génération, seulement être remplacé.
        byte[] pdf = renderer.toPdf(
                TabularDocument.of("Assiduité — élève à l'école", null, HEADERS,
                        List.of(List.of("Noël", "漢字"))),
                identity);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Assiduité").contains("élève").contains("Noël");
        }
    }

    private static List<String> flatten(Sheet sheet) {
        List<String> values = new ArrayList<>();
        for (Row row : sheet) {
            for (Cell cell : row) {
                values.add(cell.getStringCellValue());
            }
        }
        return values;
    }
}
