package com.esic.connect.integration.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de l'intégration Microsoft 365 (EF-INT-002, EF-INT-003).
 *
 * <p>Aucune valeur par défaut ne contient de secret : sans
 * {@code tenantId}, {@code clientId} et {@code clientSecret} fournis par
 * l'environnement, l'intégration est <strong>inactive</strong> et le
 * produit le déclare. C'est la même politique que Turnstile (T-08) et la
 * poussée web (T-13) : ne jamais simuler un service absent.
 *
 * @param enabled      activation explicite ; sans elle, rien n'est tenté
 * @param tenantId     identifiant de locataire Entra ID
 * @param clientId     identifiant d'application
 * @param clientSecret secret client — <strong>jamais</strong> dans le dépôt
 * @param baseUrl      racine de l'API Graph (surchargée en test)
 * @param tokenUrl     racine du service de jetons (surchargée en test)
 */
@ConfigurationProperties(prefix = "app.integration.microsoft")
public record MicrosoftGraphProperties(
        boolean enabled,
        String tenantId,
        String clientId,
        String clientSecret,
        String baseUrl,
        String tokenUrl) {

    public MicrosoftGraphProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://graph.microsoft.com/v1.0" : baseUrl;
        tokenUrl = tokenUrl == null || tokenUrl.isBlank()
                ? "https://login.microsoftonline.com" : tokenUrl;
    }

    /**
     * Une configuration incomplète n'est jamais « presque active » : elle
     * est inactive. Démarrer une requête Graph sans secret produirait un
     * échec d'authentification que rien ne distinguerait d'une panne.
     */
    public boolean isConfigured() {
        return enabled
                && notBlank(tenantId) && notBlank(clientId) && notBlank(clientSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
