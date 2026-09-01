package com.esic.connect.attendance.internal;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Vues API d'une pièce jointe de justificatif (bloc G1-E). */
final class JustificationAttachmentResponses {

    private JustificationAttachmentResponses() {
    }

    /**
     * Réponse de téléchargement <strong>sécurisée</strong> : forçage du
     * téléchargement ({@code Content-Disposition: attachment}), pas de
     * reniflage MIME ({@code X-Content-Type-Options: nosniff}), type
     * re-dérivé (jamais celui déclaré au dépôt), longueur contrôlée, pas
     * de cache. Aucune notion de {@code Range} n'est annoncée.
     */
    static ResponseEntity<InputStreamResource> download(Download file) {
        String ascii = asciiFallback(file.fileName());
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded);
        headers.add("X-Content-Type-Options", "nosniff");
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(file.sizeBytes());
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(file.content()));
    }

    private static String asciiFallback(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? c : '_');
        }
        String cleaned = sb.toString().strip();
        return cleaned.isEmpty() ? "piece-jointe" : cleaned;
    }

    /**
     * Métadonnées d'une pièce jointe <strong>{@code STORED}</strong> —
     * jamais de {@code storageKey}, de chemin ni d'identifiant SQL.
     *
     * @param publicId    identifiant public de la pièce
     * @param fileName    nom d'origine assaini (affichage)
     * @param contentType type MIME re-dérivé des magic bytes
     * @param sizeBytes   taille du contenu
     * @param sha256      empreinte hexadécimale du contenu
     * @param uploadedAt  date de dépôt
     */
    record Meta(UUID publicId, String fileName, String contentType, long sizeBytes, String sha256,
                Instant uploadedAt) {
    }

    /**
     * Contenu d'une pièce prêt à streamer vers le client. Le flux est
     * fermé par le contrôleur après écriture de la réponse.
     */
    record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }
}
