package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttachmentMalwareScanner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Analyse contre un <strong>{@code clamd} réel</strong> (dettes T-04 et
 * T-11 ; EF-JUS-002).
 *
 * <p><strong>Pourquoi ce test est conditionnel, et ce que cela signifie.</strong>
 * Il exige un démon ClamAV joignable — le service {@code clamav} du profil
 * optionnel de {@code compose.yaml}. Sans lui, il est
 * <strong>ignoré</strong>, et un test ignoré n'est pas un test réussi :
 * tant qu'il n'a pas tourné, l'analyse antivirus n'est pas démontrée, et
 * {@code docs/CURRENT-STATE.md} doit le dire.
 *
 * <p>Le contraire — un test qui passerait en l'absence d'analyseur —
 * serait pire que pas de test du tout : il ferait croire à une protection
 * qui n'existe pas.
 *
 * <pre>
 * docker compose --profile antivirus up -d clamav
 * docker exec esic-connect-clamav clamdscan --ping 1   # attendre « PONG »
 * cd backend &amp;&amp; ESIC_CLAMAV_REAL=1 ./mvnw test -Dtest=ClamAvRealDaemonIntegrationTests
 * </pre>
 */
class ClamAvRealDaemonIntegrationTests {

    /**
     * Chaîne d'essai standard EICAR, reconnue par tout antivirus
     * conforme. Elle est <strong>inoffensive</strong> — c'est une simple
     * suite de caractères imprimables — et existe précisément pour
     * vérifier qu'un analyseur fonctionne sans manipuler de code
     * malveillant. Scindée ici pour ne pas déclencher l'antivirus du
     * poste qui lit ce dépôt.
     */
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR"
            + "-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private static final String HOST = System.getenv().getOrDefault("ANTIVIRUS_HOST", "127.0.0.1");
    private static final int PORT =
            Integer.parseInt(System.getenv().getOrDefault("ANTIVIRUS_PORT", "3310"));
    /** Un vrai clamd peut mettre plusieurs secondes à répondre au premier appel. */
    private static final int TIMEOUT_MILLIS = 30_000;

    @BeforeAll
    static void requireRealDaemon() {
        assumeTrue("1".equals(System.getenv("ESIC_CLAMAV_REAL")),
                "clamd reel non demande (exporter ESIC_CLAMAV_REAL=1) — analyse antivirus NON demontree");
        assumeTrue(reachable(), "clamd injoignable sur " + HOST + ':' + PORT
                + " — demarrer `docker compose --profile antivirus up -d clamav`");
    }

    private final AttachmentMalwareScanner scanner =
            new ClamAvMalwareScanner(HOST, PORT, TIMEOUT_MILLIS);

    @Test
    @DisplayName("T-04 : un contenu sain est reconnu CLEAN par un clamd réel")
    void aCleanFileIsAccepted() {
        byte[] content = "Justificatif d'absence — contenu sans danger."
                .getBytes(StandardCharsets.UTF_8);

        AttachmentMalwareScanner.Result result = scanner.scan(new ByteArrayInputStream(content));

        assertThat(scanner.isActive()).isTrue();
        assertThat(result.verdict()).isEqualTo(AttachmentMalwareScanner.Verdict.CLEAN);
        assertThat(result.signature()).isNull();
    }

    @Test
    @DisplayName("T-11 : la chaîne EICAR est détectée par un clamd réel et refusée")
    void theEicarTestFileIsDetected() {
        byte[] eicar = EICAR.getBytes(StandardCharsets.US_ASCII);

        AttachmentMalwareScanner.Result result = scanner.scan(new ByteArrayInputStream(eicar));

        assertThat(result.verdict()).isEqualTo(AttachmentMalwareScanner.Verdict.INFECTED);
        // La signature est conservée pour la traçabilité ; jamais le
        // contenu, jamais le chemin.
        assertThat(result.signature()).isNotBlank().containsIgnoringCase("eicar");
    }

    @Test
    @DisplayName("un contenu de plusieurs mégaoctets est analysé sans erreur de cadrage")
    void aMultiMegabyteFileIsScanned() {
        // Taille proche de la limite métier d'une pièce jointe (5 Mo,
        // RG-081) : vérifie que le découpage en blocs INSTREAM tient sur
        // un volume réaliste, et non seulement sur quelques octets.
        byte[] content = new byte[4 * 1024 * 1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }

        assertThat(scanner.scan(new ByteArrayInputStream(content)).verdict())
                .isEqualTo(AttachmentMalwareScanner.Verdict.CLEAN);
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2_000);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
    }
}
