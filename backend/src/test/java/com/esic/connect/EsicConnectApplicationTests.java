package com.esic.connect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vérifie que le contexte Spring démarre correctement avec la configuration
 * du socle (sécurité, JPA/Flyway, Redis, OpenAPI, Actuator).
 */
@SpringBootTest
@ActiveProfiles("test")
class EsicConnectApplicationTests {

    @Test
    void contextLoads() {
    }
}
