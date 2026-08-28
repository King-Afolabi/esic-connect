package com.esic.connect;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Vérifie la structure modulaire du back-end (absence de cycles, respect des
 * frontières entre modules) — voir docs/03-architecture.md §6.4.
 */
class ModularityTests {

    ApplicationModules modules = ApplicationModules.of(EsicConnectApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }
}
