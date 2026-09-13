package com.esic.connect.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le profil {@code demo} est celui réellement actif sur le déploiement
 * public (Pi + tunnel Cloudflare) — il n'existe pas de profil "prod"
 * séparé à ce stade (voir docs/03-architecture.md DEC-D01, application-demo.yml).
 * L'audit de sécurité du 2026-09-14 a constaté que Swagger UI et
 * `/v3/api-docs` y étaient publiquement joignables (springdoc les active
 * par défaut, {@code application.yml} ne les désactive que nulle part) :
 * toute la surface d'API (endpoints, DTOs) était donc exposée sans
 * authentification. Ce test fige la correction apportée à
 * {@code application-demo.yml}.
 *
 * <p>Suit le même contournement que {@link com.esic.connect.identity.internal.
 * DefaultDemoAccountProvisionerTests} pour combiner {@code test} et
 * {@code demo} : {@code application-demo.yml} pointe sur
 * {@code MYSQL_DATABASE} (la base de démonstration) — {@code demo} étant
 * déclaré après {@code test}, sa valeur l'emporterait sans le
 * {@code @TestPropertySource} ci-dessous, qui réimpose la base de test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "test", "demo" })
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/"
        + "${MYSQL_TEST_DATABASE:esic_test}"
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
class SpringDocDemoProfileExposureTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void apiDocsAreNotServedUnderTheDemoProfile() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void swaggerUiIsNotServedUnderTheDemoProfile() {
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
