package com.esic.connect.notification.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Adaptateur HTTP du port {@link WebPushSender} : Web Push (RFC 8030)
 * avec chiffrement RFC 8291 et authentification VAPID RFC 8292.
 *
 * <p>Instancié <strong>uniquement</strong> lorsqu'une paire de clés VAPID
 * non vides est fournie par l'environnement — voir
 * {@link NotificationPushConfig}. Aucune clé ne figure dans le dépôt :
 * sans elles, {@link InactiveWebPushSender} prend le relais et le produit
 * déclare que la poussée est inactive.
 *
 * <p><strong>404 et 410 ne sont pas des échecs.</strong> Le service de
 * poussée les renvoie quand l'abonnement n'existe plus — appareil
 * réinitialisé, navigateur désinstallé, permission retirée. Les retenter
 * ne les fera pas revenir : l'abonnement est révoqué.
 */
class HttpWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(HttpWebPushSender.class);
    /** Durée de vie du message chez le service de poussée, en secondes. */
    private static final String TTL_SECONDS = "86400";

    private final HttpClient httpClient;
    private final WebPushCrypto crypto = new WebPushCrypto();
    private final VapidSigner vapidSigner;
    private final Duration timeout;

    HttpWebPushSender(String publicKey, String privateKey, String subject, Duration timeout,
                      ObjectMapper objectMapper, Clock clock) {
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.vapidSigner = new VapidSigner(
                WebPushCrypto.privateKeyOf(WebPushCrypto.decode(privateKey)),
                publicKey, subject, objectMapper, clock);
        log.info("Notifications poussees actives (VAPID configure).");
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Outcome send(String endpoint, String p256dhKey, String authSecret, String payload) {
        byte[] body;
        try {
            body = crypto.encrypt(p256dhKey, authSecret, payload.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            // Clés d'abonnement inexploitables : réessayer ne les
            // corrigera pas. L'abonnement est traité comme périmé.
            log.warn("Abonnement de poussee inexploitable : {}", invalid.getClass().getSimpleName());
            return Outcome.GONE;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Authorization", vapidSigner.authorizationHeader(endpoint))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", TTL_SECONDS)
                .header("Urgency", "normal")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return Outcome.GONE;
            }
            if (status >= 200 && status < 300) {
                return Outcome.SENT;
            }
            // Ni l'URL ni le corps ne sont journalisés : l'URL est un
            // secret d'appareil, le corps est chiffré pour l'abonné.
            log.warn("Service de poussee en erreur : statut={}", status);
            return Outcome.FAILED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (Exception failure) {
            log.warn("Poussee injoignable : {}", failure.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }
}
